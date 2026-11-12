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
- Los `application.yml` usan `${VAR}` sin valor por defecto. Si falta la variable, Spring deja el texto `${VAR}` sin resolver y Postgres responde `password authentication failed`.
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

## Gestión de migraciones con Flyway

**Problema:** con `ddl-auto: update` Hibernate modificaba el esquema solo, sin historial de cambios ni entorno reproducible.

**Decisión:** Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`) y `ddl-auto: validate`. Cada servicio versiona su esquema en `src/main/resources/db/migration`: `V1` esquema y `V2` datos de demo, separados para poder cambiar la demo sin tocar el esquema. Los tipos coinciden con las entidades: ids `BIGINT` (Long) y coordenadas `DOUBLE PRECISION` (como `Coordenada`, con `CHECK` de rango). Los V1 de ms-recursos y ms-operaciones se crean cuando cada servicio tenga su esquema real.

**Alternativas descartadas:** dejar `ddl-auto: update`; usar una migración repetible `R__` para la demo (volvería a insertar las filas cada vez que cambie).

**Consecuencias:** una migración ya aplicada no se edita (Flyway guarda su checksum); los cambios de esquema van en un V nuevo. El V1 de ms-obras se corrigió una única vez antes del merge, y hubo que recrear el volumen con `docker compose down -v`. Las entidades JPA deben usar `GenerationType.IDENTITY` para que `validate` cuadre con `BIGSERIAL`.

## springdoc (Swagger)

**Decisión:** springdoc 3.1.1, fijado en la propiedad `springdoc.version` del pom padre. Con Boot 4.1.2 probamos también 2.5.0: arrancaba y documentaba un controller con DTO validado, pero está fuera de la tabla de soporte (la documentación indica springdoc 3.x para Boot 4). 3.1.1 

**Alternativa descartada:** quedarnos con 2.5.0.

**Consecuencias:** Quedan habilitados por defecto, abiertos sin token (ver “Swagger sin token”) y se apagan en producción con variables de entorno.

## Gateway: rutas, seguridad y correlation-id

**Decisión:** las rutas van bajo `spring.cloud.gateway.server.webmvc.routes`, porque el starter es `spring-cloud-starter-gateway-server-webmvc`. El gateway tiene su propia `SecurityConfig` y su propio filtro, sin depender de `obras-commons`. Solo autentica (valida el JWT); cada servicio autoriza por rol con `@PreAuthorize`. El filtro genera siempre un `X-Correlation-Id` nuevo, ignora el del cliente y lo reenvía a los servicios. `/actuator/health` queda abierto para el healthcheck de Docker (se agregó `spring-boot-starter-actuator`).

**Alternativas descartadas:** `spring.cloud.gateway.routes` (prefijo de la variante reactiva; el starter MVC lo ignora sin avisar y todo da 404). Depender de commons (arrastraría el handler global y el cliente SISTEMA, que el gateway no usa). Autorizar por rol en el gateway (duplica reglas que ya viven en los servicios, y todavía no hay endpoints). Aceptar el correlation-id del cliente (un cliente externo podría falsearlo o repetirlo).

**Consecuencias:** el 401 del gateway sale con cuerpo vacío (pendiente de unificar con el handler de commons). La respuesta del servicio trae el mismo header y el proxy lo copia, así que el filtro lo reemplaza para que salga uno solo. El `issuer-uri` apunta a `localhost`: hay que revisarlo al dockerizar. El id se guarda en el MDC, pero no aparece en los logs hasta que se configure un patrón con `%X{correlationId}`. Verificado con token real: sin token 401, token basura 401, token válido llega a los tres servicios, el id del cuerpo del servicio coincide con el header y `mvn test` del gateway pasa.


## Swagger sin token
**Decisión:** `/v3/api-docs/**`, `/swagger-ui/**` y `/swagger-ui.html` son `permitAll` en `BaseResourceServerConfig` (commons). En producción se apagan con `SPRINGDOC_API_DOCS_ENABLED=false` y `SPRINGDOC_SWAGGER_UI_ENABLED=false`.

**Alternativas descartadas:** dejarlo protegido (el navegador no puede mandar el header `Authorization` al abrir la página, así que Swagger no se puede usar) y abrir solo la UI protegiendo el contrato (la UI pide el contrato sin token).

**Consecuencias:** el contrato de cada servicio es público en su puerto. Los servicios no se exponen fuera de Docker y el gateway no enruta esas rutas. 

## OpenApi
**Decisión:** cada servicio tiene una clase `OpenApiConfig` con `@OpenAPIDefinition` y `@SecurityScheme` que declaran un esquema `bearerAuth` (HTTP bearer, JWT) como requisito global, y un título propio. Así Swagger UI muestra el botón Authorize.

**Alternativas descartadas:** una sola clase en commons (commons no tiene springdoc y todos los servicios tendrían el mismo título) y un `@Bean OpenAPI` (equivale a las anotaciones pero es más largo).

**Consecuencias:** se repiten unas 20 líneas en tres servicios.

## Matriz de permisos
**Decisión:** `docs/matriz-permisos.md` define qué rol puede cada capacidad. Las filas con fuente en el enunciado están fijas; las demás son propuesta del equipo.

**Alternativas descartadas:** esperar a los controladores para escribirla (los controladores se escribirían sin un criterio común).

**Consecuencias:** quedan sin definir el OPERARIO, el alcance del AUDITOR y quién da de alta obras y recursos. Se resuelve antes del paso 8. Las pruebas 401/403/2xx por endpoint se completan en los pasos 8 a 10.

## Rutas del gateway sin StripPrefix

**Decisión:** las rutas del gateway no quitan el prefijo: `/api/obras/**` llega al servicio como `/api/obras/...`. Los `@RequestMapping` de ms-obras, ms-recursos y ms-operaciones empiezan con `/api/obras`, `/api/recursos` y `/api/operaciones`.

**Alternativas descartadas:** `StripPrefix=2` en cada ruta (los controladores se mapearían sin el prefijo y la ruta del servicio sería distinta de la que ve el cliente; es razonamiento, no se probó).

**Consecuencias:** el Swagger de cada servicio muestra las mismas rutas que usa el cliente. Verificado: el servicio respondió con `instance: /api/obras/x`. Si el equipo prefiere `StripPrefix`, hay que decidirlo antes del paso 8.

## Integración con Proveedor Externo de Rutas (OSRM)
* **Contexto:** El microservicio necesita calcular distancias y duraciones estimadas de traslado entre depósitos, cuadrillas y obras viales para planificar las reservas.
* **Decisión:** Se implementó una abstracción mediante la interfaz `ProveedorRutas` y su implementación concreta `OsrmProveedorRutas`, consumiendo la API pública de Project OSRM (`/route/v1/driving/...`) mediante `RestTemplate`.
* **Consecuencias:** 
  * Se desacopla la lógica de negocio de la API concreta de mapas, permitiendo migrar a otro proveedor (Google Maps, Mapbox, etc.) sin modificar la capa de servicios.
  * Se introduce una dependencia de red externa que requiere mecanismos de tolerancia a fallos.

---

## Estrategia de Caché Local en Memoria (`@Cacheable`)
* **Contexto:** Las consultas de cálculo de rutas entre coordenadas idénticas son recurrentes; consultar una API externa en cada petición genera latencia innecesaria y consumo de cuota de red.
* **Decisión:** Se anotó el método `calcularRuta` con `@Cacheable(value = "rutas", key = "{#origenLat, #origenLon, #destinoLat, #destinoLon}")`, respaldado por `ConcurrentMapCacheManager`.
* **Consecuencias:**
  * Respuestas en tiempo constante ($O(1)$) para trayectos previamente calculados.
  * Reducción significativa de peticiones salientes hacia la API externa de OSRM.

---

## Tolerancia a Fallos y Resiliencia (Circuit Breaker y Rate Limiter)
* **Contexto:** OSRM es un servicio de terceros sujeto a caídas de red, latencias elevadas o bloqueos por exceso de tráfico (*HTTP 429 / 500*). El fallo de este servicio externo no debe interrumpir la gestión de reservas en el sistema.
* **Decisión:** Se integró **Resilience4j** configurando dos patrones coordinados sobre el cliente de rutas:
  * **Rate Limiter (`osrmLimiter`):** Limita el caudal de peticiones salientes por ventana de tiempo para respetar los límites de uso del proveedor externo.
  * **Circuit Breaker (`osrmBreaker`):** Monitorea la tasa de errores y abre el circuito ante fallos repetidos para evitar degradar los hilos de ejecución del microservicio.
  * **Fallback (`fallbackCalcularRuta`):** Ante cualquier excepción o apertura del circuito, se calcula la distancia geodésica en línea recta mediante la **fórmula de Haversine** y se proyecta el tiempo de traslado asumiendo una velocidad promedio de `50 km/h` (con un piso mínimo de 5 minutos). El resultado se etiqueta explícitamente con `fuente: "ESTIMADA"`.
* **Consecuencias:** El microservicio garantiza alta disponibilidad funcional mediante *degradación elegante*, evitando propagar errores `500` hacia `ms-operaciones` o los usuarios finales.

---

## Estrategia de Pruebas de Integración Desacopladas
* **Contexto:** La ejecución de tests automatizados no debe depender de conectividad a internet ni requerir infraestructura pesada levantada (Keycloak, base de datos en contenedores) para validar la lógica del cliente y la caché.
* **Decisión:** Se implementaron pruebas de integración utilizando `MockRestServiceServer` interceptando el `RestTemplate` interno de `OsrmProveedorRutas`, validando respuestas exitosas (`200 OK`) y el comportamiento de aciertos de caché sin realizar tráfico de red real.
* **Consecuencias:** Ejecución determinística y reproducible de pruebas sin consumo de servicios externos.

## Dependencia entre fases (ms-obras)

**Problema:** RN-01 necesita saber si la fase anterior está completada y RN-02 necesita encontrar las fases que dependen de la que se reprograma.

**Decisión:** cada fase guarda `fase_anterior_id`. RN-01 mira esa referencia y RN-02 recorre la cadena hacia adelante con `findByFaseAnteriorId`, corriendo inicio y fin la misma cantidad de días (mantiene la duración) y cortando si detecta un ciclo.

**Alternativa descartada:** número de orden por tramo (la dependencia queda implícita y hay que renumerar al insertar una fase) y tabla muchos a muchos (permite fases en paralelo, que el TP no pide).

**Consecuencias:** la cadena es lineal por tramo. Al crear una fase se valida que la anterior sea del mismo tramo y que no tenga ya una siguiente (409). Cubierto con tests.

## Materiales por superficie (RN-03)

**Problema:** el TP no define cuánto material lleva cada tipo de fase por m².

**Decisión:** tabla `consumos_material` (tipo de fase, material, consumo por m², unidad) cargada por Flyway. El cálculo `superficie × consumo`, con `BigDecimal` y redondeo a 2 decimales, se hace al consultar.

**Alternativa descartada:** calcular y guardar los materiales de cada fase (hay que recalcular si cambia la superficie o la tabla).

**Consecuencias:** los consumos son valores de demo, calibrables sin tocar el código. Si cambian, cambia el resultado de las fases existentes.

## Contrato entre ms-obras y ms-operaciones

**Problema:** Operaciones necesita datos de la fase y poder iniciarla, pero ningún servicio puede leer las tablas de otro.

**Decisión:** `GET /api/obras/fases/{id}/datos-operacion` (SISTEMA, PLANIFICADOR y CAPATAZ) devuelve tipo de trabajo, tramo, coordenadas (latitud y longitud por separado), materiales y si la fase previa está completa. Operaciones cambia estados llamando a `POST .../iniciar` y `POST .../reprogramar`. El armado de datos vive en `OperacionService`, separado de `FaseService`.

**Alternativa descartada:** que Operaciones lea la base de Obras, o compartir los DTOs en `obras-commons`.

**Consecuencias:** Obras sigue siendo dueña del estado y es quien valida RN-01. El DTO es propio de Obras y Operaciones define el suyo.

## Errores de ms-obras con las excepciones de obras-commons

**Problema:** teníamos una `ReglaNegocioException` local que el manejador global de commons no conoce (saldría como 500).

**Decisión:** se eliminó y se usan las de commons: 404 (`RecursoNoEncontradoException`), 409 (`ConflictoEstadoException`, estado incompatible) y 422 (`ReglaNegocioException` con código: RN-01, RN-02, RN-03 y los propios FASE-FECHAS y FASE-TRAMO).

**Alternativa descartada:** manejador de errores propio en ms-obras.

**Consecuencias:** mismo formato RFC 7807 en los tres servicios.

## DTOs y validación en el borde (ms-obras)

**Problema:** las entidades tienen relaciones `LAZY` y exponerlas ataría la API al modelo de la base.

**Decisión:** los controllers reciben y devuelven DTOs, con Bean Validation. Las coordenadas se validan con `@DecimalMin` y `@DecimalMax` y no con `Coordenada` de commons, porque esa clase lanza `IllegalArgumentException` y saldría 500 en vez de 400.

**Alternativa descartada:** construir `Coordenada` en el service.

**Consecuencias:** los rangos quedan escritos en dos lugares. Probado con latitud 91 (400).

## Alcance de la API de ms-obras

**Decisión:** crear obra, tramo y fase; consultar el cronograma de un tramo; iniciar y reprogramar fases; datos para Operaciones.

**Alternativa descartada:** CRUD completo. Quedó fuera editar y borrar, editar consumos por API (se cambian por migración), paginación y filtros.

**Consecuencias:** cumplir lo comprometido pesa más que agregar extras. Lo incluido tiene tests y seguridad por rol.

## Migración V3 de ms-obras

**Problema:** V1 y V2, ya en `main`, no coincidían con las entidades (faltaban el nombre del tramo y las fechas de las fases, dos columnas tenían otro nombre y no existía `consumos_material`) y con `ddl-auto: validate` el servicio no arrancaba.

**Decisión:** V3 corrige y completa el esquema y carga los consumos de demo. No se editaron V1 y V2.

**Alternativa descartada:** editar V1 otra vez (Flyway guarda su checksum y falla en las bases que ya lo aplicaron).

**Consecuencias:** quien ya tenía la base debe recrearla (`docker compose down -v`).
