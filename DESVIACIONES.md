# Desviaciones frente a la arquitectura v4

Servicio `buildshield-api-gateway`. Fuente: C4 de contenedores, pág. 21 de `buildshield-presentacion-v4.pdf`, y la
sección «Microservicio 2» de la tarea. El gateway no tiene base de datos, así que no hay tablas ni columnas que
comparar con el ER.

## Autorización por rol

La tarea dice que el gateway hace la autorización por rol. Aquí se reparte así:

- El gateway rechaza con 401 un token cuyo claim `role` no sea `ADMINISTRATOR`, `WAREHOUSE_MANAGER` o `SITE_MANAGER`.
- El permiso de cada endpoint lo decide su servicio con `@PreAuthorize`, y el servicio responde 403.

Motivo: una tabla de permisos por ruta en el gateway duplicaría los `@PreAuthorize` de los ocho servicios y se
desalinearía con cada cambio. Las reglas de alcance, como «solo las obras asignadas», solo se pueden evaluar en el
servicio, que tiene las proyecciones.

## Comportamiento que no aparece en el diagrama C4

| Elemento | Motivo |
|---|---|
| `ReadOnlyAccountFilter` y `SubscriptionStatusClient` | Bloqueo 402 de la cuenta en solo lectura (US10), pedido en la tarea. Consulta `GET /api/v1/subscriptions/current` por REST: es la única llamada del gateway a un servicio que no reenvía una petición del usuario. Si subscription no responde, la petición pasa. |
| Swagger agregado (`/swagger-ui.html`, `/v3/api-docs/<svc>`) | Documentación OpenAPI de todos los servicios en un solo lugar (RNF17). Apagado en el perfil `prod`. |
| `DownstreamUnavailableHandler` | Responde 503 `SERVICE_UNAVAILABLE` cuando un servicio está caído, pedido en la tarea. |

## Formato de los errores

Los errores siguen `{"code","message","details"}`, el mismo formato que devuelven los servicios, y no `ProblemDetail`
como dice el Anexo A.9.
