# Pedidos360 — Etapa 1: IDaaS con Azure AD + API Manager en AWS

Sistema de pedidos cuyo objetivo de la **etapa 1** es demostrar autenticación y
autorización de punta a punta con **Azure AD (Entra ID)** y un **API Manager en AWS**
que valida el mismo token con los mismos criterios que el BFF.

```
┌────────────┐  (1) login (Authorization Code + PKCE)  ┌────────────────────┐
│ SPA Angular│ ───────────────────────────────────────▶│   Azure AD Tenant  │
│ + MSAL v5  │ ◀───────────────────────────────────────│ (JWKS / tokens)    │
└─────┬──────┘                                         └────────────────────┘
      │ (2) access_token (Bearer) → api://6da3f8f4-…/access_as_user
      ▼
┌──────────────────┐  (3) valida token: firma JWKS, iss, aud, exp/nbf
│ BFF (Spring Boot)│ ── reenvía el MISMO Bearer ────────────────┐
│  :8085           │    token inválido = 401                    ▼
└──────────────────┘                                ┌──────────────────────────┐
                                                   │ API Manager (API Gateway)│
                                                   │ JWT authorizer: mismas   │
      (4) via PEDIDOS_API_URL ────────────────▶    │ validaciones (iss/aud/exp)│
                                                   └────────────┬─────────────┘
                                                                ▼
                                                   ┌──────────────────────────┐
                                                   │ Backend Spring Boot :8090│
         ALB + EC2 (Auto Scaling)                  │ roles PEDIDOS_* (RBAC     │
                                                   │ 200 / 401 / 403)          │
                                                   └──────────────────────────┘
```

## Módulos

| Ruta | Puerto | Rol |
|---|---|---|
| `pedidos360-front/` | 4200 | SPA Angular + MSAL v5. Login/logout, guards, interceptor (Bearer), claims/roles en Dashboard, botón "Copiar access_token". |
| `pedidos360-bff/` | 8085 | BFF. Valida el JWT (JWKS + iss + aud + exp/nbf) y reenvía el mismo Bearer al API Manager. |
| `pedidos360-backend/` | 8090 | Resource server. CRUD de pedidos con RBAC (PEDIDOS_ADMIN / PEDIDOS_VENDEDOR). `/health` público. |
| `infra/` | - | CloudFormation (ALB+ASG+EC2 y API Gateway HTTP API), `deploy.ps1`, user-data. |
| `docs/` | - | Evidencias y guías: `presentacion-EP2.md`, `api-manager-aws-consola.md`, `GUIA-DEFENSA.txt`. |

## Despliegue en AWS (REAL y en producción)

- **Api Manager (endpoint)**: `https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com/prod`
- **Stack ALB + EC2**: `pedidos360-alb` (ALB `pedidos360-alb-dev-2080390497.us-east-2.elb.amazonaws.com`, EC2 `i-068c9d685225f19f3`)
- **Stack API Manager**: `pedidos360-httpapi`
- Región `us-east-2`, cuenta `637423629193`; bucket `pedidos360-matiaspulgar` (jar: `pedidos360-backend.jar`).
- El API Manager valida JWT en **todas** las rutas (GET/POST/PATCH/DELETE `/api/pedidos…` y `/health`), con CORS para `http://localhost:4200`.

## Web pública (CloudFront + S3)

La SPA también está publicada en internet (build `ng build` con `environment.prod.ts`):

- **URL:** https://d2u1dalj9nm2b1.cloudfront.net (CloudFront `E2G724OS67RE8F`)
- Origen: `s3://pedidos360-matiaspulgar/web/` (vía OAI `E2Z0KEJW8C69D6`, bucket privado)
- El build público llama **directo al API Manager** (`/api/pedidos`) con el token PKCE;
  el interceptor de MSAL adjunta el Bearer al origen `…execute-api…`. Deep links
  (`/dashboard`) sirven `index.html` (error 403/404 → 200).
- **Login:** la app registration SPA en Azure debe tener la Redirect URI
  `https://d2u1dalj9nm2b1.cloudfront.net` (Authentication → Single-page application).
- CORS: API Gateway y backend EC2 aceptan `http://localhost:4200` **y** la URL pública.
- Para re-publicar: `cd pedidos360-front && npm.cmd run build`, luego
  `aws s3 sync dist\pedidos360-front\browser s3://pedidos360-matiaspulgar/web/ --delete` y
  `aws cloudfront create-invalidation --distribution-id E2G724OS67RE8F --paths "/*"`.

Volver a desplegar desde cero (S3 → ALB/EC2 → API Gateway):

```powershell
powershell -ExecutionPolicy Bypass -File infra\scripts\deploy.ps1
```

## Datos de configuración

| Dato | Valor |
|---|---|
| Tenant (Directory) ID | `0b4bca41-b3f5-427c-aeac-2dbcd055f91d` |
| Dominio | `matiaspulgar.onmicrosoft.com` |
| Client ID App SPA | `54b906c8-47fb-4066-a49a-b3aa7f05427e` |
| Client ID App API (pedidos-api) | `6da3f8f4-905c-4c76-abf0-c711d0dd3926` |
| Scope | `api://6da3f8f4-905c-4c76-abf0-c711d0dd3926/access_as_user` |
| Issuer (browser/PKCE) | `https://login.microsoftonline.com/<tenant>/v2.0` |
| Issuer (ROPC) | `https://sts.windows.net/<tenant>/` |
| Usuarios de prueba | `maria@…` (PEDIDOS_VENDEDOR) y `jose@…` (PEDIDOS_ADMIN) |

> Los roles vienen en el claim `roles` en minúsculas; las aplicaciones los
> normalizan a `PEDIDOS_ADMIN`/`PEDIDOS_VENDEDOR` para el RBAC.

## Puesta en marcha (local)

```bash
# 1) backend  → :8090
cd pedidos360-backend && mvnw.cmd package && java -jar target\pedidos360-backend.jar
# 2) bff      → :8085  (opcional: apuntar al API Manager de AWS)
cd pedidos360-bff && mvnw.cmd package && java -jar target\pedidos360-bff-0.0.1-SNAPSHOT.jar
#    con el API Manager de AWS:
set PEDIDOS_API_URL=https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com/prod
# 3) frontend → :4200
cd pedidos360-front && npm start
```

Abrir **http://localhost:4200**, login con `maria@` o `jose@` (clave entregada),
ver Dashboard (claims/roles + botón copiar token) y Pedidos.

## Endpoints por rol (RBAC)

| Endpoint | Método | Permiso |
|---|---|---|
| `/api/pedidos` | GET | Autenticado |
| `/api/pedidos/{id}` | GET | Autenticado |
| `/api/pedidos` | POST | ADMIN o VENDEDOR |
| `/api/pedidos/{id}/estado` | PATCH | ADMIN |
| `/api/pedidos/{id}` | DELETE | ADMIN |
| `/health` | GET | Público |

Matriz verificada con token real: sin token → **401**; maria en PATCH/DELETE → **403**;
jose en PATCH → **200** y DELETE → **204**; GET/POST → **200/201** con JSON.

## Evidencias y defensa

Ver `docs/GUIA-DEFENSA.txt` (todo el proyecto para defender, incluye cada indicador
de la rúbrica EP1 y EP2 y los comandos de demo). Capturas pendientes marcadas como
`[CAPTURA]` en `docs/presentacion-EP2.md`.

## Tests

```bash
cd pedidos360-backend && mvnw.cmd test   # 6 tests (401/403/roles/CRUD)
cd pedidos360-bff && mvnw.cmd test       # 5 tests (forward + 401)
```