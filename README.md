# buildshield-api-gateway

API Gateway de BuildShield (BobBuilder), parte de la arquitectura de microservicios. Es la única entrada HTTP
(puerto 8080): la web llama a `http://localhost:8080/api/v1/...` y el gateway reenvía a cada servicio.

Stack: Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3 (Spring Cloud Gateway sobre WebFlux),
Spring Security resource server, springdoc 2.8.17.

## Rutas

| Ruta | Servicio | URI por defecto (variable) |
|---|---|---|
| `/api/v1/auth/**`, `/api/v1/users/**` | iam | `http://localhost:8081` (`IAM_URI`) |
| `/api/v1/organizations/**`, `/worksites/**`, `/warehouses/**`, `/materials/**`, `/assignments/**` | organization | `http://localhost:8082` (`ORGANIZATION_URI`) |
| `/api/v1/stock/**` | inventory | `http://localhost:8083` (`INVENTORY_URI`) |
| `/api/v1/dispatches/*/custody` (prioridad sobre dispatches) | audit | `http://localhost:8088` (`AUDIT_URI`) |
| `/api/v1/orders/**`, `/dispatches/**`, `/receptions/**` | ordering | `http://localhost:8084` (`ORDERING_URI`) |
| `/api/v1/discrepancies/**` | discrepancy | `http://localhost:8085` (`DISCREPANCY_URI`) |
| `/api/v1/subscriptions/**`, `/plans/**` | subscription | `http://localhost:8086` (`SUBSCRIPTION_URI`) |
| `/api/v1/notifications/**` | notification | `http://localhost:8087` (`NOTIFICATION_URI`) |
| `/api/v1/audit/**` | audit | `http://localhost:8088` (`AUDIT_URI`) |

## Comportamiento

- **JWT**: valida RS256, emisor `buildshield-iam`, vigencia máxima de 15 minutos y claims `sub`, `org`, `role`
  (la misma política del Core) con `BUILDSHIELD_JWT_PUBLIC_KEY` (contenido PEM). Sin token o con token inválido
  responde 401 `{"code":"UNAUTHENTICATED",...}`. La autorización por rol la hace cada servicio, que además vuelve
  a validar el token.
- **Rutas públicas** (sin token, aunque se envíe uno vencido): `/api/v1/auth/sign-up`, `sign-in`, `refresh`,
  `password-reset/**`, `/actuator/health`, `/swagger-ui.html`, `/v3/api-docs/<svc>`, la foto firmada de una
  evidencia, `GET /api/v1/plans` (catálogo para la página de inicio, US08) y
  `POST /api/v1/subscriptions/webhooks/stripe` (webhook de Stripe: lo autentica su firma en subscription-service).
  En el perfil `local` también `/actuator/gateway/routes`.
- **Cuenta en solo lectura (US10)**: si la suscripción de la organización del token venció, `POST`, `PUT`, `PATCH`
  y `DELETE` responden 402 `{"code":"SUBSCRIPTION_READ_ONLY",...}`. Las lecturas pasan siempre, y también las
  rutas para pagar y para la sesión (`/api/v1/subscriptions/**`, `/api/v1/plans/**`, `/api/v1/auth/**`). El estado
  se lee de `GET /api/v1/subscriptions/current` de subscription-service con el token del usuario y se recuerda por
  organización 30 s (`READ_ONLY_CACHE_TTL`); cualquier petición a `/api/v1/subscriptions/**` lo olvida, para que el
  pago tenga efecto al instante. Si subscription-service no responde (`READ_ONLY_TIMEOUT`, 2 s), la petición pasa:
  una caída de pagos no congela la operación. `READ_ONLY_ENABLED=false` lo desactiva. organization-service aplica la
  misma regla con su copia local del plan (defensa en profundidad).
- **Correlación**: reenvía `X-Correlation-Id` si llega y es válido; si no, genera un UUID. Va en la petición al
  servicio y en la respuesta (también en los 401).
- **CORS**: se resuelve en el gateway para `http://localhost:4200` (`BUILDSHIELD_CORS_ALLOWED_ORIGINS`). Si un
  servicio devuelve sus propias cabeceras CORS, `DedupeResponseHeader` deja una sola.
- **Swagger agregado**: `http://localhost:8080/swagger-ui.html`, con un desplegable por servicio. Cada
  especificación se lee en `/v3/api-docs/<svc>`, que el gateway reescribe a `/api/v1/api-docs` del servicio.
- **Actuator**: `/actuator/health` y `/actuator/gateway/routes`.

## Ejecutar

```bash
./mvnw verify                      # pruebas (backends simulados) y JaCoCo (línea >= 80 %)
BUILDSHIELD_JWT_PUBLIC_KEY="$(cat ~/.buildshield/jwt-public.pem)" \
  java -jar target/buildshield-api-gateway-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Perfiles: `local` (por defecto, servicios en localhost), `test` (pruebas) y `prod` (todas las URI y la clave por
variable, sin Swagger ni tabla de rutas). Para levantar todo el sistema, ver `buildshield-platform`
(`scripts/run-all.ps1` o `docker-compose.yml`).
