# Pedidos360

Sistema de pedidos construido en varias etapas. La **etapa 1** implementa la
arquitectura base de autenticación y autorización con **Azure AD (Entra ID)**:

```
┌────────────┐  (1) login Azure AD   ┌──────────────────┐
│ SPA Angular│ ────────────────────▶ │  Azure AD Tenant │
│ + MSAL     │ ◀──────────────────── │  (JWKS / tokens) │
└─────┬──────┘                      └──────────────────┘
      │ (2) Access token (Bearer, apunta a api://<pedidos-api>/access_as_user)
      ▼
┌──────────────────┐  (3) valida token (firma JWKS, iss, aud, exp/nbf)
│ BFF (Spring Boot)│ ────────────────────────────────────────────────┐
│  :8085           │   igual que el API Manager: token inválido = 401  │
└──────────────────┘                                                   ▼
                                                                 ┌────────────────────────┐
                                                                 │ API Gateway (AWS)      │
      (4) reenvía el MISMO Bearer ────────────────────────────▶  │ JWT authorizer valida  │
                                                                 │ el token también       │
                                                                 └───────────┬────────────┘
                                                                             ▼
                                                                   ┌─────────────────────┐
                                                                   │ Backend Spring Boot  │
                                                                   │ EC2 (ALB + ASG)      │
                                                                   │ :8090 roles PEDIDOS_*│
                                                                   └─────────────────────┘
```

## Módulos

| Ruta | Puerto | Rol |
|---|---|---|
| `pedidos360-front/` | 4200 | SPA Angular 20 + MSAL v5 (Azure AD). Sólo habla con el BFF. |
| `pedidos360-bff/` | 8085 | BFF Spring Boot 3.5. Valida el JWT y reenvía el mismo Bearer al API Manager. |
| `pedidos360-backend/` | 8090 | Resource server con CRUD de pedidos y roles (ADMIN/VENDEDOR/CLIENTE). |
| `infra/` | - | Artefactos AWS: OpenAPI del API Gateway, CloudFormation, user-data, Dockerfile. |

## Roles y endpoints

| Endpoint (vía BFF) | Método | Permiso |
|---|---|---|
| `/bff/pedidos` | GET | cualquier usuario autenticado |
| `/bff/pedidos/{id}` | GET | cualquier usuario autenticado |
| `/bff/pedidos` | POST | `PEDIDOS_ADMIN` o `PEDIDOS_VENDEDOR` |
| `/bff/pedidos/{id}/estado` | PATCH | `PEDIDOS_ADMIN` |
| `/bff/pedidos/{id}` | DELETE | `PEDIDOS_ADMIN` (cancela) |

Los app roles de Azure AD se mapean a `ROLE_*` en el backend.

## Puesta en marcha (local)

### 1. Prerrequisitos
- Node 22.20+ y Java 21+ (local: JDK 22, target Java 21).
- Cuenta del tenant de Azure AD del curso (es un **tenant de empresa**: el
  consentimiento puede requerir visto bueno del admin del tenant).

### 2. Registrar dos aplicaciones en Azure AD (Entra ID)
1. **SPA** (client id = `spaClientId`):
   - Redirect URI: `http://localhost:4200` (token: Authorization code + PKCE).
   - Sin secretos. Toggle "Accounts in this organizational directory only".
2. **pedidos-api** (client id = `apiClientId`):
   - "Expose an API" → creá un scope `access_as_user` → App ID URI por defecto
     `api://<client-id>`.
   - En la app SPI: **API permissions** → add permission → "My APIs" →
     `pedidos-api` → `access_as_user`.
3. (Opcional) App roles en la app SPA: `PEDIDOS_ADMIN`, `PEDIDOS_VENDEDOR`,
   `PEDIDOS_CLIENTE`; asignar a los usuarios en "Enterprise applications".

### 3. Configurar valores
- `pedidos360-front/src/environments/environment.ts` (copiá desde `.example.ts`).
- Variables de entorno para BFF/backend:
  `AZURE_TENANT_ID`, `AZURE_EXPOSED_APP_CLIENT_ID`, `AZURE_EXPOSED_APP_URI`.

### 4. Correr los tres procesos
```bash
# backend
cd pedidos360-backend && mvnw.cmd spring-boot:run     # :8090

# bff
cd pedidos360-bff && mvnw.cmd spring-boot:run         # :8085

# frontend
cd pedidos360-front && npm start                       # :4200
```

Verificación sin credenciales:
- `GET http://localhost:8085/health` → 200
- `GET http://localhost:8085/bff/pedidos` sin token → 401
- `GET http://localhost:8085/bff/pedidos` con token basura → 401
- `GET http://localhost:8090/api/pedidos` sin token → 401

Con token real:
- Loguearse en `http://localhost:4200`, entrar a **Dashboard** (muestra el
  profile y los roles) y **Pedidos** (listado; crear requiere
  ADMIN/VENDEDOR).

## Tests

```bash
cd pedidos360-backend && mvnw.cmd test    # WebMvcTest: 6 tests (401/403/roles/CRUD)
cd pedidos360-bff && mvnw.cmd test        # WebMvcTest: 5 tests (forward + 401s)
```

## Deploy AWS (artefactos, sin deploy automatizado)

1. Build del jar: `cd pedidos360-backend && mvnw.cmd package` → subir a un bucket S3.
2. `infra/cloudformation/alb-asg.yaml` — ALB + ASG + EC2 con el backend (user-data
   instala Corretto 21, baja el jar de S3 y lo corre como servicio systemd).
3. `infra/cloudformation/api-gateway-httpapi.yaml` — HTTP API con JWT authorizer
   (issuer/audience del tenant) que proxya al ALB. El endpoint resultante es el
   `PEDIDOS_API_URL` del BFF.
4. SPA: build (`npm run build`) y sirviendo desde S3/CloudFront (opcional).

`infra/api-gateway/openapi.yaml` documenta el JWT authorizer (idéntico issuer y
audience que el BFF), cumpliendo el requerimiento "el BFF funciona igual que el
API Manager".

## Pendientes con datos reales
- Completar `environment.ts` y env vars con el tenant/client ids del curso.
- En el tenant: consentimiento de `access_as_user`, asignación de app roles.