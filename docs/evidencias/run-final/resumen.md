# Evidencias API Manager - 2026-09-22 19:29

Endpoint: https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com

| # | Caso | Resultado esperado | Obtenido |
|---|------|--------------------|----------|
| 1  | `GET` /api/pedidos | 401 | **401** |
| 2  | `GET` /api/pedidos | 401 | **401** |
| 3  | `GET` /api/pedidos | 401 | **401** |
| 4  | `GET` /api/pedidos | 200 | **200** |
| 5  | `GET` /api/pedidos/7 | 200 | **200** |
| 6  | `POST` /api/pedidos | 201 | **201** |
| 7  | `PATCH` /api/pedidos/7/estado | 403 | **403** |
| 8  | `DELETE` /api/pedidos/7 | 403 | **403** |
| 9  | `PATCH` /api/pedidos/7/estado | 200 | **200** |
| 10 | `DELETE` /api/pedidos/7 | 204 | **204** |
| 11 | `OPTIONS` /api/pedidos (preflight CORS) | 200 | **204** |
| 12 | `GET` /health (ALB directo) | 200 | **200** |

Archivos de respuesta por caso en esta carpeta.
