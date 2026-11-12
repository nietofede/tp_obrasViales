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

**Decisión:** `GET /api/obras/internos/fases/{id}/contexto-ejecucion` (solo SISTEMA) devuelve tipo de trabajo, tramo, coordenadas (latitud y longitud por separado), materiales y si la fase previa está completa. Operaciones cambia el estado de las fases llamando a `POST /api/obras/internos/fases/{id}/avances` (registra el avance; al llegar al 100 % la fase queda COMPLETADA), `PATCH /api/obras/internos/fases/{id}/riesgo` y `PATCH /api/obras/fases/{id}/programacion` (reprogramar). `POST /api/obras/fases/{id}/iniciar` sigue disponible para CAPATAZ y SISTEMA. Los nombres y los roles son los del cuestionario de compromiso. El armado de datos vive en `OperacionService`, separado de `FaseService`.

**Alternativa descartada:** que Operaciones lea la base de Obras, o compartir los DTOs en `obras-commons`.

**Consecuencias:** Obras sigue siendo dueña del estado y es quien valida RN-01. El DTO es propio de Obras y Operaciones define el suyo.

## Errores de ms-obras con las excepciones de obras-commons

**Problema:** teníamos una `ReglaNegocioException` local que el manejador global de commons no conoce (saldría como 500).

**Decisión:** se eliminó y se usan las de commons: 404 (`RecursoNoEncontradoException`), 409 (`ConflictoEstadoException`: estado incompatible y RN-01, como indica el cuestionario) y 422 (`ReglaNegocioException` con código: RN-02, RN-03, AVANCE y los propios FASE-FECHAS y FASE-TRAMO).

**Alternativa descartada:** manejador de errores propio en ms-obras.

**Consecuencias:** mismo formato RFC 7807 en los tres servicios.

## DTOs y validación en el borde (ms-obras)

**Problema:** las entidades tienen relaciones `LAZY` y exponerlas ataría la API al modelo de la base.

**Decisión:** los controllers reciben y devuelven DTOs, con Bean Validation. Las coordenadas se validan con `@DecimalMin` y `@DecimalMax` y no con `Coordenada` de commons, porque esa clase lanza `IllegalArgumentException` y saldría 500 en vez de 400.

**Alternativa descartada:** construir `Coordenada` en el service.

**Consecuencias:** los rangos quedan escritos en dos lugares. Probado con latitud 91 (400).

## Alcance de la API de ms-obras

**Decisión:** los 15 endpoints que el equipo comprometió en el cuestionario (sección 07), con sus nombres y roles: alta y edición de obras y tramos, alta de fases, consulta de obras (con estado, avance, tramos y fases), de un tramo, de una fase, de sus materiales y del cronograma de una obra, reprogramación en cascada, contexto de ejecución para Operaciones, registro de avance y marca de fase en riesgo. Se mantienen tres extras: iniciar una fase, listar los tramos de una obra y el cronograma de un tramo.

**Alternativa descartada:** el alcance reducido que se adoptó primero (crear, consultar y reprogramar, sin edición). Se decidió sin tener el cuestionario a la vista y contradecía F01 («alta y edición»), que es la base con la que la cátedra corrige.

**Consecuencias:** sigue fuera borrar, editar los consumos por API (se cambian por migración), paginación y filtros. Editar un tramo no obliga a recalcular nada guardado: los materiales se calculan al consultar (ver «Materiales por superficie (RN-03)»).

## Migración V3 de ms-obras

**Problema:** V1 y V2, ya en `main`, no coincidían con las entidades (faltaban el nombre del tramo y las fechas de las fases, dos columnas tenían otro nombre y no existía `consumos_material`) y con `ddl-auto: validate` el servicio no arrancaba.

**Decisión:** V3 corrige y completa el esquema y carga los consumos de demo. No se editaron V1 y V2.

**Alternativa descartada:** editar V1 otra vez (Flyway guarda su checksum y falla en las bases que ya lo aplicaron).

**Consecuencias:** quien ya tenía la base debe recrearla (`docker compose down -v`).


## Consultas de lectura en ms-obras

**Problema:** la revisión del PR notó que no se podían ver ni listar las obras, y que el cronograma exigía conocer el id del tramo de antemano.

**Decisión:** se agregaron `GET /api/obras`, `GET /api/obras/{obraId}` y `GET /api/obras/{obraId}/tramos`, para PLANIFICADOR, CAPATAZ y AUDITOR (los mismos roles de lectura del cronograma). Devuelven DTOs y responden 404 si la obra no existe.

**Alternativa descartada:** paginación y filtros, porque el volumen del TP no lo justifica.

**Consecuencias:** quedó sin implementar borrar. Editar y el estado EN_RIESGO se incorporaron después, al compararlo con el cuestionario (ver «Alineación de ms-obras con el cuestionario»).

## Alineación de ms-obras con el cuestionario

**Problema:** al comparar ms-obras con la sección 07 del cuestionario faltaban 7 de los 15 endpoints comprometidos, dos rutas tenían otro nombre y ninguna llamada podía completar una fase, de lo que depende RN-01.

**Decisión:**
- Se implementaron los 7 faltantes (editar obra y tramo, consultar tramo y fase, materiales de una fase, avances y riesgo) y se renombraron `reprogramar` a `PATCH /fases/{id}/programacion` y `datos-operacion` a `GET /internos/fases/{id}/contexto-ejecucion`, solo para SISTEMA. Regla de rutas: prefijo `/api/obras` más la ruta del cuestionario, porque el gateway no recorta el prefijo.
- `POST /internos/fases/{id}/avances` guarda el avance real en la columna `avance_porcentaje` (migración V4) y responde 201. El primer avance de una fase PENDIENTE la inicia y aplica RN-01. Al llegar al 100 % queda COMPLETADA. El avance no puede retroceder (422 con código `AVANCE`).
- `PATCH /internos/fases/{id}/riesgo` pasa la fase al nuevo estado EN_RIESGO. En ese estado no admite avances ni inicio. El PLANIFICADOR lo resuelve reprogramándola, y vuelve a PENDIENTE. Quién decide cuándo marcarla (al llegar a N suspensiones) es Operaciones.
- El estado y el avance de una obra se calculan al consultar: el avance es el promedio del avance de sus fases y el estado es PLANIFICADA, EN_EJECUCION o COMPLETADA.
- RN-01 responde 409, como indica el cuestionario, en lugar de 422. Se pierde el campo `codigoRegla` en ese caso, porque la excepción 409 de commons no lo trae; el mensaje empieza con «RN-01:».

**Alternativa descartada:** dejar las rutas anteriores y documentar la diferencia (una diferencia sin justificar con el compromiso baja el puntaje de ALC-01), y guardar estado y avance de la obra en la base (habría que mantenerlos sincronizados con las fases).

**Consecuencias:** siguen abiertas estas diferencias con el cuestionario. El cuestionario dice que los cambios de alcance se acuerdan con la cátedra, así que conviene decidirlas en equipo:
- Los ids son `BIGSERIAL` y el DER comprometido usa `uuid`.
- El proyecto usa Spring Boot 4.1.2 y el cuestionario dice 3.2 (el roadmap, tarea 4, pedía avisar a la cátedra).
- El DER pone `material_requerido` en la fase; acá se calcula al consultar.
- La obra no guarda `estado` ni `fecha_inicio`; se derivan o no existen.
- RN-03: el cuestionario pide rechazar un tipo de trabajo sin rendimiento definido; acá devuelve una lista vacía (RELEVAMIENTO no consume material).

## Ajustes previos de ms-operaciones antes de integrar con Obras

**Problema:** antes de conectar ms-operaciones con ms-obras había tres desalineaciones. Las rutas estaban bajo `/api/v1/...`, pero el gateway solo enruta `/api/operaciones/**` y no recorta el prefijo (ver «Rutas del gateway sin StripPrefix»). Los tipos de trabajo no coincidían con los `TipoFase` de Obras (`SUB_BASE` y `LOSA` frente a `SUBBASE` y `LOSA_HORMIGON`, y faltaba `RELEVAMIENTO`), así que `valueOf` sobre lo que manda Obras fallaría. Y varias excepciones (`OrdenNoEncontrada`, `TransicionInvalida`, `IllegalArgumentException`, `IllegalStateException`, `RuntimeException`) no las conoce el manejador global de commons y salían como 500.

**Decisión:**
- Los controllers quedan en `/api/operaciones/ordenes` y `/api/operaciones/umbrales-climaticos`, con los nombres del PDF. El resto de las rutas del PDF se agregan al implementar cada funcionalidad.
- `TipoTrabajo` usa los mismos nombres que `TipoFase` de Obras. El `V2` se corrigió (todavía no estaba en `main`, por eso se pudo editar; una vez mergeado ya no se edita).
- `RELEVAMIENTO` tiene un umbral permisivo de demo (humedad 0,60, lluvia 40 %, temperatura mínima 0): es la primera fase de cada tramo y, sin umbral, no se podría habilitar nunca y bloquearía las siguientes por RN-01. Los valores son de demo y el ADMIN los puede ajustar con `PUT /api/operaciones/umbrales-climaticos/{tipoTrabajo}`.
- Las excepciones propias heredan de las de commons: `OrdenNoEncontrada` da 404, `TransicionInvalida` da 422 con código `RN-11`, un tipo de trabajo sin umbral da 422 con `RN-09` y resolver el riesgo de una orden que no está en riesgo da 409.

**Alternativa descartada:** mantener un traductor entre `TipoTrabajo` y `TipoFase` (dos nombres para lo mismo y un lugar más donde desalinearse), y no cargar umbral para `RELEVAMIENTO` (bloquea la cadena de fases).

**Consecuencias:** `EN_RIESGO` y `FINALIZADA` siguen siendo estados de la orden aunque el PDF habla de la fase en riesgo; se revisan al implementar RN-12. Quien tenga una base de ms-operaciones anterior debe recrearla (`docker compose down -v`).

## Cliente de ms-operaciones hacia ms-obras

**Problema:** Operaciones necesita datos de la fase (tramo, coordenadas, tipo de trabajo, si la fase previa está completa) y ningún servicio puede leer las tablas de otro.

**Decisión:** `ObrasClient` (interfaz) con su implementación `ObrasHttpClient`, que llama a `GET /api/obras/internos/fases/{id}/contexto-ejecucion` con el `sistemaRestClient` de commons (token de la cuenta SISTEMA). La respuesta se recibe en un DTO propio, `ContextoFaseDTO`, que ignora campos desconocidos y trae el tipo de trabajo y el estado como texto. Un 404 de Obras se traduce a 404 («la fase no existe en ms-obras»); cualquier otro fallo (5xx, 401, 403, sin conexión) se traduce a `ServicioNoDisponibleException`, que ms-operaciones responde con 503 sin detalles internos (el detalle va al log). Al crear una orden se valida que la fase exista y que pertenezca al tramo indicado (422 con código `ORDEN-TRAMO`). `crearOrden` no es transaccional, para no mantener una transacción de base abierta mientras se espera la respuesta HTTP. Verificado con Obras real: 201, 404, 422 y 503 con Obras apagado. Fue la primera vez que `SistemaTokenService` se probó contra Keycloak.

**Alternativas descartadas:** compartir el DTO de Obras por commons (acopla los dos servicios: un cambio en Obras obligaría a recompilar Operaciones); dejar que un error de red salga como 500 (no informa la causa); validar RN-01 al crear la orden (el PDF la ubica al habilitar).

**Consecuencias:** el `sistemaRestClient` de commons no define timeouts, así que una Obras colgada (no caída) podría dejar la llamada esperando; queda pendiente. Si Obras cambia la ruta o el contrato hay que actualizar el cliente y su test. La URL de Obras está en `app.servicios.obras-url` y apunta a localhost: al dockerizar se cambia por el nombre del servicio.

## Habilitación de órdenes en ms-operaciones (Obras + Recursos + clima)

- **Un solo flujo.** `POST /api/operaciones/ordenes/{id}/habilitacion` toma una orden PROGRAMADA (o RECURSOS_ASIGNADOS si un intento anterior quedó a medias) y hace: contexto de la fase en Obras (RN-01) → umbral del tipo de trabajo (RN-09) → reserva en Recursos (RN-04 a RN-08) → clima → HABILITADA o SUSPENDIDA.
- **Orden de los pasos.** El umbral se busca antes de reservar para no reservar recursos de una orden que igual no se puede evaluar.
- **Rechazo de Recursos (422) o Recursos caído (503):** la orden queda como estaba y el error se informa. Recursos es idempotente por orden, así que reintentar es seguro.
- **Clima OK:** se confirma la reserva y la orden queda HABILITADA. Si la confirmación falla (reserva vencida, Recursos caído) se deshace todo y la orden queda como estaba.
- **Clima malo:** SUSPENDIDA y se libera la reserva. Si Recursos no responde al liberar, no se frena la suspensión: la reserva provisoria vence sola por TTL (30 min).
- **Clima caído:** PENDIENTE_CONFIRMACION_CLIMATICA y se conserva la reserva.
- **Hora de salida del camión.** Recursos devuelve solo la hora; la orden guarda `fechaProgramada` + esa hora.
- **Proceso diario.** `POST /api/operaciones/habilitaciones-diarias` con `{"fecha":"yyyy-MM-dd"}` (PLANIFICADOR o SISTEMA) habilita las órdenes PROGRAMADAS y RECURSOS_ASIGNADOS de la fecha. Cada orden va en su propia transacción: una que falla no frena a las demás. Devuelve por orden el estado resultante y el motivo si falló. Se puede reejecutar.
- **Pendiente.** La transacción de `habilitarOrden` sigue abierta durante las llamadas HTTP; se revisa junto con la saga de suspensión.

## Saga de suspensión y RN-12 en ms-operaciones

- **Orquestación desde Operaciones** (no coreografía de eventos): `SuspensionService` ejecuta los pasos en orden y es quien decide qué hacer ante cada falla. Es más fácil de seguir, de probar con simulaciones y de reintentar; el costo es que Operaciones conoce los pasos de Obras y Recursos.
- **Pasos:** 1) liberar la reserva en Recursos; 2) registrar la suspensión; 3) contar las suspensiones vigentes **de la fase** (RN-12 las cuenta por fase, no por orden); 4) si no llegó al límite, reprogramar la fase en Obras (que corre en cascada las dependientes, RN-02) y crear una orden nueva PROGRAMADA, y la orden suspendida queda REPROGRAMADA; 5) si llegó al límite, marcar la fase EN_RIESGO en Obras y dejar la orden SUSPENDIDA, sin orden nueva, hasta que decida el PLANIFICADOR.
- **Fecha nueva:** la fase se reprograma al día siguiente de la fecha de la orden suspendida. Si Obras la rechaza (RN-02, por ejemplo), el error se informa y no se crea nada. Es una simplificación: si hace falta otra regla de fechas, es un cambio en un solo lugar.
- **Falla de Obras en medio de la saga:** se deshace todo lo local y se informa el error; la orden queda como estaba y se puede reintentar. Lo ya liberado en Recursos no molesta: liberar es idempotente.
- **Falla de Recursos al liberar:** no frena la suspensión; la reserva provisoria vence sola por TTL (30 min).
- **Límite configurable:** tabla `configuracion_suspensiones` (una fila, valor inicial 3), `PUT /api/operaciones/configuracion/limite-suspensiones` solo ADMIN.
- **Historial:** tabla `suspensiones` (orden, fase, motivo, detalle, fecha, vigente). `vigente` deja de ser verdadero cuando el PLANIFICADOR decide sobre la fase, para que el conteo empiece de nuevo.
- **Fases en riesgo:** tabla `fases_en_riesgo` con las pendientes de decisión (migración V3).
- **Decisión del PLANIFICADOR** (`POST /api/operaciones/fases-en-riesgo/{faseId}/decisiones`): REPROGRAMAR (exige `nuevaFechaInicio`) reprograma la fase en Obras, crea una orden nueva y deja la suspendida REPROGRAMADA; CANCELAR deja la orden CANCELADA. En los dos casos las suspensiones de la fase pasan a no vigentes (el historial se conserva) y el conteo empieza de nuevo. Obras no tiene un endpoint para cancelar fases, así que al cancelar la fase sigue EN_RIESGO en Obras.
- **Consultas:** `GET /api/operaciones/suspensiones?faseId=&ordenId=` (PLANIFICADOR, AUDITOR) y `GET /api/operaciones/fases-en-riesgo` (PLANIFICADOR, AUDITOR, solo pendientes).
- **Estados retirados:** `EN_RIESGO` y `FINALIZADA` ya no son estados de la orden (el riesgo es de la fase, como dice el PDF) y se eliminó `PATCH /ordenes/{id}/resolver-riesgo`. Reemplaza lo dicho en «Ajustes previos de ms-operaciones».

## Ejecución de órdenes en ms-operaciones

- **Inicio** (`PATCH /api/operaciones/ordenes/{id}/inicio`, CAPATAZ): HABILITADA pasa a EN_EJECUCION. No llama a Obras: ahí el primer avance es el que inicia la fase y vuelve a aplicar RN-01.
- **Rechazo** (`PATCH /ordenes/{id}/rechazo`, CAPATAZ, con `motivo`): solo una orden HABILITADA. Se suspende con motivo RECHAZO y corre la misma saga que una suspensión por clima, con RN-12 incluida; el rechazo del capataz cuenta para el límite de la fase.
- **Avances** (`POST /ordenes/{id}/avances`, CAPATAZ, con `porcentaje`): solo una orden EN_EJECUCION. Se reenvía a Obras. El porcentaje es el acumulado de la fase (1 a 100) y no puede retroceder; los rechazos de Obras (409 si la fase está en riesgo o completada, 422 `AVANCE`) se informan tal cual.
- **Cierre:** ver «Cierre de órdenes en ms-operaciones».

## Resolución climática (RN-10) en ms-operaciones

- **Cuándo ocurre:** si falta cualquier dato climático (o Open-Meteo falla o tarda más de 5 s) la orden no se habilita sola: queda PENDIENTE_CONFIRMACION_CLIMATICA y conserva su reserva de recursos.
- **Quién decide:** `PATCH /api/operaciones/ordenes/{id}/resolucion-climatica` (solo CAPATAZ), con `decision` HABILITAR o SUSPENDER y un `motivo` opcional.
- **HABILITAR** confirma la reserva en Recursos y deja la orden HABILITADA. Si la reserva ya venció por TTL (30 min), Recursos responde 409 y la orden sigue pendiente; queda la opción de suspenderla, que reserva de nuevo al volver a habilitar la orden nueva.
- **SUSPENDER** corre la saga de suspensión con motivo CLIMA (cuenta para RN-12), con el motivo del capataz o uno por defecto.
- Solo se puede resolver una orden en PENDIENTE_CONFIRMACION_CLIMATICA (RN-11).

## Cierre de órdenes en ms-operaciones

- **Endpoint:** `PATCH /api/operaciones/ordenes/{id}/cierre` (solo CAPATAZ), con `horasMaquina` y `materiales` (lista de `material`, `cantidad`, `unidad`; puede ir vacía si no hubo consumo).
- **Efecto:** una orden EN_EJECUCION pasa a COMPLETADA y queda registrado su cierre. Cualquier otro estado da 422 `RN-11`, así que una orden se cierra una sola vez.
- **Forma del consumo:** el PDF solo dice «consumo real de materiales y horas de máquina» y no define los campos. Se eligió un total de horas de máquina por orden y una línea por material con la misma forma que usa Obras (`material`, `cantidad`, `unidad`), para poder compararlo con lo planificado.
- **Persistencia** (migración V5): tabla `cierres_orden` (una por orden, restricción única) y `cierre_consumos` (una fila por material).
- **Fuera de alcance:** cerrar no libera la reserva en Recursos ni completa la fase en Obras. La fase se completa sola cuando el avance llega al 100 %.

