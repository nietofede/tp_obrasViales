# Bitácora de decisiones — GOVP

Cada entrada: decisión, alternativas descartadas y consecuencias.

## Una base de datos por microservicio

**Problema inicial:** Se definia de diferente manera la base de datos, tanto en 01-init-schemas.ql, como en application.yml.

**Decisión:** `ms_obras`, `ms_recursos` y `ms_operaciones`, cada una con su usuario dueño (`usr_obras`, `usr_recursos`, `usr_operaciones`), en una única instancia. Las crea `docker/init-db/01-init-schemas.sql` y los `application.yml` apuntan a ellas. Usamos REVOKE CONNECT...FROM PUBLIC para que cada usuario pueda conectarse solo a su base de datos

**Alternativa descartada:** una base compartida con un esquema por servicio.

**Consecuencias:** ningún servicio puede leer tablas de otro, solo vía API. Flyway trabaja en el esquema `public` de cada base. Los scripts de `init-db` solo corren con el volumen vacío (`docker compose down -v` para re-crear).

## Usuarios keycloak

**Decision:** Se crearon usuarios de demo en el realm (`admin`, `planificador`, `capataz`, `operario`, `auditor`), uno por rol. Todos con contraseña `<rol>123`. Solo para demo/local 

**Alternativa descartada:** Se descarto crearlos a mano ya que no sirve para el arranque desde cero

**Consecuencias:** Permite arrancar desde cero sin pasos manuales. 


## Secretos env

**Problema:** las contraseñas de las bases estaban escritas en application.yml y en el SQL de init-db, y el secreto de `servicio-sistema` en el realm era `**********`. Con ese secreto, un client_credentials devolvía un token con rol SISTEMA.

**Decision:** 
- Dejamos las cotnraseñas y secretos seteadas en el env.example, que es la plantilla con los valores de demo. 
- 01-init-schemas.sql lee las pw desde el .env con \set de psql.
- Compose usa ${VAR:?message} para las contraseñas que lee el init, si falta alguna, al hacer docker compose up, falla y devuelve un mensajke. 
- Los `application.yml` usan `{$VAR}` sin valor por defecto
- El realm usa `${SERVICIO_SISTEMA_SECRET}`

**Alternativa descartada:** Se descarto usar placesholders para agilizar el arranque desde cero

**Consecuencias:** Permite hacer cp .env.example .env y compose up -d y que ande a la primera sin tener que configurar nada. Sin la variable, un servicio falla ("password authentication failed"). Los valoreas demo son publicos, hay que cambiarlos fuera de local!.

## Version Java y Spring Boot

**Problema:** Estaba configurado en Java 21 con Spring Boot 4.1.2 y en nuestro documento figuraba Java 25 con Spring Boot 3.2

**Decision:** Pasamos a Java 25 y Spring Boot mantenemos en 4.1.2 ya que, hasta lo que sabemos, 3.2 no es compatible con Java 25

## Contenido de la librería común (`obras-commons`)

**Problema:** Tres microservicios (`ms-obras`, `ms-recursos`, `ms-operaciones`) requieren lógica idéntica para validar tokens JWT, extraer roles de Keycloak, estructurar respuestas de error homogéneas, propagar identificadores de trazabilidad (Correlation ID) y comunicarse entre sí como clientes autenticados (`SISTEMA`). Duplicar este código en cada servicio viola DRY y dificulta el mantenimiento; compartir de más introduce acoplamiento indebido.

**Decisión:** 
- **Qué entra:** Exclusivamente código transversal de infraestructura y soporte técnico:
  - Configuración base de Resource Server con conversión de roles de Keycloak.
  - Manejo global de excepciones bajo estándar RFC 7807 (`ProblemDetail`) y excepciones base de dominio.
  - Filtro HTTP de `Correlation-Id` y almacenamiento en el `MDC` para trazabilidad de logs.
  - Servicio e interceptor de cliente HTTP (`RestClient`) para la cuenta `SISTEMA` vía OAuth2 *Client Credentials*.
  - DTO de valor puro reusable: `Coordenada`.
- **Qué NO entra:** Entidades JPA de persistencia, lógica de negocio ni DTOs de contrato funcional entre microservicios. Cada microservicio es dueño de su modelo de datos y de sus contratos API.

**Alternativas descartadas:**
1. *Copiar y pegar código en cada microservicio:* Alto riesgo de divergencias en seguridad y formatos de error.
2. *Poner entidades y DTOs compartidos en `commons`:* Acoplamiento fuerte entre microservicios. Cualquier cambio en un contrato obligaría a recompilar y desplegar toda la plataforma al unísono.

**Consecuencias:** Se garantiza consistencia en la seguridad, observabilidad y manejo de errores respetando la independencia de ciclo de vida y despliegue de cada microservicio.

**Limitación conocida:** el handler genérico (`Exception.class`) todavía convierte los errores estándar de Spring MVC (ruta inexistente, método no permitido) en 500 en vez de 404 o 405. Alternativas evaluadas: extender `ResponseEntityExceptionHandler`. Queda pendiente de resolver antes de escribir los tests de las RN.
