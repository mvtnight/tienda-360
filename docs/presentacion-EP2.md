# Presentación EP2 — Pedidos360 (Evaluación Parcial N°2)

**Asignatura:** DSY1107 Desarrollo Cloud Native I · **Ponderación:** 24% · **Individual**
**Duración:** 5 a 10 minutos · **Formato:** orden e hilo conductor, lenguaje técnico.

> Documento vivo: los artículos marcados con `[CAPTURA]` se reemplazan por pantallazos/curls reales cuando exista el deploy en AWS con el tenant real.

---

## 1. Estructura general (10 slides ≈ 7:30 minutos)

| # | Slide | Tiempo | Indicador EP2 |
|---|-------|--------|---------------|
| 1 | Arquitectura de la solución | 0:45 | (contexto) |
| 2 | Tenant de Azure AD (IDaaS) | 0:45 | 3 · 10% |
| 3 | Aplicaciones y roles dentro del tenant | 0:50 | 4 · 10% |
| 4 | Flujo de registro y login desde el frontend | 0:50 | 5 · 10% |
| 5 | OAuth 2.0 Authorization Code con PKCE | 1:00 | 6 · 15% |
| 6 | Rutas del API Manager | 0:60 | 1 · 13% |
| 7 | CORS en el API Manager | 0:30 | 2 · 7% |
| 8 | JWT en todas las rutas (200 / 401 / 403) | 1:15 | 7 · 20% |
| 9 | Evidencia de las rutas y JSON esperado | 1:15 | 8 · 15% |
| 10 | Cierre | 0:30 | — |

**Hilo conductor (frase para abrir y cerrar):**
"Pedidos360 es una arquitectura cloud native donde *ningún* componente confía en el otro: el frontend obtiene tokens vía Azure AD y cada capa (API Manager y BFF) valida el mismo JWT antes de dejar pasar una petición."

---

## 2. Slide a slide

### S1 · Arquitectura de la solución (0:45)
Diagrama: `Angular (MSAL) → (JWT) → API Manager (AWS API Gateway) → ALB → Microservicio backend (EC2) `, y a la derecha el BFF que replica la validación del API Manager.

- Frontend: SPA Angular con **MSAL v5** (misma librería de Microsoft para autenticación).
- API Manager: **AWS API Gateway HTTP API**, valida el JWT y reenvía.
- Backend: microservicio Spring Boot en **EC2** (ALB + Auto Scaling), resource server.
- BFF: capa intermedia que valida el token **igual que el API Manager** antes de reenviar (Evaluación Parcial N°1).
- **Justificación de microservicios:** BFF y backend son componentes desplegables de forma independiente; cada uno es un microservicio con su propio ciclo de vida.

### S2 · Tenant de Azure AD (0:45) — Indicador 3 (10%)
- Tenant creado `[CAPTURA: página Tenant overview]`; dominio `pedidos360.onmicrosoft.com`.
- **Usuarios de prueba** creados y con roles: `[CAPTURA: lista Users]`.
  - `maria@...` → rol **pedidos_admin**
  - `juan@...` → rol **pedidos_vendedor**
- Roles definidos: `pedidos_admin`, `pedidos_vendedor`, `pedidos_cliente`.
- Parámetros requeridos: `[CAPTURA: la app con su clientId]`.

### S3 · Aplicaciones y roles dentro del tenant (0:50) — Indicador 4 (10%)
- **pedidos360-api** (resource): `[CAPTURA: App registrations]`
  - clientId correcto, redirect URIs bien definidas.
  - **Expose an API** → scope `access_as_user` (`[CAPTURA: scopes]`).
  - **App roles** → 3 roles (`[CAPTURA: roles]`).
- **pedidos360-spa** (frontend): redirect URI `http://localhost:4200`, permisos a `pedidos360-api`.
- IDaaS expone la API del proyecto de forma adecuada.

### S4 · Flujo de registro y login (0:50) — Indicador 5 (10%)
- Usuarios se crean/administran en el tenant (usuarios de prueba con roles) y desde el frontend inician sesión.
- Demo en vivo: entrar a `http://localhost:4200` → **Iniciar sesión** → login.microsoftonline.com → volver autenticado con roles en la barra de la app. `[CAPTURA: Dashboard con usuario y roles]`
- Logout y protección de rutas con `MsalGuard`.

### S5 · OAuth 2.0 Authorization Code con PKCE (1:00) — Indicador 6 (15%)
- El SPA usa el flujo **Authorization Code + PKCE** (MSAL `redirect`, `responseType=code`).
- En la pestaña **Red** del navegador: `[CAPTURA: /oauth2/v2.0/authorize?code_challenge=...&code_challenge_method=S256&state=...&nonce=...]`
- Code verifier/challenge generados en el cliente (S256), `state` y `nonce` validados contra CSRF/replay.
- Nunca se usa el flujo implícito: el token se obtiene canjeando el code por el token en el backend de Azure (mensaje desde dev tools).

### S6 · Rutas del API Manager (1:00) — Indicador 1 (13%)
- El API Gateway es el **intermediario** entre frontend y backend.
- Rutas declaradas (paths y métodos coherentes) `[CAPTURA: consola API Gateway → Routes]`:

| Método | Ruta | Microservicio destino |
|--------|------|-----------------------|
| GET | /health | backend (salud) |
| GET / POST | /api/pedidos | backend |
| GET / DELETE | /api/pedidos/{id} | backend |
| PATCH | /api/pedidos/{id}/estado | backend |
| ANY | /{proxy+} | catch-all protegido |

- Todas dirigidas al ALB → microservicio; catch-all garantiza que **nada** queda sin intermediario.

### S7 · CORS en el API Manager (0:30) — Indicador 2 (7%)
- `CorsConfiguration` en el API Gateway HTTP: `[CAPTURA: CORS config]`
  - `AllowOrigins: http://localhost:4200`
  - `AllowMethods: GET, POST, PATCH, DELETE, OPTIONS`
  - `AllowHeaders: *`, `ExposeHeaders: Authorization`
- Razón: la SPA corre en distinto origen que el API Manager; sin CORS el navegador bloquea las llamadas.
- Demo: OPTIONS preflight desde DevTools `[CAPTURA: Preflight 200 + Access-Control-Allow-Origin]`.

### S8 · JWT en todas las rutas (1:15) — Indicador 7 (20%)
- **Todas** las rutas tienen `AuthorizationType: JWT` + scope `access_as_user`.
- El API Manager verifica **issuer y audience**:
  - issuer → `https://login.microsoftonline.com/<TENANT_ID>/v2.0`
  - audience → `api://<CLIENT_ID>`
- Serie de pruebas (las mismas que se muestran en S9) `[CAPTURA: resultados]`:

| Caso | Resultado esperado |
|------|--------------------|
| Sin token | 401 |
| Token inválido | 401 |
| Token válido sin scope | 403 |
| Token válido con scope y rol | 200 |

### S9 · Evidencia de cada ruta (1:15) — Indicador 8 (15%)

Suite de comandos (ejecutar contra el endpoint del API Manager, `$API=https://...execute-api.us-east-1.amazonaws.com/prod` y `$TOKEN` = token real del SPA):

```bash
# 401 - sin token
curl -i $API/api/pedidos
# 401 - token inválido (firma no verificada)
curl -i -H "Authorization: Bearer token.basura.xxx" $API/api/pedidos
# 403 - token válido pero sin scope access_as_user (obtenido contra otro resource)
curl -i -H "Authorization: Bearer $TOKEN_SIN_SCOPE" $API/api/pedidos
# 200 - token del SPA (scope y rol correctos)
curl -i -H "Authorization: Bearer $TOKEN" $API/api/pedidos
curl -i -H "Authorization: Bearer $TOKEN" $API/api/pedidos/1
curl -i -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"cliente":"Maria","monto":12500,"estado":"PENDIENTE"}' $API/api/pedidos
curl -i -X PATCH -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"estado":"EN_PREPARACION"}' $API/api/pedidos/1/estado
# 200 con rol admin (DELETE) y JSON esperado de vuelta
curl -i -X DELETE -H "Authorization: Bearer $TOKEN_ADMIN" $API/api/pedidos/1
```

`[CAPTURA: cada comando con su respuesta JSON]` — las respuestas muestran que el backend responde el **JSON esperado** (PedidoResponse: id, cliente, monto, estado, creadoPor, fecha).

### S10 · Cierre (0:30)
- Resumen: mismo JWT validado en 3 puntos (API Manager, BFF y microservicio) = defense in depth.
- Cumplimiento de los indicadores (tabla con %).
- Q&A.

---

## 3. Checklist previo a la defensa (se completa cuando tengas datos)

- [ ] `environment.ts` con `tenantId`, `spaClientId`, `apiClientId`, scope real.
- [ ] Usuarios de prueba con roles asignados (verificar en DASHBOARD del front).
- [ ] Backend/BFF levantados con envs reales (no placeholders `00000000-...`).
- [ ] Deploy CFN: `alb-asg.yaml` → output `ALBDnsName`; luego `api-gateway-httpapi.yaml` → output `ApiEndpoint`.
- [ ] `PEDIDOS_API_URL` (BFF) = ApiEndpoint; probar E2E Angular → BFF → API GW → EC2.
- [ ] Capturas: S2, S3, S4, S5, S6, S7, S8, S9.
- [ ] Ensayar cronometrado (objetivo ≤ 8 min).