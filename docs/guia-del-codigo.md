# Guía del código de GOVP

Qué resuelve el sistema, qué hace cada endpoint y cómo funciona cada archivo y cada método.

Esta guía describe la versión con el **recorte de alcance** (rama `recorte/alcance-reducido`, 32 endpoints, sin el rol OPERARIO; ver `docs/DECISIONES.md`, «Recorte de alcance»). Para preparar la defensa conviene leerla junto con `docs/guia-de-estudio.md` (conceptos y preguntas) y `docs/arquitectura.md` (diagramas).

Convenciones de esta guía:
- Las rutas de archivo son relativas a la raíz del repositorio.
- «Pasa a X» quiere decir que el método cambia el estado guardado en la base.
- Cuando un método lanza una excepción de `obras-commons`, el código HTTP lo decide `GlobalExceptionHandler`: `RecursoNoEncontradoException` → 404, `ConflictoEstadoException` → 409, `ReglaNegocioException` → 422 con `codigoRegla`, errores de validación → 400.

---

## 1. Qué problema resuelve

Una empresa vial pavimenta rutas por **obras**. Cada obra se divide en **tramos** (un pedazo de ruta con coordenadas y superficie) y cada tramo se construye en **fases** que van en orden: relevamiento, sub-base, base, carpeta asfáltica o losa de hormigón. Cada día de trabajo de una fase es una **orden de trabajo**.

Antes de mandar una cuadrilla a la ruta hay que contestar tres preguntas:

1. **¿Se puede trabajar en esa fase?** La fase anterior tiene que estar terminada (no se puede asfaltar sin la base).
2. **¿Hay con qué?** Hace falta una cuadrilla libre ese día, con la gente y las máquinas que pide ese tipo de trabajo, que llegue al tramo a tiempo y, si el trabajo lleva mezcla asfáltica, una planta lo bastante cerca para que la mezcla no se enfríe en el camino.
3. **¿Lo permite el clima?** Con el suelo húmedo, lluvia probable o frío, el material no fragua o no compacta bien.

Si se decide mal, se pierde un día de cuadrilla, de máquinas y de mezcla. El sistema **GOVP** automatiza esa decisión: cada mañana (o cuando el planificador lo pide) evalúa las órdenes del día y cada una termina en uno de estos resultados:
- **HABILITADA**: tiene recursos reservados y confirmados, y el clima de la jornada cumple los umbrales.
- **Suspendida por clima**: se liberan los recursos, se corre la fase y las que dependen de ella, y se crea la orden del día siguiente.
- **Rechazada por recursos**: queda PROGRAMADA con el motivo (por ejemplo, la cuadrilla está demasiado lejos).
- **Pendiente de confirmación**: no hay dato climático confiable y decide el capataz.

Además lleva el registro de la ejecución: el capataz inicia la orden, informa el avance y la orden se completa al llegar al 100 %. También avisa cuando una fase ya se suspendió tres veces (fase **en riesgo**) para que el planificador decida.

## 2. Cómo está armado

```
Cliente (Postman, app) ──HTTP + JWT──▶ api-gateway (8081) ──▶ ms-obras (8082)
                                               │          ──▶ ms-recursos (8083) ──▶ OSRM (rutas)
                                               │          ──▶ ms-operaciones (8084) ──▶ Open-Meteo (clima)
Keycloak (8080): emite los tokens            PostgreSQL (5432): una base por servicio
```

- **Keycloak** es el servidor de identidad: el usuario se loguea ahí y recibe un **JWT** (un token firmado que dice quién es y qué roles tiene). Los roles son ADMIN, PLANIFICADOR, CAPATAZ y AUDITOR, más la cuenta de servicio SISTEMA (la usan los servicios entre sí y el job diario).
- **api-gateway** es la única puerta de entrada: valida el token, aplica una primera regla de roles por ruta y método, pone un `X-Correlation-Id` nuevo y reenvía el pedido al servicio que corresponde.
- **ms-obras** es dueño de obras, tramos, fases y materiales (RN-01, RN-02 y RN-03).
- **ms-recursos** es dueño de cuadrillas, operarios, máquinas, plantas y reservas; decide si hay recursos (RN-05 a RN-08) y consulta OSRM para los tiempos de viaje.
- **ms-operaciones** es dueño de las órdenes de trabajo. Orquesta el proceso diario: pregunta a Obras, pide recursos a Recursos, consulta el clima en Open-Meteo y decide (RN-09 a RN-12).
- **obras-commons** es una librería (un JAR, no un servicio) que usan los tres servicios: seguridad, formato de errores, correlation-id y el cliente HTTP que se autentica como SISTEMA.
- **PostgreSQL** tiene una base por servicio (`ms_obras`, `ms_recursos`, `ms_operaciones`), cada una con su propio usuario: ningún servicio lee las tablas de otro, todo pasa por la API.

**Defensa en profundidad:** el gateway valida el token y cada servicio lo vuelve a validar y controla el rol con `@PreAuthorize`. En Docker solo el gateway y Keycloak se publican hacia afuera; los servicios quedan en la red interna (la «trusted zone»).

### Recorrido de un pedido

Ejemplo: el capataz registra un avance (`POST /api/operaciones/ordenes/5/avances`).

1. El gateway (`SecurityConfig`) valida el JWT contra las claves de Keycloak y comprueba que un POST a `/api/operaciones/**` lo pueda hacer un CAPATAZ. `CorrelationIdFilter` del gateway genera un id y lo agrega al pedido.
2. El gateway reenvía el pedido a `ms-operaciones:8084` (rutas del `application.yml`).
3. En ms-operaciones, `CorrelationIdFilter` de commons toma el id y lo pone en el log. `BaseResourceServerConfig` vuelve a validar el token y `@PreAuthorize("hasRole('CAPATAZ')")` del controlador controla el rol.
4. `EjecucionController.registrarAvance` valida el cuerpo (`@Valid`) y llama a `EjecucionService.registrarAvance`.
5. El servicio llama a ms-obras con `ObrasHttpClient`, usando el `sistemaRestClient` de commons, que agrega el token de SISTEMA y el mismo correlation-id.
6. Obras actualiza la fase y responde. Operaciones guarda la orden y devuelve el resultado.
7. Si algo falla, la excepción llega a `GlobalExceptionHandler`, que arma la respuesta de error en formato estándar (RFC 7807) con el `correlationId`.

## 3. El proceso central, paso a paso

Lo dispara `POST /api/operaciones/habilitaciones-diarias` (PLANIFICADOR o SISTEMA), `POST /api/operaciones/ordenes/{id}/habilitacion` para una sola orden, o el job diario (`JobHabilitacionDiaria`, todos los días a las 6). Ambos llaman a `HabilitacionDiariaService.habilitarDelDia(fecha)`, que toma las órdenes de esa fecha en estado PROGRAMADA o RECURSOS_ASIGNADOS y llama a `OrdenTrabajoServiceImpl.habilitarOrden` con cada una:

1. **Obras (RN-01):** pide el contexto de la fase (`GET /api/obras/internos/fases/{id}/contexto-ejecucion`): tipo de trabajo, coordenadas del tramo, materiales y si la fase anterior está completa. Si no lo está, la orden no avanza (409).
2. **Umbrales (RN-09):** busca los umbrales del tipo de trabajo. Si no hay, 422.
3. **Recursos (RN-05 a RN-08):** pide «proponer y reservar» (`POST /api/recursos/internos/reservas`). Recursos devuelve una reserva PROVISORIA, que vence en 30 minutos, con la hora de salida del camión, o un 422 con la regla que falló. La orden pasa a RECURSOS_ASIGNADOS.
4. **Clima (RN-09, RN-10):** la orden pasa a EVALUACION_CLIMATICA. `OpenMeteoService` pide los datos horarios de la fecha de la orden y toma el peor valor de 08:00 a 13:00. Se guarda una `EvaluacionClimatica`.
5. **Decisión:**
   - Cumple los umbrales: confirma la reserva en Recursos y la orden queda HABILITADA.
   - No cumple: `SuspensionService.suspender` corre la saga (paso 6).
   - Sin dato confiable: queda PENDIENTE_CONFIRMACION_CLIMATICA y conserva la reserva; decide el capataz.
6. **Saga de suspensión (RN-02, RN-12):** libera la reserva en Recursos y registra la suspensión. Después:
   - Si la fase lleva menos de 3 suspensiones vigentes, pide a Obras correr la fase, que corre también las que dependen de ella, y crea la orden nueva. La vieja queda REPROGRAMADA.
   - En la tercera, marca la fase EN_RIESGO en Obras y la orden queda SUSPENDIDA, sin orden nueva. El planificador decide reprogramando la fase y creando la orden; al crearla, las suspendidas pasan a REPROGRAMADA y el conteo vuelve a cero.
7. **Ejecución:** el capataz inicia la orden (EN_EJECUCION) y registra avances. Al 100 % Obras deja la fase COMPLETADA y la orden queda COMPLETADA. También puede rechazar una orden habilitada, lo que dispara la misma saga.

Estados de la orden (`EstadoOrden`): PROGRAMADA → RECURSOS_ASIGNADOS → EVALUACION_CLIMATICA → HABILITADA → EN_EJECUCION → COMPLETADA, con desvíos a PENDIENTE_CONFIRMACION_CLIMATICA y SUSPENDIDA → REPROGRAMADA o CANCELADA.

## 4. Los 32 endpoints

Todos exigen un JWT salvo que se diga lo contrario. Sin token: 401. Con un rol que no corresponde: 403. Las rutas `/internos/` son para la cuenta SISTEMA: los servicios se las llaman entre sí, sin pasar por el gateway.

### 4.1 ms-obras (12)

| Método y ruta | Roles | Qué hace | Respuestas |
|---|---|---|---|
| `POST /api/obras` | PLANIFICADOR | Da de alta una obra (nombre y descripción). | 201; 400 si falta el nombre |
| `GET /api/obras` | PLANIFICADOR, CAPATAZ, AUDITOR | Lista las obras con un estado calculado (PLANIFICADA, EN_EJECUCION o COMPLETADA) y el avance promedio de sus fases. | 200 |
| `GET /api/obras/{obraId}` | PLANIFICADOR, CAPATAZ, AUDITOR | Obra completa: sus tramos, cada uno con sus fases ordenadas por fecha. | 200; 404 |
| `POST /api/obras/{obraId}/tramos` | PLANIFICADOR | Agrega un tramo (nombre, superficie en m², latitud y longitud). | 201; 400; 404 |
| `GET /api/obras/{obraId}/tramos` | PLANIFICADOR, CAPATAZ, AUDITOR | Tramos de una obra. Extra no comprometido: lo usa Operaciones para filtrar órdenes por obra. | 200; 404 |
| `GET /api/obras/{obraId}/cronograma` | PLANIFICADOR, CAPATAZ, AUDITOR | Todas las fases de la obra ordenadas por fecha de inicio, con estado, fechas, fase anterior y avance. | 200; 404 |
| `POST /api/obras/tramos/{tramoId}/fases` | PLANIFICADOR | Crea una fase con su tipo, fechas y fase anterior opcional. Valida fechas, mismo tramo, una sola siguiente por fase y que no empiece antes del fin de la anterior (RN-02). | 201; 404; 409; 422 |
| `GET /api/obras/fases/{id}/materiales` | PLANIFICADOR, CAPATAZ | Materiales que necesita la fase según la superficie del tramo (RN-03). | 200; 404; 422 |
| `PATCH /api/obras/fases/{id}/programacion` | PLANIFICADOR, SISTEMA | Corre la fecha de inicio de la fase y en cascada las que dependen de ella (RN-02). Una fase EN_RIESGO vuelve a PENDIENTE. | 200; 404; 409 si está COMPLETADA; 422 |
| `GET /api/obras/internos/fases/{id}/contexto-ejecucion` | SISTEMA | Todo lo que Operaciones necesita de una fase: tipo de trabajo, tramo, coordenadas, materiales, si la anterior está completa y cuándo termina. | 200; 404 |
| `POST /api/obras/internos/fases/{id}/avances` | SISTEMA | Registra el avance acumulado. El primero inicia la fase (controla RN-01) y al 100 % la completa. | 201; 404; 409; 422 si retrocede |
| `PATCH /api/obras/internos/fases/{id}/riesgo` | SISTEMA | Marca la fase EN_RIESGO (RN-12). | 200; 404; 409 si está COMPLETADA |

### 4.2 ms-recursos (7)

| Método y ruta | Roles | Qué hace | Respuestas |
|---|---|---|---|
| `GET /api/recursos/maquinas` | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | Lista las máquinas cargadas, con tipo, estado, horas de uso y cuadrilla. | 200 |
| `GET /api/recursos/operarios` | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | Lista los operarios con su cuadrilla, rol operativo y estado. | 200 |
| `GET /api/recursos/cuadrillas` | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | Lista las cuadrillas con su última ubicación, la fecha del check-in y los ids de sus operarios y máquinas. | 200 |
| `POST /api/recursos/cuadrillas/{id}/ubicaciones` | CAPATAZ (solo el de esa cuadrilla) | Check-in: actualiza la ubicación de la cuadrilla, que es la que RN-07 usa para calcular el viaje al tramo. | 200; 400; 403; 404 |
| `POST /api/recursos/internos/reservas` | SISTEMA | «Proponer y reservar»: busca una cuadrilla elegible (RN-05, RN-06, RN-07) y una planta si hay mezcla (RN-08), y crea una reserva PROVISORIA con TTL de 30 min. Es idempotente por orden. | 201; 400; 422 con `codigoRegla` |
| `PATCH /api/recursos/internos/reservas/{id}/confirmacion` | SISTEMA | Confirma una reserva PROVISORIA. Si el TTL venció, la pasa a EXPIRADA. Repetirla no cambia nada. | 200; 404; 409 |
| `PATCH /api/recursos/internos/reservas/{id}/liberacion` | SISTEMA | Libera una reserva PROVISORIA o CONFIRMADA. Repetirla no cambia nada. | 200; 404 |

### 4.3 ms-operaciones (13)

| Método y ruta | Roles | Qué hace | Respuestas |
|---|---|---|---|
| `POST /api/operaciones/ordenes` | PLANIFICADOR | Crea una orden PROGRAMADA para una fase y fecha. Comprueba en Obras que la fase sea de ese tramo. Si la fase venía en riesgo, es la decisión del planificador: cierra las suspensiones de la fase (RN-12). | 201; 400; 404; 422 |
| `GET /api/operaciones/ordenes` | PLANIFICADOR, CAPATAZ, AUDITOR | Lista órdenes; filtros opcionales y combinables `fecha`, `estado` y `obraId` (este se resuelve preguntando a Obras). | 200; 404 si la obra no existe |
| `GET /api/operaciones/ordenes/{id}` | PLANIFICADOR, CAPATAZ, AUDITOR | Una orden con su estado, reserva, hora de salida del camión y contador de suspensiones. | 200; 404 |
| `POST /api/operaciones/ordenes/{id}/habilitacion` | PLANIFICADOR, SISTEMA | Habilita una orden puntual con el mismo proceso que la habilitación del día (Obras, Recursos y clima). Sirve para la demo y para reintentar una orden. | 200 con la orden; 404; 409 (RN-01); 422 (RN-11, RN-09 o rechazo de Recursos); 503 |
| `GET /api/operaciones/ordenes/{id}/evaluaciones-climaticas` | PLANIFICADOR, CAPATAZ, AUDITOR | Historial de evaluaciones climáticas de la orden (valores medidos y si cumplió). | 200; 404 |
| `PATCH /api/operaciones/ordenes/{id}/resolucion-climatica` | CAPATAZ | Decide una orden PENDIENTE_CONFIRMACION_CLIMATICA: HABILITAR (confirma la reserva) o SUSPENDER (corre la saga) (RN-10). | 200; 404; 409; 422 |
| `PATCH /api/operaciones/ordenes/{id}/inicio` | CAPATAZ | Pasa una orden HABILITADA a EN_EJECUCION. | 200; 404; 422 (RN-11) |
| `PATCH /api/operaciones/ordenes/{id}/rechazo` | CAPATAZ | Rechaza una orden HABILITADA con motivo; corre la saga de suspensión. | 200; 400; 404; 422 |
| `POST /api/operaciones/ordenes/{id}/avances` | CAPATAZ | Registra el avance de una orden EN_EJECUCION en Obras; al 100 % la orden queda COMPLETADA. | 201; 400; 404; 409; 422 |
| `POST /api/operaciones/habilitaciones-diarias` | PLANIFICADOR, SISTEMA | Habilita todas las órdenes de una fecha (el proceso central). Una orden que falla no frena a las demás; se puede repetir. | 200 con el resultado de cada orden |
| `GET /api/operaciones/suspensiones` | PLANIFICADOR, AUDITOR | Historial de suspensiones; filtros opcionales `faseId` u `ordenId`. | 200 |
| `GET /api/operaciones/umbrales-climaticos` | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | Umbrales vigentes por tipo de trabajo. | 200 |
| `PUT /api/operaciones/umbrales-climaticos/{tipoTrabajo}` | ADMIN | Crea o cambia los umbrales de un tipo de trabajo (humedad del suelo máxima, probabilidad de lluvia máxima y temperatura mínima). | 200; 400 |

Sin token se abren solo `/actuator/**` y Swagger (`/v3/api-docs/**`, `/swagger-ui/**`) en cada servicio, y `/actuator/health/**` en el gateway.

---

## 5. Archivo por archivo

En cada servicio el código se reparte en capas:
- **controller**: recibe el pedido HTTP, valida el cuerpo y controla el rol. No tiene lógica de negocio.
- **service**: las reglas de negocio.
- **repository**: interfaces de Spring Data JPA. Spring arma la consulta SQL a partir del nombre del método; por ejemplo, `findByTramoId` busca por la columna del tramo.
- **entity/domain/modelo**: clases mapeadas a tablas.
- **dto**: lo que entra y sale por la API. Cada servicio define los suyos; no se comparten.

Las entidades y los DTO casi no tienen métodos propios (getters y setters, o los que genera Lombok con `@Getter`/`@Setter`). Por eso se describen por sus campos y solo se detallan los métodos con lógica.

### 5.1 obras-commons (librería compartida)

Es un JAR que dependen los tres servicios (no el gateway). Como su paquete (`com.obrasviales.commons`) no está dentro del de cada servicio, Spring no lo encuentra solo. Lo registra el archivo `obras-commons/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, que lista las 7 clases que Spring tiene que cargar al arrancar cualquier servicio: `BaseResourceServerConfig`, `CorrelationIdFilter`, `GlobalExceptionHandler`, `SistemaRestClientConfig`, `SistemaRequestInterceptor`, `SistemaTokenService` y `SistemaClientProperties`.

**`security/Roles.java`**: constantes con los nombres de los roles (ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR, SISTEMA). Se usan en los `@PreAuthorize` para no escribir el texto a mano. El constructor es privado para que nadie la instancie.

**`security/KeycloakConversorRoles.java`**: traduce los roles del token de Keycloak al formato de Spring Security.
- `convert(Jwt jwt)`: lee el claim `realm_access.roles` del token; si no existe devuelve una lista vacía, y si existe le antepone `ROLE_` a cada rol (`PLANIFICADOR` → `ROLE_PLANIFICADOR`). Spring necesita ese prefijo para que `hasRole('PLANIFICADOR')` funcione.

**`security/BaseResourceServerConfig.java`**: la configuración de seguridad que heredan los tres servicios. `@EnableMethodSecurity` activa los `@PreAuthorize` de los controladores.
- `jwtAuthenticationConverter()`: arma el convertidor de JWT de Spring y le pone `KeycloakConversorRoles` para leer los roles.
- `baseSecurityFilterChain(HttpSecurity)`: define la cadena de filtros:
  - desactiva CSRF, porque no hay cookies;
  - sin sesiones (cada pedido trae su token);
  - deja libres `/actuator/**` y Swagger, y exige token para todo lo demás;
  - configura el servicio como *resource server*: valida la firma del JWT con las claves de Keycloak;
  - conecta los manejadores de 401 y 403 con formato estándar.

**`security/ProblemJson.java`**: escribe a mano una respuesta de error JSON en formato RFC 7807. Hace falta porque los 401 y 403 del filtro de seguridad ocurren antes de llegar a Spring MVC, donde el `GlobalExceptionHandler` no actúa.
- `escribir(request, response, status, type, title, detail)`: pone el status y el content-type `application/problem+json`, y arma el JSON con `type`, `title`, `status`, `detail`, `instance` (la ruta), `timestamp` y `correlationId` (si hay uno en el MDC).
- `escapar(texto)`: escapa comillas y barras para que el texto sea un string JSON válido.

**`security/ProblemDetailAuthenticationEntryPoint.java`**: el 401.
- `commence(...)`: primero llama al `BearerTokenAuthenticationEntryPoint` de Spring, que pone el header `WWW-Authenticate` que pide el estándar de tokens Bearer. Después escribe el cuerpo con `ProblemJson` («El token de acceso falta, venció o no es válido»).

**`security/ProblemDetailAccessDeniedHandler.java`**: el 403 del filtro.
- `handle(...)`: escribe «No posee los permisos suficientes» con `ProblemJson`. Los 403 de `@PreAuthorize` no pasan por acá: los atiende `GlobalExceptionHandler.handleAccessDenied` con el mismo texto.

**`filter/CorrelationIdFilter.java`**: da a cada pedido un identificador para seguirlo en los logs de todos los servicios. Corre primero que ningún otro filtro (`@Order(HIGHEST_PRECEDENCE)`).
- `doFilterInternal(...)`: lee el header `X-Correlation-Id`; si no viene, genera un UUID. Lo guarda en el MDC (un mapa por hilo que usa el log: el patrón `[%X{correlationId:-}]` de cada `application.yml` lo imprime en cada línea) y lo devuelve en la respuesta. Al terminar el pedido lo borra del MDC, porque los hilos se reutilizan.

**`exception/RecursoNoEncontradoException`, `ConflictoEstadoException`, `ReglaNegocioException`**: excepciones de dominio genéricas. Los servicios las lanzan y el handler les asigna 404, 409 y 422. `ReglaNegocioException` además guarda `codigoRegla` (por ejemplo `"RN-05"`) y lo expone con `getCodigoRegla()`.

**`exception/GlobalExceptionHandler.java`**: transforma cualquier excepción en una respuesta de error con el mismo formato (RFC 7807). Extiende `ResponseEntityExceptionHandler`, la clase de Spring que ya sabe tratar los errores propios de Spring MVC (ruta inexistente, método no permitido, JSON ilegible).
- `crearProblemDetail(status, titulo, detalle, tipo)`: arma el objeto de error y le agrega `timestamp` y `correlationId`.
- `handleNotFound`, `handleReglaNegocio` (además agrega `codigoRegla`), `handleConflicto` y `handleAccessDenied`: devuelven 404, 422, 409 y 403.
- `handleMethodArgumentNotValid(...)`: errores de Bean Validation (`@NotNull`, `@Positive`, etc.). Devuelve 400 con un mapa `errores` campo → mensaje.
- `handleHttpMessageNotReadable(...)`: JSON mal formado o un valor que no entra en el tipo, como un enum inexistente. Devuelve 400.
- `handleExceptionInternal(...)` y `completarFormato(...)`: todas las excepciones de Spring MVC pasan por acá. Si el error no tiene tipo, se le pone uno según el status (404 → `not-found`, 405 → `method-not-allowed`), con su `timestamp` y su `correlationId`.
- `handleGenerico(Exception)`: cualquier otra excepción se registra en el log y devuelve 500, sin mostrar detalles internos al cliente.

**`dto/Coordenada.java`**: *value object* (un objeto definido solo por sus valores) con latitud y longitud.
- El constructor compacto valida que la latitud esté entre -90 y 90 y la longitud entre -180 y 180; si no, lanza `IllegalArgumentException`.
- `toOsrmString()`: devuelve `"longitud,latitud"`, el orden que espera OSRM, al revés que casi todas las APIs. La usan `OsrmProveedorRutas` (Recursos) y `OpenMeteoService` (Operaciones).

**`client/SistemaClientProperties.java`**: lee de la configuración `obrasviales.sistema-client.*` la URL de tokens de Keycloak, el `client-id` y el `client-secret` de la cuenta SISTEMA. Solo getters y setters.

**`client/SistemaTokenService.java`**: obtiene y guarda en memoria el token de la cuenta SISTEMA (flujo *client credentials*: el servicio se autentica con su id y su secreto, sin usuario).
- `obtenerToken()` (sincronizado, para que dos hilos no pidan dos tokens a la vez): si no hay token o faltan menos de 30 segundos para que venza, pide uno nuevo; si no, devuelve el guardado.
- `solicitarConReintentos()`: hasta 3 intentos con espera creciente (500 ms, 1 s), porque Keycloak puede tardar en estar listo al arrancar el stack. Un 4xx (credenciales mal configuradas) no se reintenta.
- `solicitarNuevoToken()`: hace el POST de formulario a Keycloak y guarda el `access_token` y su vencimiento (`expires_in`).
- `clienteConTimeouts()`: cliente HTTP con 3 s para conectar y 5 s para leer, para que un Keycloak colgado no cuelgue al servicio.
- `esperar(pausa)` y la interfaz `Espera`: la pausa entre reintentos se inyecta, así los tests no esperan de verdad.

**`client/SistemaRequestInterceptor.java`**: se ejecuta antes de cada llamada que hace el `sistemaRestClient`.
- `intercept(...)`: agrega el header `X-Correlation-Id` del pedido actual (así el id se propaga de servicio en servicio) y el `Authorization: Bearer <token de SISTEMA>`.

**`client/SistemaRestClientConfig.java`**:
- `sistemaRestClient(interceptor)`: crea el bean `RestClient` calificado `sistemaRestClient`, con timeouts (3 s y 10 s) y el interceptor anterior. Lo usan `ObrasHttpClient` y `RecursosHttpClient` de Operaciones. No es un `RestClient.Builder` global a propósito: así el token de SISTEMA nunca viaja a terceros como Open-Meteo.

### 5.2 api-gateway

No depende de commons, así que repite algunas piezas pequeñas (formato de error, conversor de roles) para no acoplarse.

**`ApiGatewayApplication.java`**: `main` que arranca Spring Boot.

**`application.yml`**: puerto 8081, las tres rutas (`/api/obras/**` → 8082, `/api/recursos/**` → 8083, `/api/operaciones/**` → 8084, bajo `spring.cloud.gateway.server.webmvc.routes`), el `issuer-uri` de Keycloak y el patrón de log con el correlation-id. No recorta el prefijo: el servicio recibe la ruta completa (`/api/obras/...`), y por eso los controladores empiezan con `/api/<servicio>`.

**`application-docker.yml`**: perfil que se activa en Docker (`SPRING_PROFILES_ACTIVE=docker`). Repite las tres rutas con los nombres de contenedor (`http://ms-obras:8082`) porque las listas de YAML se reemplazan enteras y no se fusionan. También baja las claves de Keycloak por la red interna (`jwk-set-uri`).

**`security/SecurityConfig.java`**: la seguridad del borde.
- `securityFilterChain(HttpSecurity)`: sin CSRF ni sesiones; `/actuator/health/**` libre; `/api/*/internos/**` solo SISTEMA. Después, reglas gruesas por servicio y método, que son la unión de los roles que algún endpoint acepta. Por ejemplo, un PATCH a `/api/operaciones/**` solo lo hace el CAPATAZ y un PUT solo el ADMIN. Cualquier otra combinación bajo `/api/**` queda en `denyAll`. Los 401 y 403 salen en formato estándar. Con esto, un rol que no tiene nada que hacer en una ruta recibe 403 aunque mande un cuerpo inválido (sin el gateway, el servicio validaría primero el cuerpo y respondería 400).
- `conversorDeRoles()` y `rolesDeKeycloak(Jwt)`: lo mismo que `KeycloakConversorRoles` de commons, sobre el claim `realm_access.roles`.

**`filter/CorrelationIdFilter.java`**: a diferencia del de commons, en el borde **siempre genera un id nuevo** e ignora el que mande el cliente, para que nadie pueda inyectar un id falso en los logs.
- `doFilterInternal(...)`: genera el UUID, lo pone en el MDC y en la respuesta, y sigue la cadena con dos envoltorios.
- Clase interna `ConCorrelationId` (envuelve el request): sobrescribe `getHeader`, `getHeaders` y `getHeaderNames` para que el servicio de destino reciba el id del gateway y no el del cliente. El request original es de solo lectura, por eso hace falta envolverlo.
- Clase interna `SinHeaderDuplicado` (envuelve la respuesta): el servicio también devuelve `X-Correlation-Id` y el proxy lo copiaría; `addHeader` lo reemplaza en lugar de agregarlo, para que la respuesta tenga uno solo.

**`security/ProblemJson`, `ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler`**: iguales a los de commons (ver 5.1), para que el cliente vea el mismo formato de 401 y 403 venga del gateway o del servicio.

### 5.3 ms-obras

**`MsObrasApplication.java`**: `main` de Spring Boot.

**`config/OpenApiConfig.java`**: solo anotaciones para Swagger: título y descripción del servicio y un esquema de seguridad `bearerAuth`, que agrega el botón *Authorize* en la página de Swagger.

**Dominio (`domain/`)**
- `Obra`: `id`, `nombre`, `descripcion`. El estado y el avance no se guardan: se calculan a partir de las fases (ver `ObraService`).
- `Tramo`: `id`, `obra` (muchos a uno), `nombre`, `superficieM2`, `latitud` y `longitud`, como dos `Double`. No se usa `Coordenada` como `@Embedded` para no meter JPA en commons.
- `Fase`: `id`, `tramo`, `tipo` (`TipoFase`), `estado` (`EstadoFase`, arranca PENDIENTE), fechas de inicio y fin planificadas, `faseAnterior` (relación consigo misma: arma la cadena de dependencias) y `avancePorcentaje` (0 a 100).
- `TipoFase`: RELEVAMIENTO, BASE, SUBBASE, CARPETA_ASFALTICA y LOSA_HORMIGON. Es el vocabulario de tipos de trabajo acordado para los tres servicios.
- `EstadoFase`: PENDIENTE, EN_CURSO, COMPLETADA y EN_RIESGO.
- `ConsumoMaterial`: tabla de configuración para RN-03; dice cuánto material (`consumoPorM2`, con su `unidad`) lleva cada m² de un tipo de fase.

**Repositorios (`repository/`)**
- `ObraRepository`: solo lo que trae `JpaRepository` (`findById`, `findAll`, `save`, `existsById`).
- `TramoRepository.findByObraIdOrderByIdAsc(obraId)`: los tramos de una obra en orden.
- `FaseRepository`:
  - `findByFaseAnteriorId`: las fases que dependen de una; lo usan la cascada y el control de «una sola siguiente».
  - `findByTramoObraId`: todas las fases de una obra, recorriendo fase → tramo → obra.
  - `findByTramoObraIdOrderByFechaInicioPlanificadaAsc`: lo mismo, ordenado por fecha (el cronograma).
- `ConsumoMaterialRepository.findByTipoFase(tipo)`: los consumos de un tipo de fase.

**DTO (`dto/`)**
- De entrada, con Bean Validation:
  - `CrearObraRequest`: nombre obligatorio, de hasta 200 caracteres.
  - `CrearTramoRequest`: superficie positiva y coordenadas en rango.
  - `CrearFaseRequest`: tipo y fechas obligatorios; `faseAnteriorId` opcional.
  - `RegistrarAvanceRequest`: porcentaje de 1 a 100.
  - `ReprogramarFaseRequest`: nueva fecha obligatoria.
- De salida:
  - `ObraResponse`, `TramoResponse` y `FaseResponse`: cada uno con un método estático `desde(entidad)` que copia los campos y reemplaza las relaciones por sus ids.
  - `ObraResumenResponse`: la obra con su estado y avance calculados.
  - `ObraDetalleResponse`: la obra con la lista `TramoConFases`.
  - `MaterialCalculado`: material, cantidad y unidad.
  - `DatosOperacionResponse`: el contexto que consume Operaciones, incluido `fechaFinFaseAnterior`.

**Controladores (`controller/`)**: cada método controla el rol con `@PreAuthorize` y delega en el servicio. Las rutas y roles están en la tabla de la sección 4.1.
- `ObraController`: `crear`, `listar`, `obtener`, `crearTramo`, `tramos` y `cronograma` llaman a los métodos de `ObraService` del mismo nombre.
- `TramoController.crearFase`: llama a `FaseService.crearFase`.
- `FaseController`: `materiales` llama a `FaseService.materialesDeFase` y `reprogramar` a `FaseService.reprogramarFase`.
- `FaseInternaController`: solo SISTEMA, a nivel de clase. `registrarAvance` llama a `FaseService.registrarAvance` y `marcarEnRiesgo` a `FaseService.marcarEnRiesgo`.
- `OperacionController.contextoEjecucion`: llama a `OperacionService.obtenerDatosOperacion`.

**`service/ObraService.java`**
- `crearObra(nombre, descripcion)`: crea y guarda la obra.
- `listarObras()`: para cada obra trae sus fases y arma el resumen con `estadoDe` y `avanceDe`.
- `obtenerObra(obraId)`: trae la obra (404 si no existe), sus fases ordenadas por fecha y sus tramos, y agrupa las fases bajo su tramo.
- `crearTramo(...)`: comprueba que exista la obra y guarda el tramo.
- `listarTramos(obraId)` y `cronogramaDeObra(obraId)`: 404 si la obra no existe; si existe, devuelven los tramos o las fases ordenadas por fecha.
- `avanceDe(fases)`: promedio del avance de las fases, redondeado (0 si no tiene fases).
- `estadoDe(fases)`:
  - PLANIFICADA si no tiene fases o ninguna empezó;
  - COMPLETADA si todas lo están;
  - EN_EJECUCION si alguna está en curso o completada, o tiene avance.
- `buscarObra(id)`: busca la obra o lanza 404.

**`service/FaseService.java`**: la mayor parte de la lógica de Obras.
- `crearFase(tramoId, tipo, inicio, fin, faseAnteriorId)`: valida en orden:
  1. que exista el tramo (404);
  2. que el fin no sea anterior al inicio (422 `FASE-FECHAS`).
  3. Si tiene fase anterior:
     - que exista (404);
     - que sea del mismo tramo (422 `FASE-TRAMO`);
     - que todavía no tenga otra fase siguiente (409: la cadena es lineal);
     - y que la nueva no empiece antes de que termine la anterior (422 `RN-02`).
- `materialesDeFase(faseId)` (RN-03): busca la fase y pide a `MaterialService` los materiales según la superficie de su tramo.
- `registrarAvance(faseId, porcentaje)`:
  1. Rechaza una fase COMPLETADA o EN_RIESGO (409) y un avance que retrocede (422 `AVANCE`).
  2. Si la fase estaba PENDIENTE, este es el primer avance: aplica RN-01 con `exigirAnteriorCompletada` y la pasa a EN_CURSO.
  3. Guarda el porcentaje y, si llega a 100, la pasa a COMPLETADA.
- `marcarEnRiesgo(faseId)` (RN-12): 409 si está COMPLETADA; si ya estaba EN_RIESGO la devuelve igual; si no, la pasa a EN_RIESGO.
- `reprogramarFase(faseId, nuevaFechaInicio)` (RN-02):
  1. Rechaza una fase COMPLETADA (409) y una fecha anterior al fin de la fase anterior (422).
  2. Calcula cuántos días se corre y llama a `desplazarEnCascada`.
  3. Si la fase estaba EN_RIESGO, la vuelve a PENDIENTE: reprogramarla es la forma de resolver el riesgo.
- `desplazarEnCascada(fase, dias, visitadas)`: suma los días a las dos fechas de la fase y se llama a sí mismo para cada fase que dependa de ella (recursión). El conjunto `visitadas` detecta un ciclo en las dependencias y lo rechaza con 422.
- `buscar(id)`: busca la fase o lanza 404.
- `exigirAnteriorCompletada(fase)` (RN-01): si tiene fase anterior y no está COMPLETADA, lanza 409.

**`service/MaterialService.java`**
- `calcularMateriales(tipoFase, superficieM2)` (RN-03): 422 si falta el tipo o la superficie no es mayor que 0. Para cada consumo configurado de ese tipo, multiplica superficie × consumo por m², redondeado a 2 decimales. Ejemplo: base de 5000 m² × 0,25 = 1250 m³ de piedra triturada.

**`service/OperacionService.java`**
- `obtenerDatosOperacion(faseId)`: busca la fase (404) y arma el `DatosOperacionResponse`:
  - tipo de trabajo, estado, tramo, coordenadas y superficie;
  - los materiales calculados;
  - `faseAnteriorCompletada`: verdadero si no tiene anterior o si la anterior está COMPLETADA;
  - el fin planificado de la anterior, que la saga de Operaciones usa para no reprogramar antes de esa fecha.

**Configuración y migraciones**
- `application.yml`: puerto 8082, base `ms_obras` con el usuario `usr_obras` y la contraseña en `OBRAS_DB_PASSWORD`, `ddl-auto: validate` (Hibernate solo comprueba que las entidades coincidan con las tablas; no las crea) y el `issuer-uri` de Keycloak. `application-docker.yml` cambia la URL de la base y de las claves a los nombres de contenedor.
- `V1__init.sql`: tablas `obras`, `tramos` (con `CHECK` de rango de coordenadas) y `fases`.
- `V2__datos_demo.sql`: la obra «Ruta Provincial 34», un tramo y dos fases (RELEVAMIENTO completada y BASE pendiente).
- `V3__ajuste_modelo_obras.sql`:
  - alinea el esquema con las entidades: nombre del tramo, columnas renombradas y fechas planificadas;
  - agrega el `CHECK` fin ≥ inicio;
  - crea `consumos_material` con los consumos por tipo.
- `V4__avance_de_fases.sql`: columna `avance_porcentaje` (0 a 100).
- `V5__escenario_demo_habilitacion.sql`:
  - mueve las fechas de las fases del tramo 1 alrededor de hoy, en hora de Argentina;
  - agrega el tramo 2, en Río Cuarto, y su fase BASE, para que la tercera orden sembrada se rechace por distancia (RN-07).

### 5.4 ms-recursos

**`MsRecursosApplication.java`**: `main` de Spring Boot. `@EnableCaching` activa la caché de rutas y `@EnableScheduling` las tareas periódicas.
- `cacheManager()`: crea una caché en memoria llamada `rutas` (un `ConcurrentMapCacheManager`). No tiene vencimiento por entrada; por eso existe `VencimientoCacheRutas`.

**Entidades (`entity/`)**: getters y setters escritos a mano.
- `Cuadrilla`: `nombre`, la última ubicación (`ultimaLatitud`, `ultimaLongitud`) y la `fechaCheckin`. Los operarios y las máquinas apuntan a su cuadrilla (la relación vive en `Operario` y `Maquina`).
- `Operario`:
  - `cuadrilla` y `keycloakUsername`, el usuario de Keycloak; con él se reconoce al capataz en el check-in;
  - `nombre`;
  - `rolOperativo`: el puesto dentro de la cuadrilla, por ejemplo MAQUINISTA, CAPATAZ o PEON. No es un rol de Keycloak;
  - `estado` (ACTIVO).
- `Maquina`: `cuadrilla`, `codigo`, `tipo`, `estado` (DISPONIBLE) y `horasUso`.
- `PlantaMezcla`: `nombre`, coordenadas y si está `activa`.
- `ComposicionMinimaTrabajo`: tiene una fila por tipo de trabajo:
  - `minOperarios`;
  - `tipoMaquinaRequerida` y `cantidadMaquinas`;
  - `requiereMezcla`.
- `ConfiguracionDesplazamiento`: una sola fila (id 1) con el viaje máximo de la cuadrilla (90 min), la duración de la jornada (480), la ventana de transporte de la mezcla (60) y el margen del camión (15).
- `Reserva`:
  - `cuadrilla` y `planta`;
  - `ordenTrabajoId`, que es único: una reserva por orden;
  - `fechaReserva` y `fechaVencimientoTtl`;
  - `estado`: PROVISORIA, CONFIRMADA, LIBERADA o EXPIRADA;
  - las horas de llegada de la cuadrilla y de salida del camión, los tiempos de viaje y la `fuenteRuta` (OSRM o ESTIMADA).

**Repositorios (`repository/`)**
- `OperarioRepository`:
  - `findByKeycloakUsername`: lo usa el check-in;
  - `findByCuadrillaIdAndEstado`: los operarios activos, para RN-06;
  - `findByCuadrillaId`: para armar la respuesta.
- `MaquinaRepository`: `findByCuadrillaIdAndEstado` (las disponibles, para RN-06) y `findByCuadrillaId`.
- `PlantaMezclaRepository.findByActivaTrue()`: las plantas activas.
- `CuadrillaRepository`, `ComposicionMinimaRepository` (la clave es el tipo de trabajo) y `ConfiguracionDesplazamientoRepository`: solo lo básico de `JpaRepository`.
- `ReservaRepository`:
  - `findByOrdenTrabajoId`: para la idempotencia.
  - `existeReservaSolapada(cuadrillaId, fecha, ahora)`: consulta JPQL escrita a mano (RN-05). ¿Tiene la cuadrilla, ese día, una reserva CONFIRMADA o una PROVISORIA que todavía no venció?
  - `expirarReservasVencidas(ahora)`: un `UPDATE` masivo que pasa a EXPIRADA las PROVISORIAS vencidas.

**DTO (`dto/`)**
- `CatalogoDtos`: agrupa los records `MaquinaResponse`, `OperarioResponse` y `CuadrillaResponse` (con los ids de operarios y máquinas), cada uno con `desde(...)`. También `UbicacionRequest`, con coordenadas validadas.
- `ReservaResponse`: el contrato de salida de una reserva, con todo lo que Operaciones necesita (id, estado, cuadrilla, planta, tiempos, `horaSalidaCamion`, `fuenteRuta`).
- `ReservaController.SolicitudReservaRequest`: el cuerpo del POST de reservas, definido dentro del controlador. Valida `ordenTrabajoId`, `tipoTrabajo`, `fecha` y las coordenadas en rango. Las coordenadas son `Double` y no `double`, para que un campo faltante dé 400 en lugar de 0.0 sin aviso.

**Controladores (`controller/`)**
- `Permisos.java`: las expresiones de `@PreAuthorize` en un solo lugar: `SOLO_CAPATAZ` y `LECTURA` (ADMIN, PLANIFICADOR, CAPATAZ y AUDITOR).
- `CatalogoController`: `listarMaquinas` y `listarOperarios` llaman a `CatalogoService`.
- `CuadrillaController`:
  - `listar` llama a `CuadrillaService.listar`.
  - `registrarUbicacion` saca del token el usuario (`preferred_username`) y llama a `CuadrillaService.registrarUbicacion`.
- `ReservaController`: solo SISTEMA, a nivel de clase. No tiene try/catch: los errores los traduce el handler de commons.
  - `proponerYReservar` responde 201.
  - `confirmar` y `liberar` llaman a `confirmarReserva` y a `liberarReserva`.

**`service/CatalogoService.java`**
- `listarMaquinas()` y `listarOperarios()`: leen todo y lo convierten a DTO. Los datos vienen precargados por Flyway.

**`service/CuadrillaService.java`**
- `listar()`: todas las cuadrillas, cada una armada con `armar`.
- `registrarUbicacion(cuadrillaId, req, usuarioKeycloak)`:
  1. Busca la cuadrilla (404).
  2. Busca el operario con ese usuario de Keycloak y comprueba que su rol operativo sea CAPATAZ y que pertenezca a esa cuadrilla. Si no, lanza `AccessDeniedException` (403).
  3. Guarda la nueva ubicación y la fecha del check-in.
- `armar(c)`: arma `CuadrillaResponse` con los operarios y las máquinas de la cuadrilla.

**`service/ReservaService.java`**: el motor de elegibilidad.
- `proponerYReservar(ordenTrabajoId, tipoTrabajo, fecha, lat, lon)`:
  1. **Idempotencia:** si la orden ya tiene una reserva que bloquea recursos (`bloqueaRecursos`), la devuelve sin crear otra. Así Operaciones puede reintentar sin duplicar.
  2. Lee la configuración de desplazamiento y la composición mínima del tipo de trabajo (422 `RN-06` si el tipo no está configurado).
  3. Recorre las cuadrillas y evalúa cada una con `evaluarCuadrilla`; se queda con la primera elegible. Para las rechazadas guarda el motivo y la regla «más avanzada» según `ORDEN_REGLAS` (RN-05, RN-06, RN-07), es decir, la de la cuadrilla que estuvo más cerca de servir.
  4. Si ninguna sirve, lanza 422 con esa regla y el motivo de cada cuadrilla.
  5. **RN-08:** si el trabajo lleva mezcla, busca la primera planta activa cuyo viaje al tramo entre en la ventana de transporte. La hora de llegada de la cuadrilla es 08:00 más su viaje. La hora de salida del camión es la llegada menos (viaje de la mezcla + margen). Si ninguna planta llega a tiempo, 422 `RN-08`.
  6. Guarda la reserva PROVISORIA con vencimiento en 30 minutos. Reutiliza la fila si la orden tenía una reserva vencida o liberada, porque `orden_trabajo_id` es único.
- `evaluarCuadrilla(...)`: revisa las reglas en orden y devuelve la primera que no se cumple:
  - **RN-05:** que no tenga otra reserva vigente ese día (`existeReservaSolapada`).
  - **RN-06:** que tenga suficientes operarios activos y, si el trabajo pide máquina, suficientes máquinas de ese tipo disponibles.
  - **RN-07:** que el viaje al tramo (`proveedorRutas.calcularRuta`) no supere el máximo y que la ida y vuelta entre en la jornada.
  - Si pasa todo, devuelve la cuadrilla como elegida, con su ruta.
- `confirmarReserva(id)`:
  1. 404 si no existe.
  2. Si ya está CONFIRMADA, la devuelve (idempotente).
  3. Si no es PROVISORIA, 409.
  4. Si el TTL venció, la guarda como EXPIRADA y lanza 409. Lleva `noRollbackFor = ConflictoEstadoException`: sin eso, la excepción desharía la transacción y el EXPIRADA no quedaría guardado.
  5. Si no, la pasa a CONFIRMADA.
- `liberarReserva(id)`: una PROVISORIA o CONFIRMADA pasa a LIBERADA. Si ya estaba LIBERADA o EXPIRADA, la devuelve sin cambios (idempotente).
- `expirarVencidas()`: llama al `UPDATE` masivo del repositorio.
- `bloqueaRecursos(r, ahora)`: verdadero si la reserva está CONFIRMADA, o PROVISORIA y todavía no venció.
- Record interno `Evaluacion`: el resultado de evaluar una cuadrilla (rechazada con regla y detalle, o elegida con su ruta). `esElegida()` dice cuál de los dos casos es.

**`service/ExpiracionReservasJob.java`**
- `expirarVencidas()`: `@Scheduled` cada 60 s (configurable con `app.reservas.expiracion-ms`). Llama a `ReservaService.expirarVencidas` y registra cuántas pasaron a EXPIRADA. Un error no frena las corridas siguientes. Las reservas vencidas ya no bloqueaban recursos; el job hace que el estado guardado también lo diga.

**`client/ProveedorRutas.java`**: interfaz con `calcularRuta(origen, destino)` y el record `ResultadoRuta` (minutos, km y fuente). `ReservaService` depende de esta interfaz y no de OSRM, así que se podría cambiar de proveedor sin tocar las reglas.

**`client/OsrmProveedorRutas.java`**: la implementación con OSRM (el servidor público de ruteo sobre datos de OpenStreetMap).
- El constructor crea un `RestTemplate` con timeouts (2 s y 5 s) y un interceptor que pone el `User-Agent` propio, que pide la política del servidor público.
- `calcularRuta(...)` lleva tres anotaciones:
  - `@Cacheable("rutas")`: el mismo par de coordenadas se responde de la caché;
  - `@CircuitBreaker`: después de muchos fallos deja de llamar un rato;
  - `@RateLimiter`: como máximo 1 pedido por segundo, con hasta 3 s de espera.

  Arma las dos `Coordenada` y llama a `/route/v1/driving/{origen};{destino}` en orden longitud,latitud. Convierte la duración a minutos multiplicada por el factor de corrección (1,3, porque el perfil de OSRM no considera el tránsito) y la distancia a km. Devuelve fuente OSRM.
- `fallbackCalcularRuta(...)`: si OSRM falla, el breaker está abierto o se agotó el límite, estima en línea recta: distancia de Haversine × 1,3 a 50 km/h, con un mínimo de 5 minutos. Marca la fuente ESTIMADA.
- `calcularDistanciaHaversine(...)`: distancia sobre la esfera terrestre entre dos coordenadas, con radio de 6371 km.
- Records `OsrmResponse` y `OsrmRoute`: la parte de la respuesta de OSRM que se usa.

**`client/VencimientoCacheRutas.java`**
- `vaciar()`: `@Scheduled` cada 6 h (`app.osrm.cache-ttl`). Vacía la caché `rutas` entera, así ninguna ruta guardada tiene más de 6 h.

**`config/OpenApiConfig.java`**: título y descripción de Swagger, con la atribución de los datos de OpenStreetMap (licencia ODbL), que pide la política de OSRM.

**Configuración y migraciones**
- `application.yml`:
  - puerto 8083 y base `ms_recursos`;
  - `app.osrm.url` (servidor público), `factor-correccion` 1,3 y `cache-ttl` PT6H;
  - Resilience4j: breaker con ventana de 5 y 50 % de fallos; rate limiter de 1 pedido por segundo.
- `V1__init.sql`: todas las tablas, con `CHECK` de coordenadas y `orden_trabajo_id` único en `reservas`.
- `V2__datos_demo.sql`:
  - configuración de desplazamiento y composición mínima de los 5 tipos;
  - una planta en Córdoba;
  - dos cuadrillas: Alfa, en Córdoba, con 3 operarios y 2 máquinas, y Beta Lejana, sin operarios;
- `V3__quitar_mantenimientos.sql`: borra la tabla `mantenimientos` (recorte de alcance).
- `V4__quitar_rn04_y_restos_del_recorte.sql`: borra la tabla `certificaciones` (RN-04 quitada el 2026-11-16), deja sin usuario de Keycloak a los operarios `operario` y `operario2` (ese usuario ya no existe) y cambia el valor por defecto de `rol_operativo` a PEON. La V1 la crea y la V2 la siembra; como ya están aplicadas no se editan, y la tabla se borra en una versión nueva.

### 5.5 ms-operaciones

**`MsOperacionesApplication.java`**: `main` de Spring Boot; `@EnableScheduling` activa el job diario.

**Modelo (`modelo/`)**
- `OrdenTrabajo`:
  - `faseId` y `tramoId`: ids de Obras, sin clave foránea porque viven en otra base;
  - `fechaProgramada`, `estado`, `reservaId`, `horaSalidaCamion` y `contadorSuspensiones`;
  - `@Version` (bloqueo optimista): si dos procesos modifican la misma orden, el segundo falla en lugar de pisar al primero;
  - `creado` y `actualizado`, que completan `@PrePersist` y `@PreUpdate`.
- `EstadoOrden`: los 10 estados. Las transiciones válidas (RN-11) no viven en el enum: las controla `EjecucionService.exigirEstado` y el chequeo del principio de `habilitarOrden`, que dan 422 si la orden no está en el estado que corresponde.
- `EvaluacionClimatica`: los valores medidos (humedad del suelo, lluvia y temperatura), si cumplió los umbrales y la fecha (`@PrePersist`). Pertenece a una orden.
- `UmbralClimatico`: una fila por `TipoTrabajo` con la humedad del suelo máxima (en m³/m³), la probabilidad de lluvia máxima (%) y la temperatura mínima (°C).
- `Suspension`: orden, fase, `motivo` (CLIMA o RECHAZO), detalle, `vigente` y fecha. RN-12 cuenta las vigentes de la fase.
- `ConfiguracionSuspensiones`: una sola fila con el límite de suspensiones (3).
- `TipoTrabajo`: el mismo vocabulario que `TipoFase` de Obras.
- `MotivoSuspension`: CLIMA o RECHAZO.

**Repositorios (`repositorios/`)**
- `OrdenTrabajoRepository`: `findByFechaProgramadaAndEstado`, `findByFechaProgramada` y `findByEstado` (para listar y para el job) y `findByFaseIdAndEstado` (para cerrar las suspendidas de una fase).
- `EvaluacionClimaticaRepository.obtenerHistorialPorOrden(ordenId)`: JPQL ordenado de la más nueva a la más vieja.
- `SuspensionRepository`:
  - `countByFaseIdAndVigenteTrue`: el conteo de RN-12;
  - los tres listados del historial;
  - `marcarNoVigentes(faseId)`: un `UPDATE` que reinicia el conteo y conserva el historial.
- `UmbralClimaticoRepository.findByTipoTrabajo` y `ConfiguracionSuspensionesRepository`.

**DTO (`dtos/`)**
- De entrada, con validación:
  - `CrearOrdenRequest`: fase, tramo y fecha obligatorios; la fecha no puede estar en el pasado (`@FutureOrPresent`).
  - `AvanceOrdenRequest`: porcentaje de 1 a 100.
  - `RechazoOrdenRequest`: motivo obligatorio.
  - `ResolucionClimaticaRequest`: `decision` HABILITAR o SUSPENDER, y un motivo opcional.
  - `HabilitacionDiariaRequest`: fecha obligatoria.
  - `UmbralClimaticoRequest`: los tres valores obligatorios.
- De salida:
  - `OrdenTrabajoDTO.desde(orden)`;
  - `AvanceOrdenResponse`, con el avance y estado de la fase y el estado de la orden;
  - `EvaluacionClimaticaResponseDTO.desde(...)` y `SuspensionDTO.desde(...)`;
  - `ResultadoHabilitacionDTO`: orden, estado y motivo si no se habilitó.
- Lo que Operaciones recibe de los otros servicios (cada consumidor define su DTO; no se comparte con commons):
  - `ContextoFaseDTO` y `FaseDTO`, de Obras;
  - `ReservaDTO`, de Recursos;
  - `SolicitudReservaDTO`: el cuerpo que se manda a Recursos.

  Todos llevan `@JsonIgnoreProperties(ignoreUnknown = true)`: si el otro servicio agrega un campo, este no se rompe.
- De Open-Meteo:
  - `OpenMeteoResponseDTO`: la respuesta horaria (horas, temperatura, probabilidad de lluvia y humedad del suelo);
  - `DatosClimaDTO`: el resultado ya reducido a los tres peores valores.

**Excepciones (`excepciones/`)**
- `OrdenNoEncontrada`: extiende `RecursoNoEncontradoException`, así sale 404 con el formato de commons.
- `TransicionInvalida`: extiende `ReglaNegocioException` con código `RN-11`, así sale 422.
- `ServicioNoDisponibleException`: se lanza cuando Obras o Recursos no responden.
- `ManejadorServiciosExternos`: un `@RestControllerAdvice` con prioridad máxima.
  - `handleServicioNoDisponible`: devuelve 503 «Servicio no disponible» con el mismo formato. La prioridad hace falta porque si no, el `handleGenerico` de commons lo convertiría en 500.

**Clientes de los otros servicios (`clientes/`)**
- `ObrasClient` y `RecursosClient`: interfaces con lo que Operaciones necesita de cada servicio. Los servicios dependen de las interfaces, así los tests las simulan con Mockito.
- `ObrasHttpClient`: usa el `sistemaRestClient` de commons (token de SISTEMA) y llama directo a `app.servicios.obras-url`, sin pasar por el gateway.
  - `obtenerContextoFase(faseId)`: GET del contexto; un 404 de Obras se traduce a 404 y un error de red a `ServicioNoDisponibleException`.
  - `reprogramarFase`, `marcarFaseEnRiesgo` y `registrarAvance`: PATCH y POST a las rutas de Obras, a través de `ejecutar`.
  - `ejecutar(descripcion, llamada)`: ejecuta la llamada. Una respuesta vacía o un error de red pasan a `ServicioNoDisponibleException`; un 4xx pasa por `traducir`.
  - `traducir(e, descripcion)`: lee el error de Obras (su `detail` y su `codigoRegla`) y lo vuelve a lanzar como la misma excepción de commons (422, 409 o 404). Así el motivo original de Obras llega hasta el cliente.
  - `tramosDeObra(obraId)`: pide los tramos de una obra **con el token del usuario**, no con el de SISTEMA. Usa un segundo cliente sin el interceptor de SISTEMA y reenvía el correlation-id.
  - `tokenDelUsuario()`: saca ese token del contexto de seguridad.
  - `clienteConTimeouts()`: arma ese segundo cliente.
- `RecursosHttpClient`: igual que el de Obras, contra `app.servicios.recursos-url`. `proponerYReservar`, `confirmar` y `liberar` pasan por `ejecutar` y `traducir`. `leerError(e)` lee el cuerpo de error de Recursos.

**Controladores (`controller/`)**: las rutas y roles están en la sección 4.3.
- `OTController`: `crearOrden` (201), `obtenerPorId`, `listar`, `habilitar` y `obtenerHistorialClimatico` llaman a `OTServiceInterfaz`; `habilitar` usa `habilitarOrden`, el mismo método que el lote del día, y el último convierte cada evaluación a DTO.
- `EjecucionController`: `resolverClima`, `iniciar`, `rechazar` y `registrarAvance` (201) llaman a `EjecucionService`.
- `HabilitacionDiariaController.habilitar`: llama a `HabilitacionDiariaService.habilitarDelDia`.
- `SuspensionController.historial`: llama a `SuspensionService.historial`.
- `UmbralClimaticoController`: `actualizarUmbral` (ADMIN) llama a `UmbralClimaticoService.guardarOActualizarUmbral` y `listarUmbrales` a `listarUmbrales`. El tipo de trabajo de la ruta se convierte solo al enum; uno inexistente da 400.

**`servicios/OTServiceInterfaz.java`**: interfaz del servicio de órdenes: `crearOrden`, `obtenerPorId`, `listar`, `habilitarOrden` y `obtenerHistorialClimatico`. La implementa `OrdenTrabajoServiceImpl`.

**`servicios/OrdenTrabajoServiceImpl.java`**
- `crearOrden(request)`:
  1. Pide el contexto de la fase a Obras: 404 si no existe, y comprueba que la fase sea del tramo indicado (422 `ORDEN-TRAMO`).
  2. Crea la orden PROGRAMADA.
  3. Llama a `suspensionService.cerrarSuspensionesDeLaFase(faseId)`. Si la fase venía en riesgo, crear la orden nueva es la decisión del planificador: las órdenes suspendidas de la fase pasan a REPROGRAMADA y el conteo vuelve a cero. Si no había suspensiones, no cambia nada.
- `obtenerPorId(id)`: busca la orden (404).
- `listar(fecha, estado, obraId)`:
  1. Elige la consulta según qué filtros vinieron: fecha y estado, solo fecha, solo estado o ninguno.
  2. Si viene `obraId`, pide a Obras los tramos de esa obra y filtra las órdenes por tramo, porque la orden no guarda la obra: ese dato es de Obras.
- `habilitarOrden(ordenId)`: el proceso central de la sección 3, en este orden:
  1. Solo acepta órdenes PROGRAMADA o RECURSOS_ASIGNADOS (422 `RN-11`).
  2. Contexto de Obras y RN-01 (409 si la fase anterior no está completa).
  3. `tipoTrabajoDe` y los umbrales del tipo (422 `RN-09` si faltan).
  4. Si no tiene reserva, pide una a Recursos. Si Recursos rechaza o no responde, la excepción corta acá y, como el método es `@Transactional`, la orden queda como estaba. Si la reserva llega, guarda su id y la hora de salida del camión y pasa a RECURSOS_ASIGNADOS.
  5. Pasa a EVALUACION_CLIMATICA y pide el clima.
     - Si el clima falla por cualquier motivo, la orden queda PENDIENTE_CONFIRMACION_CLIMATICA, conserva la reserva y el método termina.
     - Si el clima llega, compara: humedad ≤ máxima, lluvia ≤ máxima y temperatura ≥ mínima. Guarda la `EvaluacionClimatica`.
  6. Si cumple, confirma la reserva y la orden queda HABILITADA. Si no, llama a `suspensionService.suspender(orden, CLIMA, detalle)`, con el detalle de lo medido contra los umbrales.
- `tipoTrabajoDe(contexto)`: convierte el texto que manda Obras al enum `TipoTrabajo`; si no lo reconoce, 422.
- `obtenerHistorialClimatico(ordenId)`: 404 si la orden no existe; si no, sus evaluaciones.
- `mapearADto(orden)`: atajo a `OrdenTrabajoDTO.desde`.

**`servicios/SuspensionService.java`**: la **saga** de suspensión. Una saga es una operación repartida entre servicios en la que cada paso tiene una compensación, porque no hay una transacción de base de datos que abarque a los tres. No tiene `@Transactional` propio: corre dentro de la transacción de quien la llama. Si falla un paso de Obras, se deshace lo local; lo ya liberado en Recursos se puede repetir sin daño.
- `suspender(orden, motivo, detalle)`:
  1. libera la reserva;
  2. pasa la orden a SUSPENDIDA;
  3. guarda la `Suspension`;
  4. cuenta las suspensiones vigentes de la fase y las copia al contador de la orden;
  5. si llegó al límite (3), llama a `marcarFaseEnRiesgo`; si no, a `reprogramar`.
- `reprogramar(orden)`: la nueva fecha es la mayor entre el día siguiente a la orden y el fin de la fase anterior. Con una fecha anterior a ese fin, Obras rechazaría por RN-02 y la saga fallaría. Pide a Obras correr la fase (que corre también las dependientes), crea la orden nueva PROGRAMADA en la fecha que devolvió Obras y deja la vieja REPROGRAMADA.
- `marcarFaseEnRiesgo(orden)`: llama a Obras para pasar la fase a EN_RIESGO. La orden queda SUSPENDIDA y no se crea otra.
- `cerrarSuspensionesDeLaFase(faseId)`: la llama `crearOrden`. Pasa las órdenes SUSPENDIDA de la fase a REPROGRAMADA y marca sus suspensiones como no vigentes.
- `liberarReserva(orden)`: pide a Recursos liberar la reserva. Si Recursos no responde, solo registra un aviso, porque la reserva vence sola por TTL. Después borra la reserva y la hora del camión de la orden. Se usa en `suspender`.
- `historial(faseId, ordenId)`: por orden (tiene prioridad), por fase o todo, ordenado por fecha.
- `limiteSuspensiones()`: lee el límite de la tabla de configuración; si falta la fila, lanza error.

**`servicios/EjecucionService.java`**: lo que hace el capataz con una orden.
- `resolverClima(ordenId, request)` (RN-10): exige PENDIENTE_CONFIRMACION_CLIMATICA.
  - Con HABILITAR: si no hay reserva, 409; si hay, confirma la reserva y la orden queda HABILITADA.
  - Con SUSPENDER: corre la saga por clima, con el motivo del capataz o uno por defecto.
- `iniciar(ordenId)`: exige HABILITADA y la pasa a EN_EJECUCION.
- `rechazar(ordenId, motivo)`: exige HABILITADA y corre la saga con motivo RECHAZO.
- `registrarAvance(ordenId, porcentaje)`: exige EN_EJECUCION y manda el avance a Obras. Si Obras devuelve la fase COMPLETADA, la orden pasa a COMPLETADA. Devuelve el avance y los dos estados.
- `buscar(id)`: busca la orden (404).
- `exigirEstado(orden, requerido, accion)` (RN-11): si la orden no está en el estado requerido, lanza `TransicionInvalida` (422).

**`servicios/HabilitacionDiariaService.java`**
- `habilitarDelDia(fecha)`: junta las órdenes de la fecha en PROGRAMADA y RECURSOS_ASIGNADOS y llama a `habilitarOrden` con cada una. Si una falla, se anota el motivo y se sigue con la siguiente. Es reejecutable: las ya habilitadas no se vuelven a tomar.
- `motivoDe(e)`: para errores de negocio o de servicios externos devuelve el mensaje; para cualquier otro, un texto genérico, para no filtrar detalles internos.

**`servicios/JobHabilitacionDiaria.java`**
- `ejecutarEvaluacionDiaria()`: `@Scheduled` con el cron `app.jobs.habilitacion.cron` (por defecto todos los días a las 6). Llama a `ejecutar(LocalDate.now())`. El compose fija la zona horaria de Argentina para que «hoy» y las 6 h sean las de acá.
- `ejecutar(fecha)`: llama a `habilitarDelDia` y registra el resumen. Un fallo general, como la base caída, se registra pero no tira el scheduler.
- `registrarResumen(fecha, resultados)`: registra en el log las órdenes que no se habilitaron y cuántas quedaron en cada estado.

**`servicios/InterfazClimaService.java`**: interfaz con `obtenerCondicionesClimaticas(lat, lon, fecha)`. Cumple el papel de `ProveedorClima` de la propuesta: el resto del servicio no sabe que existe Open-Meteo.

**`servicios/OpenMeteoService.java`**: la implementación con Open-Meteo, una API pública y sin clave.
- Constructores: el público lee de la configuración la URL, la ventana (6 h) y la hora de inicio de la jornada (8). Los de paquete reciben un `RestClient` simulado, para los tests. El último rechaza una ventana que se sale del día.
- `construirRestClient()`: cliente con timeouts de 3 s y 5 s.
- `obtenerCondicionesClimaticas(lat, lon, fecha)` (RN-09):
  - Lleva `@Retry` (3 intentos con 1 s entre ellos) y `@CircuitBreaker`.
  - Arma la `Coordenada` y pide los datos horarios de esa fecha (`hourly=temperature_2m,precipitation_probability,soil_moisture_0_to_1cm`, `start_date` y `end_date` = la fecha, `timezone=auto`).
  - Toma el **peor** valor de la ventana: la menor temperatura, la mayor probabilidad de lluvia y la mayor humedad del suelo. Si una sola hora no cumple, no se trabaja.
  - La humedad del suelo está en m³/m³ (0,15 = 15 % de agua en volumen), no en porcentaje.
- `indicesDeLaVentana(tiempos, fecha)`: busca en la lista de horas la posición de cada hora de la ventana (08:00 a 13:00); si falta alguna, lanza error.
- `valorEn(valores, indice, nombre)`: devuelve el valor de esa hora o lanza error si falta. Nunca se asume un valor (RN-10).
- `obtenerCondicionesFallback(...)`: lo llama el breaker o el último reintento fallido. Registra el error y relanza una excepción. `habilitarOrden` la atrapa y deja la orden PENDIENTE_CONFIRMACION_CLIMATICA.

**`servicios/UmbralClimaticoService.java`**
- `guardarOActualizarUmbral(tipo, request)`: busca el umbral del tipo; si no existe, lo crea; y guarda los tres valores.
- `listarUmbrales()`: devuelve todos los umbrales.

**`config/OpenApiConfig.java`**: título y esquema `bearerAuth` de Swagger.

**Configuración y migraciones**
- `application.yml`:
  - puerto 8084 y base `ms_operaciones`;
  - Resilience4j para Open-Meteo: reintento 3 × 1 s y breaker;
  - `app.open-meteo` (URL, ventana y hora de inicio) y `app.servicios` (URLs de Obras y Recursos);
  - el cliente de SISTEMA (`obrasviales.sistema-client`, con el secreto en `SERVICIO_SISTEMA_SECRET`).

  `application-docker.yml` cambia las URLs a los nombres de contenedor.
- `V1__init.sql`: `ordenes_trabajo` (con `version` para el bloqueo optimista), `evaluaciones_climaticas` y `umbrales_climaticos`.
- `V2__datosPrueba.sql`: los umbrales de los 5 tipos, por ejemplo BASE con humedad 0,40 m³/m³, lluvia 20 % y 5 °C.
- `V3__suspensiones.sql`: `suspensiones` (con índice por fase), `configuracion_suspensiones` (límite 3) y `fases_en_riesgo`, que la V6 borra.
- `V4__seed_demo_orders.sql`: las 3 órdenes del escenario, en hora de Argentina:
  - hoy, en la fase 2 del tramo 1;
  - mañana, en la misma fase, para mostrar la suspensión por clima con los umbrales bajados;
  - pasado mañana, en la fase 3 del tramo 2 de Río Cuarto, que se rechaza por distancia.
- `V5__cierre_ordenes.sql`: tablas del cierre con consumo, que la V6 borra.
- `V6__recorte_alcance.sql`: borra `fases_en_riesgo`, `cierre_consumos` y `cierres_orden`. Las migraciones ya aplicadas no se editan; los cambios van siempre en una versión nueva, porque Flyway guarda una suma de control de cada una y no arranca si cambió.

## 6. Infraestructura

- **`docker-compose.yml`**: levanta Postgres, Keycloak, los tres servicios y el gateway, cada uno con un *healthcheck*. Cada servicio arranca recién cuando lo que necesita está sano. Solo el gateway (8081) y Keycloak (8080) se publican; Postgres se publica solo en `127.0.0.1`. Las contraseñas salen del `.env` (los valores de demo están en `.env.example`).
- **`*/Dockerfile`**: construcción en dos etapas. Una imagen con Maven y Java 25 compila el módulo; la imagen final solo tiene el JRE y el `.jar`.
- **`docker/init-db/01-init-schemas.sql`**: crea las tres bases, cada una con su usuario dueño, y les quita el acceso público (`REVOKE CONNECT ... FROM PUBLIC`).
- **`docker/keycloak/realm-obras-viales.json`**: se importa al arrancar Keycloak. Define:
  - el realm `obras-viales` y sus roles;
  - los usuarios de demo `admin`, `planificador`, `capataz` y `auditor`, con contraseña `<rol>123`, solo para uso local;
  - el cliente público `obras-public-client`, con el que se loguean las personas;
  - el cliente `servicio-sistema`, la cuenta de servicio con *client credentials*.

## 7. Tests

Hay un test por regla de negocio, con casos normales, de límite y de rechazo. Las APIs externas se simulan con `MockRestServiceServer`. Los tests de controlador prueban, para cada endpoint, sin token (401), con rol insuficiente (403) y con el rol correcto.

| Clase | Qué prueba |
|---|---|
| `obras-commons`: `CoordenadaTest`, `KeycloakConversorRolesTest`, `CorrelationIdFilterTest`, `SistemaTokenServiceTest`, `GlobalExceptionHandlerTest`, `ProblemDetailSeguridadTest` | Validación de coordenadas y orden longitud,latitud; roles a `ROLE_*`; correlation-id en el MDC; token de SISTEMA con caché y reintentos; formato estándar de 400, 404, 405, 409, 422, 500, 401 y 403. |
| `api-gateway`: `SeguridadGatewayTests`, `ReglasPorRolGatewayTests`, `ErroresSeguridadGatewayTests`, `CorrelationIdEnLogsTests` | 401 sin token, health abierto, reglas de rol por ruta y método, 401/403 en formato estándar, id propio del gateway y su aparición en el log. |
| `ms-obras`: `ObraControllerTest`, `ObraControllerConsultasTest`, `TramoControllerTest`, `FaseControllerTest`, `FaseControllerLecturaTest`, `FaseInternaControllerTest`, `OperacionControllerTest` | Seguridad y respuestas HTTP de cada endpoint de Obras. |
| `ms-obras`: `ObraServiceTest`, `ObraServiceConsultasTest`, `FaseServiceTest`, `FaseServiceAvanceTest`, `MaterialServiceTest`, `OperacionServiceTest` | Estado y avance de la obra; RN-02 (alta con fecha previa, cascada, ciclos); RN-01 en el primer avance; avance que no retrocede; riesgo; RN-03 (cálculo y superficie inválida); contexto para Operaciones. |
| `ms-recursos`: `ReservaServiceReglasTest` | RN-05 a RN-08, incluidos los límites (viaje igual al máximo, ventana justa) y la regla informada cuando ninguna cuadrilla sirve. |
| `ms-recursos`: `ReservaServiceEstadosTest`, `ExpiracionReservasTest` | Máquina de estados de la reserva, idempotencia, TTL vencido → EXPIRADA, job de expiración. |
| `ms-recursos`: `ReservaControllerSeguridadTest`, `ReservaControllerContratoTest`, `ReservaControllerEstadosTest`, `CatalogoControllerSeguridadTest` | Internos solo SISTEMA; campos de la respuesta (hora de salida del camión); 404/409; matriz de permisos de consultas y check-in. |
| `ms-recursos`: `CatalogoServiceTest`, `CuadrillaServiceTest`, `OsrmProveedorRutasIntegrationTest`, `VencimientoCacheRutasTest` | Consultas; check-in solo del capataz de la cuadrilla; OSRM y su respaldo ESTIMADA; vencimiento de la caché. |
| `ms-operaciones`: `OrdenTrabajoServiceHabilitacionTest` | El proceso central: RN-01, RN-09, RN-10 (clima caído → pendiente), RN-11, suspensión y confirmación. |
| `ms-operaciones`: `SuspensionServiceTest` | La saga: fecha de reprogramación, orden nueva, límite → fase en riesgo, Recursos caído, Obras que rechaza, cierre de suspensiones de la fase. |
| `ms-operaciones`: `EjecucionServiceTest` | Inicio, rechazo, avances (al 100 % completa), resolución climática y estados inválidos. |
| `ms-operaciones`: `OrdenTrabajoServiceCrearOrdenTest`, `OrdenTrabajoServiceListadoTest`, `HabilitacionDiariaServiceTest`, `JobHabilitacionDiariaTest` | Alta con validación del tramo, filtros combinados, lote que sigue si una orden falla, job que delega y no cae. |
| `ms-operaciones`: `OpenMeteoServiceTest`, `ObrasHttpClientTest`, `ObrasHttpClientTramosTest`, `RecursosHttpClientTest` | Peor valor de la ventana y datos faltantes; traducción de errores de Obras y Recursos; tramos con el token del usuario. |
| `ms-operaciones`: `SeguridadEndpointsTest` | 401/403/2xx de los 13 endpoints y 404 de las 7 rutas quitadas. |
| `*ApplicationTests` (uno por módulo) | Que la aplicación arranque. Los de los servicios necesitan Postgres: Flyway aplica las migraciones y `ddl-auto: validate` comprueba que coincidan con las entidades. |

## 8. Cómo se relacionan los servicios (resumen)

| Quién llama | A quién | Para qué | Con qué token |
|---|---|---|---|
| Gateway | Obras, Recursos, Operaciones | Reenviar el pedido del cliente | El del usuario |
| Operaciones (`ObrasHttpClient`) | Obras | Contexto de la fase, avance, reprogramación y riesgo | SISTEMA |
| Operaciones (`ObrasHttpClient.tramosDeObra`) | Obras | Tramos de una obra para filtrar órdenes | El del usuario |
| Operaciones (`RecursosHttpClient`) | Recursos | Proponer, confirmar y liberar reservas | SISTEMA |
| Operaciones (`OpenMeteoService`) | Open-Meteo | Clima de la jornada | Ninguno (API pública) |
| Recursos (`OsrmProveedorRutas`) | OSRM | Tiempos de viaje | Ninguno (servidor público, con User-Agent) |
| Todos (`SistemaTokenService`) | Keycloak | Obtener el token de SISTEMA | Secreto del cliente |

Obras y Recursos no se llaman entre sí ni llaman a Operaciones: Operaciones es el único orquestador.

## 9. Código sin uso

Al escribir esta guía aparecieron restos del recorte que el código principal no llamaba. Se borraron el 2026-11-14, sin que cambiara ningún test:
- `ms-obras`: la clase vacía `CronogramaService` y su test sin casos, `ObraService.buscarTramo`, `FaseRepository.findByTramoId` y `findByTramoIdOrderByFechaInicioPlanificadaAsc`, y `TramoRepository.findByObraId`.
- `ms-recursos`: `CertificacionRepository.findByOperarioId` (después, el 2026-11-16, se borró toda la entidad al quitar RN-04) y `ReservaRepository.findByCuadrillaIdAndFechaReserva`.
- `ms-operaciones`: `OrdenTrabajoRepository.findByFaseId`, `UmbralClimaticoService.obtenerUmbralPorTipo` y `EstadoOrden.puedeTransicionarHacia` (una tabla de transiciones que no consultaba nadie: RN-11 se controla en los servicios, ver 5.5).
