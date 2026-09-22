# GUÍA DE CAPTURAS — Pedidos360 EP2

Guarda cada captura en `docs/evidencias/img/` con el **nombre exacto** indicado
(el generador de slides las ubica por palabra clave en el nombre).

> Deploy de las capturas en el deck:
> `python infra/scripts/evidencias-pptx.py --json docs/evidencias/resumen.json --img docs/evidencias/img --out docs/evidencias/Pedidos360-Evidencias.pptx`
> Re-ejecuta el PPTX después de guardar cada captura para actualizar el deck.

---

## A. Azure Portal (Entra ID / IDaaS)

| # | Nombre de archivo | Qué capturar | Slide |
|---|-------------------|--------------|-------|
| 1 | `tenant-overview.png` | Inicio / **Microsoft Entra ID → Overview** (fin de pantalla con **Tenant ID / Directory ID** `0b4bca41-...` visible) | Tenant |
| 2 | `tenant-users.png` | **Users → All users**: maria y jose (con UPN `@matiaspulgar.onmicrosoft.com`) | Tenant |
| 3 | `app-spa.png` | **App registrations → SPA**: clientId `54b906c8-...`, **Redirect URIs** (localhost:4200 + CloudFront), tipo SPA | Apps |
| 4 | `app-api.png` | **App registrations → pedidos-api**: clientId `6da3f8f4-...`, **Application ID URI** `api://6da3f8f4-...` | Apps |
| 5 | `app-scopes.png` | **pedidos-api → Expose an API**: scope `access_as_user` (rango completo) | Apps |
| 6 | `app-roles.png` | **pedidos-api → App roles**: `PEDIDOS_ADMIN` y `PEDIDOS_VENDEDOR` | Apps |
| 7 | `app-asignacion.png` | **App roles** asignados: jose=PEDIDOS_ADMIN, maria=PEDIDOS_VENDEDOR (Enterprise applications → pedidos-api → Users, o cada usuario → Assigned roles) | Apps |

Cómo llegar: `portal.azure.com` → **Microsoft Entra ID** (o **App registrations**).

---

## B. AWS Console (API Manager)

| # | Nombre de archivo | Qué capturar | Slide |
|---|-------------------|--------------|-------|
| 8 | `routes.png` | **API Gateway → API (pedidos360-httpapi) → Routes**: lista con GET/POST/PATCH/DELETE `/api/pedidos...`, `/health`, `/{proxy+}` y el authorizer JWT en cada una | Rutas+CORS |
| 9 | `authorizer.png` | **Authorization** o en una ruta, el **JWT authorizer**: issuer `https://login.microsoftonline.com/0b4bca41-.../v2.0`, audience `api://6da3f8f4-...`, IdentitySource `$request.header.Authorization`, scope `access_as_user` | Rutas+CORS |
| 10 | `cors.png` | **CORS**: AllowOrigins `http://localhost:4200` y `https://d2u1dalj9nm2b1.cloudfront.net`; AllowHeaders `Authorization, Content-Type`; Methods GET/POST/PATCH/DELETE/OPTIONS | Rutas+CORS |
| 11 | `stage.png` | **Stages → prod**: Invoke URL `https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com/prod` (y auto-deploy ON) | Rutas+CORS |

Cómo llegar: `console.aws.amazon.com/apigateway` (región **us-east-2**) → API Gateway → la API `pedidos360-httpapi`.

---

## C. Navegador (flujo real PKCE)

| # | Nombre de archivo | Qué capturar | Slide |
|---|-------------------|--------------|-------|
| 12 | `login.png` | `https://d2u1dalj9nm2b1.cloudfront.net` → botón **Ingresar** → página de login de Microsoft (jose o maria) | Login+PKCE |
| 13 | `claims.png` | **Perfil/claims** del Dashboard: nombre, email, oid y **roles PEDIDOS_ADMIN / PEDIDOS_VENDEDOR** | Login+PKCE |
| 14 | `authorize.png` | DevTools → **Red** → marca `/authorize` → URL con `response_type=code&code_challenge=...&code_challenge_method=S256&state=...&nonce=...` (es el Authorization Code + PKCE real) | Login+PKCE |
| 15 | `headers.png` | DevTools → **Red** → `GET /prod/api/pedidos` → **Encabezados** → `Authorization: Bearer eyJ...` (¡interceptor agregando el token!) | Matriz |
| 16 | `cors-preflight.png` | (opcional) DevTools → petición **OPTIONS** con status **204** y `access-control-allow-origin` | Rutas+CORS |

---

## Comandos de regeneración

```powershell
# 1) Matriz real (pega el token copiado del Dashboard de jose y maria):
powershell -ExecutionPolicy Bypass -File infra\scripts\evidencias-api-manager.ps1 -Token $TOKEN_MARIA -Token2 $TOKEN_JOSE

# 2) Generar el deck con las capturas ya guardadas:
python infra\scripts\evidencias-pptx.py --json docs/evidencias/resumen.json --img docs/evidencias/img --out docs/evidencias/Pedidos360-Evidencias.pptx
```