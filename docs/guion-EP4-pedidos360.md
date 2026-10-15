# Guion de presentación — EP4 Pedidos360 (DSY1107)

> Duración objetivo: **8 minutos** (rango permitido 5–10 min). Presentación **grupal**
> con fundamentación individual. En cada bloque: qué mostrar, cómo, y el indicador de
> rúbrica que cubre. Sustituir `[CAPTURA]` por la imagen/evidencia correspondiente.

## 0. Apertura (30 s)

> "Pedidos360 es un sistema cloud native compuesto por una SPA Angular, un BFF y
> microservicios Spring Boot desplegados en AWS. En la etapa 3 incorporamos **RabbitMQ**
> para mensajería asíncrona desacoplada y un **microservicio administrador** de la
> mensajería, sin afectar la lógica existente."

Slides sugeridas: portada, integrantes, stack (Angular, Spring Boot, Azure AD, AWS, RabbitMQ).

## 1. Arquitectura general (1 min)

- Mostrar el diagrama del `README.md` (flujo login → BFF → API Manager → backend).
- Mencionar los módulos: `pedidos360-front` (4200), `pedidos360-bff` (8085),
  `pedidos360-backend` (8090), `pedidos360-admin` (8091).
- Código: `pedidos360-backend/.../config/RabbitConfig.java` (topología centralizada).

## 2. IDaaS — Tenant y usuarios (1 min)

- Abrir Azure Portal → **Microsoft Entra ID** → Overview: tenant
  `0b4bca41-b3f5-427c-aeac-2dbcd055f91d`, dominio `matiaspulgar.onmicrosoft.com`. `[CAPTURA]`
- **Users**: mostrar `maria@…` (PEDIDOS_VENDEDOR) y `jose@…` (PEDIDOS_ADMIN). `[CAPTURA]`
- App registrations: SPA (`54b906c8-…`, redirect `localhost:4200` + CloudFront) y
  API `pedidos-api` (`6da3f8f4-…`, scope `access_as_user`, roles app). `[CAPTURA]`

> **Rúbrica EP4:** "creación del tenant en IDaaS y la existencia de usuarios registrados".

## 3. Login OAuth 2.0 / OpenID Connect → JWT (1 min)

- `npm start` → http://localhost:4200 → botón **Login** (Authorization Code + PKCE con MSAL v5).
- Tras el login, Dashboard muestra **claims** y el botón **Copiar access_token**. `[CAPTURA]`
- Pegar el token (o parte) y señalar `iss` (`sts.windows.net/<tenant>/`), `aud`
  (`api://6da3f8f4-…`) y `roles`.

> **Rúbrica EP4:** "el frontend utiliza OAuth 2.0/OpenID Connect para iniciar sesión y
> obtener un JWT válido".

## 4. API Manager en AWS (1 min)

- Consola **API Gateway** → API `pedidos360-httpapi` (HTTP API, stage `$default`). `[CAPTURA]`
- **Authorization**: JWT authorizer con issuer `https://sts.windows.net/<tenant>/` y
  audience `api://6da3f8f4-905c-4c76-abf0-c711d0dd3926`. `[CAPTURA]`
- **Routes**: `/api/pedidos`, `/api/pedidos/{id}`, `/api/pedidos/{id}/estado`, `/health`
  → integración con el ALB del backend. `[CAPTURA]`
- **Integración**: mostrar que el frontend apunta al endpoint del API Manager
  (`environment.prod.ts` → `https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com`).

> **Rúbrica EP4:** "instancia de API Manager en funcionamiento", "configuración que
> permite llamar a los endpoints del backend", "el frontend consume los endpoints a
> través del API Manager".

## 5. El API Manager valida el JWT (1 min)

Ejecutar (PowerShell) contra el endpoint del API Manager:

- Sin token → **401** (`docs`/script `evidencias-api-manager.ps1`). `[CAPTURA]`
- Con token **válido** (ROPC de `maria`) → **200** y JSON de pedidos. `[CAPTURA]`
- Con **token inválido/issuer falso** → **401**. `[CAPTURA]`

> **Rúbrica EP4:** "el API Manager valida JWT, rechazando peticiones inválidas y
> aceptando las correctas". Ver `docs/evidencias/resumen.json` (matriz 12/12).

## 6. Backend y frontend desplegados en la nube (1 min)

- **CloudFront** sirviendo la SPA: https://d2u1dalj9nm2b1.cloudfront.net (incógnito). `[CAPTURA]`
- **EC2 + ALB**: consola EC2 → instancia del ASG `pedidos360-alb` en estado `running`;
  target group `healthy`. `[CAPTURA]`
- Request originado desde la SPA pública llegando al backend (Network/Consola). `[CAPTURA]`

> **Rúbrica EP4:** "el backend y el frontend están desplegados, activos e integrados".

## 7. RabbitMQ en la nube: clúster, colas y exchanges (1.5 min)

- **docker-compose levantando RabbitMQ**: `[CAPTURA]` de `docker compose ps` / UI mostrando
  los contenedores `rabbitmq-node1` y `rabbitmq-node2`. Local:
  `powershell -ExecutionPolicy Bypass -File infra\scripts\rabbitmq-cluster.ps1 status`.
- **Dos nodos en clúster**: UI de RabbitMQ → *Nodes* → `rabbit@rabbitmq-node1` y
  `rabbit@rabbitmq-node2` con `rabbit@rabbitmq-node1` como disc node. `[CAPTURA]`
- **Tres colas + DLQ**: UI → *Queues and Streams*:
  `notificaciones.pedidos` (+`.dlq`), `documentos.pedidos` (+`.dlq`), `tickets.pedidos` (+`.dlq`). `[CAPTURA]`
- **Exchanges direct y topic**: UI → *Exchanges*: `pedido.notificacion` (direct),
  `pedido.documento` y `ticket` (topic), con sus bindings. `[CAPTURA]`

> **Rúbrica EP4:** "dos nodos RabbitMQ en clúster", "tres colas con sus DLQ",
> "exchanges direct y topic", "docker-compose levantando servicios de RabbitMQ".

## 8. Mensajería en vivo (1 min)

1. En la SPA, **crear un pedido** con total ≥ 1000.
2. Mostrar en la UI de RabbitMQ el paso por las colas (mensajes publicados/consumidos):
   notificación → documento → ticket.
3. **Demo DLQ**: publicar un evento con `FORZAR_DLQ` (desde el admin o el backend) y
   mostrar el mensaje en `notificaciones.pedidos.dlq` tras 3 reintentos. `[CAPTURA]`

> **Rúbrica EP3/EP4:** ACK manual + reintentos + DLQ (consumidores `@RabbitListener`
> en `pedidos360-backend/.../messaging/`).

## 9. Microservicio administrador de RabbitMQ (1 min)

Mostrar llamadas REST (Postman/PowerShell) al admin `:8091` con token de `jose`:

- `POST /admin/queues` → **201** crea cola; `DELETE /admin/queues/{nombre}` → **200**. `[CAPTURA]`
- `POST /admin/exchanges` (`type=topic`) → **201**; `DELETE /admin/exchanges/{nombre}`. `[CAPTURA]`
- `POST /admin/bindings` / `DELETE /admin/bindings`. `[CAPTURA]`
- Validación: `POST /admin/queues` con nombre inválido → **400** con detalle por campo. `[CAPTURA]`
- Sin token / sin rol → **401/403**.

> **Rúbrica EP3:** API REST admin (13%), encapsulación en `RabbitAdminService` (10%),
> validación con DTO (7%).

## 10. Cierre (30 s)

- Repaso del hilo: IDaaS → JWT → API Manager → backend → **RabbitMQ desacoplado** → admin.
- Buenas prácticas: nombres centralizados, separación negocio/mensajería, fail-safe,
  ACK manual y DLQ.
- Preguntas.

## Checklist rápido antes de presentar

- [ ] Clúster RabbitMQ arriba (`rabbitmq-cluster.ps1 status`).
- [ ] Backend `:8090`, admin `:8091`, BFF `:8085`, SPA `:4200`.
- [ ] Token de `jose` (admin) y `maria` (vendedor) a mano.
- [ ] CloudFront y EC2 visibles en consola.
- [ ] Capturas `[CAPTURA]` recopiladas en `docs/evidencias/img/`.
