# API Manager en AWS — Guía (Pedidos360)

Basado en los tutoriales del curso: **1.1.2 (Crear API Manager)**, **1.1.4 (CORS)**,
**1.3.6 (API Gateway → microservicio)** y **1.3.7 (IdaaS + API Manager)**.
Equivalente a lo que automatiza `infra/cloudformation/api-gateway-httpapi.yaml`
(vía `infra/scripts/deploy.ps1`). Úsala para crear el API Manager a mano en la
consola AWS o para sustentar la evidencia de la presentación.

## Datos del entorno (Pedidos360)

| Dato | Valor |
|---|---|
| Región | `us-east-2` |
| Tenant Azure AD (issuer) | `https://sts.windows.net/0b4bca41-b3f5-427c-aeac-2dbcd055f91d/` |
| Audience (app API) | `api://6da3f8f4-905c-4c76-abf0-c711d0dd3926` |
| Scope requerido | `access_as_user` |
| Origen del SPA (CORS) | `http://localhost:4200` |
| Backend | ALB → EC2 (puerto 8090, path `/api/pedidos*`) |
| Usuarios demo | `maria@...` (vendedora), `jose@...` (admin) |

---

## Paso 1 — Crear el API (tutorial 1.1.2)

1. Consola AWS (región `us-east-2`) → **API Gateway** → **Create API**.
2. Elige **HTTP API** (soporta authorizer JWT nativo) → **Build**.
3. Nombre: `pedidos360-httpapi` → en "Configure routes" deja vacío y avanza
   (las rutas se agregan después) → Crear.

> Si el curso te pide REST API, aplica lo mismo: recursos `/api/pedidos`, método por
> verbo, y authorizer JWT se adjunta por método.

## Paso 2 — Rutas (indicador EP2 #1)

Cada ruta se integra al backend como **HTTP proxy** apuntando al ALB:

| Método | Ruta | Objetivo |
|---|---|---|
| `GET` | `/health` | health check |
| `GET` | `/api/pedidos` | listar pedidos |
| `GET` | `/api/pedidos/{id}` | detalle |
| `POST` | `/api/pedidos` | crear pedido |
| `PATCH` | `/api/pedidos/{id}/estado` | cambiar estado |
| `DELETE` | `/api/pedidos/{id}` | eliminar pedido |

Integración: `Integration type = HTTP proxy`, `Method = ANY / <verbo>`,
`Endpoint URL = http://<ALB-DNS>` (puerto 80, el ALB reenvía a 8090).

## Paso 3 — CORS del API Manager (tutorial 1.1.4, indicador EP2 #2, 7%)

En configuración **CORS** del API:
- `Access-Control-Allow-Origin`: `http://localhost:4200`
- Methods: `GET, POST, PATCH, DELETE, OPTIONS`
- Allow headers: `Authorization, Content-Type`
- Expose headers: `Authorization` (el SPA necesita leerlo si se obtiene token del BFF)
- `Age`/max-age: `3600`

## Paso 4 — Integrar el IdaaS (Azure AD) como authorizer JWT (tutorial 1.3.7)

1. **Authorizers → Create and attach**:
   - Name: `pedidos360-jwt-authorizer`, type `JWT`
   - Identity source: `$request.header.Authorization`
   - **Issuer**: `https://sts.windows.net/0b4bca41-b3f5-427c-aeac-2dbcd055f91d/`
     (los tokens del tenant son v1.0, emiten este issuer para PKCE y ROPC)
   - **Audience**: `api://6da3f8f4-905c-4c76-abf0-c711d0dd3926`
2. **Adjúntalo a TODAS las rutas** (incluida `/health`) y en cada una marca el scope
   **`access_as_user`**.
3. Resultado de seguridad (requisito JWT 200/401/403):
   - Sin token o token inválido → **401**
   - Token válido sin scope `access_as_user` → **403**
   - Token válido con scope → **200** (integra al backend, que aplica roles)

## Paso 5 — Deploy

1. **Stage** → `$default`, **Auto-deploy** activado (o Deploy cada vez que cambies).
2. Endpoint base (para el BFF y las evidencias):
   `https://<api-id>.execute-api.us-east-2.amazonaws.com` (stage `$default`, sin prefijo)

> Este endpoint va como `PEDIDOS_API_URL` en el BFF (`application.properties` →
> `bff.api-url=${PEDIDOS_API_URL:http://localhost:8085}`).

---

## Paso 6 — Evidencias para la presentación (indicador 7 y 8)

Con `$TOKEN` = access_token de `maria` (scope `access_as_user`):

```bash
# Ruta 1 (sin token → 401)
curl -i https://<api-id>.execute-api.us-east-2.amazonaws.com/api/pedidos
#   -> HTTP/1.1 401 Unauthorized

# Ruta 2 (con token → 200 + JSON lista de pedidos)
curl -i -H "Authorization: Bearer $TOKEN" https://<api-id>.execute-api.us-east-2.amazonaws.com/api/pedidos

# Ruta 3 (detalle → 200 + JSON del pedido)
curl -i -H "Authorization: Bearer $TOKEN" https://<api-id>.execute-api.us-east-2.amazonaws.com/api/pedidos/1

# Ruta 4 (crear → 201)
curl -i -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"cliente":"ACME","monto":10000}' \
  https://<api-id>.execute-api.us-east-2.amazonaws.com/api/pedidos

# 403: token sin scope → se demuestra con un token de otra app (o scope distinto)
curl -i -H "Authorization: Bearer $TOKEN_SIN_SCOPE" https://<api-id>.execute-api.us-east-2.amazonaws.com/api/pedidos
#   -> HTTP/1.1 403 Forbidden
```

---

## Verificación cruzada con CloudFormation

El template `infra/cloudformation/api-gateway-httpapi.yaml` crea **exactamente** esto
(rutas por recurso, CORS `SpaOrigin`, authorizer JWT issuer/audience, scope
`access_as_user` en todas las rutas, stage `$default`). Desplegar con:

```powershell
.\infra\scripts\deploy.ps1
```

(o los comandos `aws cloudformation deploy` de los dos stacks, primero `alb-asg`,
luego `api-gateway-httpapi` con el `ALBDnsName`).