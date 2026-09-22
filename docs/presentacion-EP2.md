# Presentación EP2 — Pedidos360 (Evaluación Parcial N°2)

**Asignatura:** DSY1107 Desarrollo Cloud Native I · **Ponderación:** 24% · **Individual**
**Duración:** 5 a 10 minutos · **Formato:** orden e hilo conductor, lenguaje técnico.

> Evidencias visuales: deck **`docs/evidencias/Pedidos360-Evidencias.pptx`** (generado con
> `python infra/scripts/evidencias-pptx.py` + `resumen.json` + capturas en `docs/evidencias/img/`).
> Los `[CAPTURA: <file>]` apuntan al nombre de archivo en `docs/evidencias/img/` (guía: `docs/evidencias/GUIA-CAPTURAS.md`).

---

## 0. Cumplimiento de la rúbrica (8/8 = 100%)

| # | Indicador | % | Estado | Dónde se demuestra |
|---|-----------|---|--------|--------------------|
| 1 | Rutas del API Manager | 13% | ✅ | Slide Rutas + `routes.png` |
| 2 | CORS en el API Manager | 7% | ✅ | Slide Rutas + `cors.png` + caso preflight |
| 3 | Crear tenant | 10% | ✅ | Slide Tenant + `tenant-overview.png` |
| 4 | Configurar app en tenant | 10% | ✅ | Slide Apps + `app-*.png` |
| 5 | Flujo registro + login | 10% | ✅ | Slide Login + `login.png` |
| 6 | OIDC Authorization Code + PKCE | 15% | ✅ | Slide Login + `authorize.png` (code_challenge S256) |
| 7 | JWT en todas las rutas (401/403/200) | 20% | ✅ | Slide Matriz coloreada (resumen.json) |
| 8 | Evidencia de rutas + JSON | 15% | ✅ | Slide Matriz + JSON de cada caso + `/health` |
| | **TOTAL** | **100%** | 8/8 | Deck `Pedidos360-Evidencias.pptx` |

**Hilo conductor (abrir y cerrar):**
"Pedidos360 es una arquitectura cloud native donde *ningún* componente confía en el otro:
la SPA obtiene tokens vía Azure AD y cada capa (API Manager y backend) valida el mismo JWT
antes de dejar pasar una petición."

---

## 1. Datos reales del entorno (memoriza)

| Dato | Valor |
|---|---|
| Tenant (Directory) ID | `0b4bca41-b3f5-427c-aeac-2dbcd055f91d` |
| Dominio | `matiaspulgar.onmicrosoft.com` |
| SPA clientId | `54b906c8-47fb-4066-a49a-b3aa7f05427e` (public client, PKCE) |
| API clientId | `6da3f8f4-905c-4c76-abf0-c711d0dd3926` |
| App ID URI | `api://6da3f8f4-905c-4c76-abf0-c711d0dd3926` |
| Scope | `api://6da3f8f4-905c-4c76-abf0-c711d0dd3926/access_as_user` |
| Issuer validado | `https://sts.windows.net/0b4bca41-.../` (tokens v1.0 que emite la app API para TODOS los clientes; el authorizer exige el issuer exacto del tenant → issuer de otro tenant → 401) |
| API Manager | `https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com` (stage `$default`, sin prefijo) |
| Web pública | `https://d2u1dalj9nm2b1.cloudfront.net` |
| ALB / EC2 | `http://pedidos360-alb-dev-2080390497.us-east-2.elb.amazonaws.com` |
| Usuarios | `jose@...` → **PEDIDOS_ADMIN** · `maria@...` → **PEDIDOS_VENDEDOR** |

Roles: **2** (`PEDIDOS_ADMIN`, `PEDIDOS_VENDEDOR`). El backend normaliza el claim `roles`
(minúsculas de Azure) a `ROLE_PEDIDOS_*` para `@PreAuthorize`.

---

## 2. Slide a slide

### S1 · Arquitectura de la solución (0:45)
`Angular + MSAL` → **(JWT)** → `API Gateway (API Manager)` → `ALB` → `EC2 Spring Boot :8090`.
- Frontend: SPA Angular con **MSAL v5** (mismo public client que Microsoft).
- API Manager: **AWS API Gateway HTTP API**, valida el JWT y proxya.
- Backend: microservicio **Spring Boot** en EC2 (ALB + Auto Scaling), resource server + RBAC.
- **BFF** (EP1): capa que valida el token **igual que el API Manager** (JWKS, iss, aud, exp).
- JSM: cada componente desplegable de forma independiente = microservicios.

### S2 · Tenant de Azure AD (0:45) — Indicador 3 (10%)
- Tenant real: `matiaspulgar.onmicrosoft.com` / `0b4bca41-...`. `[CAPTURA: tenant-overview.png]`
- Usuarios de prueba con roles. `[CAPTURA: tenant-users.png]` — jose (ADMIN), maria (VENDEDOR).

### S3 · Aplicaciones y roles en el tenant (0:50) — Indicador 4 (10%)
- **SPA** `54b906c8-...`: tipo **SPA**/*confidential=no*, redirect `http://localhost:4200` y `https://d2u1dalj9nm2b1.cloudfront.net`. `[CAPTURA: app-spa.png]`
- **pedidos-api** `6da3f8f4-...`: **Expose an API** → `access_as_user`; **App roles** ADMIN/VENDEDOR. `[CAPTURA: app-api.png]` `[CAPTURA: app-scopes.png]`
- Roles asignados: jose=ADMIN, maria=VENDEDOR. `[CAPTURA: app-asignacion.png]`
- IDaaS (**Entra ID**) expone la API; la SPA pide permiso a ese scope.

### S4 · Flujo de registro y login (0:50) — Indicador 5 (10%)
- Los usuarios se crean/administran en el tenant; desde la web pública inician sesión.
- Web pública → **Ingresar** → login Microsoft → volver autenticado con roles. `[CAPTURA: login.png]`
- **Dashboard** muestra claims (nombre, email, oid) y **roles** del access_token. `[CAPTURA: claims.png]`
- `MsalGuard` protege las rutas; logout del lado MSAL.

### S5 · OAuth 2.0 Authorization Code con PKCE (1:00) — Indicador 6 (15%)
- MSAL `redirect`, `responseType=code`: flujo **Authorization Code + PKCE**.
- DevTools: `/authorize?...code_challenge=...&code_challenge_method=S256&state=...&nonce=...` `[CAPTURA: authorize.png]`
- Verifier+challenge S256 generados en el cliente; `state` (anti-CSRF) y `nonce` (anti-replay).
- Nunca **implicit**: el token jamás va en la URL. Public client sin secretos.

### S6 · Rutas del API Manager (1:00) — Indicador 1 (13%)
`[CAPTURA: routes.png]` — todas con **JWT authorizer + scope access_as_user**:

| Método | Ruta | Destino backend |
|--------|------|-----------------|
| GET | /health | salud |
| GET | /api/pedidos | listar |
| GET | /api/pedidos/{id} | detalle |
| POST | /api/pedidos | crear |
| PATCH | /api/pedidos/{id}/estado | estado |
| DELETE | /api/pedidos/{id} | eliminar |

Authorizer: issuer `https://sts.windows.net/<tenant>/` + audience `api://6da3f8f4-...`. `[CAPTURA: authorizer.png]`

### S7 · CORS en el API Manager (0:30) — Indicador 2 (7%)
`[CAPTURA: cors.png]`
- AllowOrigins: `http://localhost:4200` **y** `https://d2u1dalj9nm2b1.cloudfront.net`
- AllowMethods: `GET, POST, PATCH, DELETE, OPTIONS`
- AllowHeaders: `Authorization, Content-Type` · ExposeHeaders: `Authorization`
- Razón: la SPA y el API corren en orígenes distintos; sin CORS el navegador bloquea.
- Preflight: **OPTIONS → 204** con `access-control-allow-origin`. `[CAPTURA: cors-preflight.png]` (o caso 10 del deck).

### S8 · JWT en todas las rutas (1:15) — Indicador 7 (20%)
- **Todas** las rutas con `AuthorizationType: JWT`; verifica **issuer y audience** exactos.
- **Sin token → 401** · **token inválido → 401** · **token sin scope → 403** · **valido → 200/201**.
- RBAC en backend: maria PATCH/DELETE → **403**; jose → **200/204**.

### S9 · Evidencia de las rutas y JSON (1:15) — Indicador 8 (15%)
El **deck** (`Pedidos360-Evidencias.pptx`) muestra la **matriz coloreada** con cada caso:
- Tarjetas: **401 roja**, **403 ámbar**, **200/201 verdes** con método, ruta, esperado-obtenido,
  duración y el **JSON real** devuelto por el backend.
- Incluye preflight CORS (204) y health directo al ALB (`{"status":"OK"}`).
- Fuente: `resumen.json` generado por `infra/scripts/evidencias-api-manager.ps1` con los tokens
  del navegador (botón **Copiar access_token** del Dashboard) o del ROPC de `renovar-tokens.ps1`
  (ambos salen v1.0 con issuer `sts.windows.net`, que es el que valida el authorizer).
- Interceptor con el header: `[CAPTURA: headers.png]` (Authorization: Bearer eyJ...).

### S10 · Cierre (0:30)
- Mismo JWT validado en 3 puntos (SPA/interceptor, API Manager y backend) = **defense in depth**.
- Tabla 8/8 (100%) y Q&A.

---

## 3. Checklist antes de la defensa

- [ ] Regenerar matriz real: `evidencias-api-manager.ps1 -Token $maria -Token2 $jose`
- [ ] Tomar las ~14 capturas (`docs/evidencias/GUIA-CAPTURAS.md`).
- [ ] Regenerar deck: `python infra/scripts/evidencias-pptx.py --img docs/evidencias/img ...`
- [ ] Verificar web pública (CloudFront) con Pedidos cargando (200).
- [ ] Ensayar cronometrado (objetivo ≤ 8 min).
- [ ] `git push` y entregar link del repo en AVA.