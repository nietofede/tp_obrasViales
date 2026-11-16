# Guía de defensa de GOVP

Esta guía sirve para defender el sistema delante de la cátedra: explica **qué hace cada parte, cómo lo hace y por qué se hizo así**. Cuando aparece una anotación o un mecanismo de Spring (`@Bean`, `@Transactional`, `@PreAuthorize`, un filtro, un proxy), se explica qué hace por dentro y dónde se usa en este repositorio, no solo que "se usa".

Cómo se relaciona con los otros documentos:
- [`guia-del-codigo.md`](guia-del-codigo.md) es el índice exhaustivo: cada archivo y cada método, uno por uno. Sirve para buscar.
- [`guia-de-estudio.md`](guia-de-estudio.md) es el resumen conceptual para leer de corrido.
- Esta guía es la del **cómo**: los mecanismos, con el código real y las preguntas que pueden hacer.

Estado que describe: `main` después del recorte aprobado por la cátedra y de quitar RN-04 (2026-11-16). 32 endpoints, 11 reglas de negocio (RN-01 a RN-12 sin RN-04), 4 roles de personas (ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR) y la cuenta de servicio SISTEMA.

---

## 1. El sistema en una página

**Problema.** Una repartición vial planifica obras de pavimentación divididas en tramos y fases (relevamiento, base, sub-base, carpeta asfáltica, losa de hormigón). Cada día hay que decidir si una orden de trabajo se puede ejecutar: si la fase anterior terminó, si hay una cuadrilla libre con la gente y las máquinas que pide ese trabajo y que llegue a tiempo, si la mezcla asfáltica llega antes de enfriarse y si el clima lo permite.

**Proceso central (la habilitación diaria de una orden).** Lo dispara el PLANIFICADOR (`POST /api/operaciones/ordenes/{id}/habilitacion` o `POST /api/operaciones/habilitaciones-diarias`) o el job diario con la cuenta SISTEMA. El método que hace todo es `OrdenTrabajoServiceImpl.habilitarOrden` (ms-operaciones):

| Paso | Qué pasa | Dónde |
|---|---|---|
| 1 | Se exige que la orden esté PROGRAMADA o RECURSOS_ASIGNADOS (RN-11) | `habilitarOrden`, primeras líneas |
| 2 | Operaciones pide a Obras el contexto de la fase: tipo de trabajo, coordenadas, si la fase anterior está completa | `ObrasHttpClient.obtenerContextoFase` → `GET /api/obras/internos/fases/{id}/contexto-ejecucion` |
| 3 | Si la fase anterior no está completa: 409 (RN-01) y la orden queda como estaba | `habilitarOrden` |
| 4 | Se busca el umbral climático del tipo de trabajo (si no hay: 422 RN-09) | `UmbralClimaticoRepository.findByTipoTrabajo` |
| 5 | Operaciones pide a Recursos "proponer y reservar": elige cuadrilla (RN-05, RN-06, RN-07) y planta (RN-08), y crea una reserva PROVISORIA que vence en 30 min. Si no hay recursos: 422 y la orden queda PROGRAMADA | `RecursosHttpClient.proponerYReservar` → `ReservaService.proponerYReservar` |
| 6 | La orden guarda la reserva y la hora de salida del camión y pasa a RECURSOS_ASIGNADOS y luego a EVALUACION_CLIMATICA | `habilitarOrden` |
| 7 | Operaciones consulta Open-Meteo para la fecha de la orden y toma el peor valor de 08:00 a 13:00 (RN-09). Si no hay dato confiable: PENDIENTE_CONFIRMACION_CLIMATICA y decide el CAPATAZ (RN-10) | `OpenMeteoService.obtenerCondicionesClimaticas` |
| 8 | Cumple los umbrales: se confirma la reserva y la orden queda HABILITADA | `RecursosClient.confirmar` |
| 9 | No cumple: saga de suspensión: libera la reserva, registra la suspensión, Obras corre la fase en cascada (RN-02) y se crea la orden nueva; a la tercera suspensión la fase queda en riesgo (RN-12) | `SuspensionService.suspender` |
| 10 | Después, el CAPATAZ inicia la orden y registra avances; con el 100 % la fase y la orden quedan COMPLETADA | `EjecucionService` |

---

## 2. Arquitectura y por qué está partida así

**Contenedores** (ver `docs/c2-contenedores.md`): Keycloak (8080), API Gateway (8081), ms-obras (8082), ms-recursos (8083), ms-operaciones (8084) y un PostgreSQL con una base por servicio. Todo lo levanta `docker-compose.yml`.

**Por qué tres servicios.** Cada uno es dueño de un pedazo del dominio y de sus datos:
- **ms-obras**: qué hay que construir y cuándo (obras, tramos, fases, dependencias, materiales). Reglas RN-01, RN-02, RN-03.
- **ms-recursos**: con qué se construye (cuadrillas, operarios, máquinas, plantas, reservas). Es el motor de elegibilidad. RN-05 a RN-08. Habla con OSRM.
- **ms-operaciones**: qué se hace hoy (órdenes, clima, suspensiones). Orquesta el proceso central y la saga. RN-09 a RN-12. Habla con Open-Meteo.

Si fuera un solo servicio, la decisión diaria mezclaría planificación, recursos y clima en las mismas clases; separado, cada servicio cambia y se prueba solo, y el contrato entre ellos es HTTP.

**Trusted zone.** Solo el gateway y Keycloak se publican fuera de Docker. Los servicios y Postgres viven en la red interna `obras-network` (Postgres además se publica solo en `127.0.0.1` para desarrollo). Desde afuera no se puede llegar a un servicio sin pasar por el gateway. Entre servicios se llaman directo por la red de Docker (`http://ms-obras:8082`), con el token de la cuenta SISTEMA.

**Una base por servicio.** `docker/init-db/01-init-schemas.sql` crea `ms_obras`, `ms_recursos` y `ms_operaciones`, cada una con su usuario dueño, y les quita el permiso de conexión a los demás (`REVOKE CONNECT ... FROM PUBLIC`). Ningún servicio lee tablas de otro: si Operaciones necesita la fase, la pide por HTTP. Así cada servicio puede cambiar su esquema sin romper a los otros.

**obras-commons** es un JAR que usan los tres servicios. Tiene lo transversal: seguridad base, conversor de roles, formato de errores, excepciones genéricas, filtro de correlation-id, cliente HTTP con token de SISTEMA y el value object `Coordenada`. **No** tiene entidades, lógica de negocio ni DTOs de contrato: si los compartiera, cambiar un DTO de Recursos obligaría a recompilar y desplegar los tres servicios juntos (acoplamiento). Cada consumidor define su propio DTO (por ejemplo `ReservaDTO` en Operaciones) con `@JsonIgnoreProperties(ignoreUnknown = true)`, que le dice a Jackson que ignore los campos que no conoce: si Recursos agrega un campo, Operaciones no se rompe.

---

## 3. Spring desde cero: los mecanismos que aparecen en todo el código

### 3.1 El contenedor, los beans y la inyección de dependencias

**Bean**: un objeto que crea y administra Spring, no el programador con `new`. Spring lo crea una sola vez al arrancar (por defecto es *singleton*), le pasa lo que necesita y lo guarda en su **contenedor** (el `ApplicationContext`). Cuando otra clase pide ese tipo, Spring le entrega la misma instancia.

**Inyección de dependencias**: en lugar de que `ReservaService` haga `new ReservaRepository(...)`, declara en su constructor que lo necesita y Spring se lo pasa. Ejemplo real (`ms-recursos/.../service/ReservaService.java`):

```java
@Service
public class ReservaService {
    private final ReservaRepository reservaRepository;
    private final ProveedorRutas proveedorRutas;
    ...
    public ReservaService(ReservaRepository reservaRepository, ..., ProveedorRutas proveedorRutas) {
        this.reservaRepository = reservaRepository;
        ...
    }
}
```

Al arrancar, Spring ve que `ReservaService` es un bean (por `@Service`), mira su constructor, busca en el contenedor un bean de cada tipo pedido y lo construye. Ventajas que hay que saber decir: (1) los campos son `final`, no cambian; (2) en los tests se pasa un mock en lugar del repositorio real (`@InjectMocks` lo hace en `ReservaServiceReglasTest`); (3) `ProveedorRutas` es una **interfaz**: el servicio no sabe que detrás está OSRM (`OsrmProveedorRutas`), así se puede cambiar el proveedor sin tocar la regla.

**`@RequiredArgsConstructor` (Lombok)**: muchas clases no escriben el constructor a mano. Lombok lo genera al compilar con un parámetro por cada campo `final`. Ejemplo: `SuspensionService` tiene cinco campos `final` y la anotación; el constructor existe en el `.class` aunque no esté en el `.java`, y Spring lo usa para inyectar.

**`@Autowired` cuando hay más de un constructor.** Si una clase tiene un solo constructor, Spring lo usa sin anotación. Si tiene varios, hay que marcar cuál usar. Pasa en tres clases, y siempre por el mismo motivo: un constructor público para Spring y otro de paquete para los tests.
- `OpenMeteoService`: el público recibe la URL y la ventana con `@Value` y arma su propio `RestClient`; el de paquete recibe un `RestClient` simulado. Sin `@Autowired` en el público, la aplicación no arrancaba (pasó en la revisión del 13/11: "No default constructor found").
- `ObrasHttpClient` y `SistemaTokenService`: igual.

**Cómo encuentra Spring las clases (component scan).** `@SpringBootApplication` en `MsRecursosApplication` incluye `@ComponentScan` sobre su paquete (`com.obrasviales.ms_recursos`) y todos los subpaquetes. Toda clase con `@Component`, `@Service`, `@Repository`, `@RestController` o `@Configuration` en esos paquetes se convierte en bean.

### 3.2 Los estereotipos: `@Component`, `@Service`, `@Repository`, `@RestController`

Los cuatro hacen lo mismo para el contenedor (registran la clase como bean); cambian lo que **dicen** y algún comportamiento extra:
- `@Component`: genérico. Se usa para piezas técnicas: `OsrmProveedorRutas`, `ObrasHttpClient`, `CorrelationIdFilter`, los jobs.
- `@Service`: lógica de negocio (`ReservaService`, `FaseService`, `OrdenTrabajoServiceImpl`). Es la capa donde viven las reglas.
- `@Repository`: acceso a datos. En este proyecto los repositorios son interfaces de Spring Data (sección 7.2) y ni siquiera necesitan la anotación, porque Spring Data los detecta por heredar de `JpaRepository`.
- `@RestController` = `@Controller` + `@ResponseBody`: cada método devuelve un objeto que Spring convierte a JSON con Jackson, en vez de buscar una vista HTML.

**Regla de capas del proyecto**: controller → service → repository. Los controladores solo reciben el pedido, validan la entrada y llaman al servicio; las reglas de negocio están en los servicios. Por eso, por ejemplo, RN-01 está en `FaseService.exigirAnteriorCompletada` y no en `FaseController`.

### 3.3 `@Configuration` y `@Bean`: cuándo y cómo se usan acá

`@Bean` se pone sobre un **método** dentro de una clase `@Configuration`. Spring llama a ese método **una vez** al arrancar y guarda lo que devuelve como bean. Se usa cuando el objeto es de una clase que no escribimos nosotros (no le podemos poner `@Component`) o cuando hay que configurarlo antes de usarlo. El nombre del bean es el nombre del método. Si el método tiene parámetros, Spring los resuelve igual que en un constructor.

Los cinco `@Bean` del proyecto:

1. **`SistemaRestClientConfig.sistemaRestClient`** (obras-commons):
   ```java
   @Bean
   public RestClient sistemaRestClient(SistemaRequestInterceptor interceptor) {
       JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
               HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
       fabrica.setReadTimeout(Duration.ofSeconds(10));
       return RestClient.builder().requestFactory(fabrica).requestInterceptor(interceptor).build();
   }
   ```
   - Qué hace: arma **un** cliente HTTP (`RestClient` es el cliente de Spring 6+) con timeouts (3 s para conectar, 10 s para leer) y con un interceptor que a cada pedido le agrega el token de SISTEMA y el correlation-id.
   - Cómo lo usan: `ObrasHttpClient` y `RecursosHttpClient` lo piden en el constructor con `@Qualifier("sistemaRestClient") RestClient restClient`. `@Qualifier` elige el bean por nombre; hace falta porque podría haber más de un `RestClient` en el contenedor y Spring no sabría cuál dar.
   - Por qué es un bean y no un `new` en cada cliente: la configuración (timeouts e interceptor) está en un solo lugar y todos los clientes entre servicios se comportan igual.
   - Por qué **no** se registró un `RestClient.Builder` global con el interceptor: cualquier cliente creado con ese builder (por ejemplo el de Open-Meteo) le mandaría el token de SISTEMA a un tercero. Se corrigió en la revisión de commons (paso 5).

2. **`BaseResourceServerConfig.jwtAuthenticationConverter`** (obras-commons): crea un `JwtAuthenticationConverter` y le pone `KeycloakConversorRoles` para que los roles de Keycloak se conviertan en autoridades de Spring (sección 6.3).

3. **`BaseResourceServerConfig.baseSecurityFilterChain`** (obras-commons): arma la cadena de filtros de seguridad de los tres servicios (sección 6.4). Recibe `HttpSecurity`, un objeto que Spring Security le pasa ya preparado para configurar.
   - Detalle fino: este método llama a `jwtAuthenticationConverter()`. Como la clase es `@Configuration`, Spring la envuelve en un proxy (una subclase generada con CGLIB) que intercepta esa llamada y devuelve **el mismo bean** ya creado, no uno nuevo.

4. **`MsRecursosApplication.cacheManager`** (ms-recursos):
   ```java
   @Bean
   public CacheManager cacheManager() { return new ConcurrentMapCacheManager("rutas"); }
   ```
   Le dice a Spring que la caché de `@Cacheable` se guarda en un `ConcurrentHashMap` en memoria, con una caché llamada `rutas`. `VencimientoCacheRutas` pide este mismo bean para vaciar la caché (sección 9.3).

5. **`SecurityConfig.securityFilterChain`** (api-gateway): la cadena de filtros del gateway, con las reglas por ruta y método (sección 6.5).

### 3.4 Cómo entran a cada servicio las clases de obras-commons (auto-configuración)

El component scan de ms-recursos solo mira `com.obrasviales.ms_recursos.*`. Las clases de commons están en `com.obrasviales.commons.*`, otro paquete: el scan no las ve. Spring Boot tiene un mecanismo para librerías: el archivo `obras-commons/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` lista clases que Boot carga al arrancar en cualquier aplicación que tenga el JAR en el classpath:

```
com.obrasviales.commons.security.BaseResourceServerConfig
com.obrasviales.commons.filter.CorrelationIdFilter
com.obrasviales.commons.exception.GlobalExceptionHandler
com.obrasviales.commons.client.SistemaRestClientConfig
com.obrasviales.commons.client.SistemaRequestInterceptor
com.obrasviales.commons.client.SistemaTokenService
com.obrasviales.commons.client.SistemaClientProperties
```

Consecuencia: basta con poner la dependencia `obras-commons` en el `pom.xml` de un servicio para que tenga seguridad JWT, formato de errores, correlation-id y el cliente con token, sin escribir nada. Alternativa descartada: `scanBasePackages = "com.obrasviales"` en cada aplicación, que obliga a cada servicio a saber cómo está empaquetada la librería.

### 3.5 Configuración externa: `application.yml`, variables, `@Value`, `@ConfigurationProperties` y perfiles

- **`application.yml`** de cada servicio: puerto, base de datos, Keycloak, Resilience4j, URLs. Los secretos no están escritos: `password: ${OBRAS_DB_PASSWORD}`. Spring reemplaza `${...}` por la variable de entorno. Si falta, queda el texto literal y el error es `password authentication failed`. Docker Compose lee el `.env` (que no se commitea; los valores demo están en `.env.example`) y se las pasa a cada contenedor.
- **`@Value("${clave:valorPorDefecto}")`** inyecta un valor suelto. Ejemplos: `OpenMeteoService` recibe `@Value("${app.open-meteo.ventana-ejecucion-horas:6}") int ventanaHoras` (si la clave no está, vale 6). `OsrmProveedorRutas` usa `@Value("${app.osrm.url}")` **sin** valor por defecto, a propósito: si falta la clave, la aplicación no arranca, en lugar de arrancar apuntando a cualquier lado.
- **`@ConfigurationProperties(prefix = "obrasviales.sistema-client")`** en `SistemaClientProperties`: liga un grupo de claves a un objeto. `obrasviales.sistema-client.token-url` se carga en `setTokenUrl(...)` (Spring traduce `token-url` a `tokenUrl`; se llama *relaxed binding*). Es mejor que tres `@Value` sueltos: las claves quedan agrupadas y con tipo.
- **Perfiles.** Cada módulo tiene un `application-docker.yml` que solo se activa con `SPRING_PROFILES_ACTIVE=docker` (lo pone el compose). Cambia lo que es distinto dentro de Docker: la URL de Postgres (`postgres:5432` en lugar de `localhost`), las URLs de los otros servicios y `jwk-set-uri` hacia `http://keycloak:8080`. Trampa conocida: las **listas** del yml (como las rutas del gateway) no se fusionan con las del archivo base, se reemplazan enteras; por eso `api-gateway/.../application-docker.yml` repite las tres rutas completas.

---

## 4. La capa web: cómo entra un pedido y cómo sale la respuesta

### 4.1 Controladores y mapeo de rutas

Ejemplo real (`ms-operaciones/.../controller/OTController.java`, simplificado):

```java
@RestController
@RequestMapping("/api/operaciones/ordenes")
public class OTController {
    @PostMapping("/{id}/habilitacion")
    @PreAuthorize("hasAnyRole('" + Roles.PLANIFICADOR + "', '" + Roles.SISTEMA + "')")
    public ResponseEntity<OrdenTrabajoDTO> habilitar(@PathVariable Long id) {
        return ResponseEntity.ok(ordenService.habilitarOrden(id));
    }
}
```

- `@RequestMapping` en la clase fija el prefijo; `@GetMapping`, `@PostMapping`, `@PutMapping` y `@PatchMapping` en cada método fijan el verbo HTTP y el resto de la ruta.
- Todas las rutas empiezan con `/api/obras`, `/api/recursos` o `/api/operaciones` porque el gateway reenvía la ruta tal cual (no recorta el prefijo: decisión registrada como "StripPrefix" en DECISIONES).
- `@PathVariable Long id` toma `{id}` de la URL y lo convierte a `Long` (si no es un número, el handler de errores responde 400).
- `@RequestParam` toma parámetros de consulta: `GET /ordenes?fecha=...&estado=...&obraId=...` en `listar`.
- `@RequestBody` convierte el JSON del cuerpo en un objeto Java con Jackson.
- `ResponseEntity` deja elegir el código: `ResponseEntity.status(HttpStatus.CREATED).body(...)` para 201 al crear.
- `@AuthenticationPrincipal Jwt jwt` (en `CuadrillaController.registrarUbicacion`) inyecta el token ya validado; de ahí se saca el usuario de Keycloak para comprobar que quien hace el check-in es el capataz de esa cuadrilla.

### 4.2 Validación de entradas (Bean Validation)

Los DTO de entrada tienen anotaciones de `jakarta.validation`: `@NotNull`, `@NotBlank`, `@Size`, `@DecimalMin`, `@DecimalMax`, `@Min`, `@Max`, `@Positive`, `@FutureOrPresent`. Ejemplo: `SolicitudReservaRequest` en Recursos usa `@NotNull @DecimalMin("-90") @DecimalMax("90") Double tramoLat`.

- `@Valid` delante del `@RequestBody` hace que Spring valide antes de entrar al método. Si algo no cumple, lanza `MethodArgumentNotValidException` y el handler de commons responde 400 con un mapa `errores` campo → mensaje.
- Por qué `Double` y no `double`: con el primitivo, un campo que falta en el JSON llega como `0.0` y `@NotNull` no lo detecta (un primitivo nunca es null). Pasó en Recursos y se corrigió.

### 4.3 Formato de los DTO de respuesta

Las respuestas son `record` de Java con un método estático `desde(entidad)`. Ejemplo: `OperarioResponse.desde(Operario o)`. Así la API no expone la entidad JPA (con sus relaciones perezosas y campos internos) y el contrato queda explícito.

### 4.4 OpenAPI / Swagger

Cada servicio tiene springdoc y una clase `OpenApiConfig` con `@OpenAPIDefinition` (título y descripción; en Recursos incluye la atribución de OpenStreetMap/ODbL que pide OSRM) y `@SecurityScheme` de tipo `bearer`, que agrega el botón *Authorize* para pegar el token. `@Operation` y `@Tag` describen cada endpoint. La UI (`/swagger-ui.html`) y el JSON (`/v3/api-docs`) están abiertos sin token (`permitAll` en `BaseResourceServerConfig`), pero solo se ven corriendo el servicio por fuera de Docker, porque dentro de Docker los servicios no se publican (decisión del 13/11).

---

## 5. Errores: un solo formato para toda la API

### 5.1 Excepciones de dominio

En commons hay tres excepciones genéricas, todas `RuntimeException` (no obligan a declarar `throws`):

| Excepción | Cuándo se lanza | Respuesta |
|---|---|---|
| `RecursoNoEncontradoException` | id inexistente | 404 |
| `ConflictoEstadoException` | el estado actual no permite la acción (por ejemplo, RN-01, reserva no PROVISORIA) | 409 |
| `ReglaNegocioException(mensaje, codigoRegla)` | se incumple una regla | 422 con `codigoRegla` (por ejemplo `RN-07`) |

Operaciones agrega `ServicioNoDisponibleException` (503) para cuando Obras o Recursos no responden, y `OrdenNoEncontrada`/`TransicionInvalida`, que heredan de las de commons.

### 5.2 `@RestControllerAdvice` y `@ExceptionHandler`

`GlobalExceptionHandler` (commons) está anotado con `@RestControllerAdvice`: Spring lo aplica a **todos** los controladores. Cada método con `@ExceptionHandler(Tipo.class)` atrapa ese tipo de excepción cuando sale de un controlador (aunque se haya lanzado tres capas más abajo, en un servicio) y devuelve la respuesta. Ejemplo:

```java
@ExceptionHandler(ReglaNegocioException.class)
public ProblemDetail handleReglaNegocio(ReglaNegocioException ex) {
    ProblemDetail problem = crearProblemDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Incumple regla de negocio", ex.getMessage(), "urn:problem:business-rule");
    problem.setProperty("codigoRegla", ex.getCodigoRegla());
    return problem;
}
```

**`ProblemDetail`** es la clase de Spring para el estándar **RFC 7807** (*Problem Details for HTTP APIs*): un JSON con `type`, `title`, `status`, `detail` e `instance`, con content-type `application/problem+json`. El proyecto le suma `timestamp`, `correlationId` y, en las reglas, `codigoRegla`. Así el cliente siempre sabe qué forma tiene un error.

**Errores propios de Spring (404 de ruta inexistente, 405, JSON ilegible, enum inválido).** El handler **hereda** de `ResponseEntityExceptionHandler`, la clase de Spring que ya sabe responder esos casos con el código correcto. Se sobreescriben tres métodos: `handleMethodArgumentNotValid` (400 con el mapa de errores), `handleHttpMessageNotReadable` (400 "solicitud ilegible") y `handleExceptionInternal`, por donde pasan todos, para sumarles `type`, `timestamp` y `correlationId`. Historia: al principio había solo un `@ExceptionHandler(Exception.class)` que convertía todo eso en 500; se arregló en la Rama 1.

**El 500 genérico.** `handleGenerico` atrapa cualquier otra excepción, la **registra en el log** (`log.error("Error no controlado", ex)`) y responde 500 sin mostrar detalles internos al cliente.

**Orden entre advices.** `ManejadorServiciosExternos` (Operaciones) atrapa `ServicioNoDisponibleException` y responde 503. Tiene `@Order(Ordered.HIGHEST_PRECEDENCE)` para que Spring lo consulte antes que el de commons; sin eso, el `@ExceptionHandler(Exception.class)` genérico lo atrapaba primero y salía 500.

### 5.3 Por qué los 401 y 403 se escriben a mano (`ProblemJson`)

Los 401 (sin token o token inválido) y los 403 del filtro de seguridad ocurren **antes** de llegar al controlador: los filtros de Spring Security corren antes que el `DispatcherServlet`, que es quien aplica los `@RestControllerAdvice`. Por eso el handler no los ve. Para que tengan el mismo formato:
- `ProblemDetailAuthenticationEntryPoint` implementa `AuthenticationEntryPoint` (lo que Spring Security llama cuando falta autenticación). Primero deja que `BearerTokenAuthenticationEntryPoint` ponga el 401 y el header `WWW-Authenticate` (lo pide el estándar de tokens Bearer) y después escribe el JSON con `ProblemJson.escribir`.
- `ProblemDetailAccessDeniedHandler` implementa `AccessDeniedHandler` para el 403.
- `ProblemJson` arma el JSON con un `StringBuilder` y escapa comillas y barras, porque fuera de Spring MVC no hay conversor de mensajes disponible.
- El gateway tiene copias propias de estas tres clases porque no depende de commons.

Un 403 lanzado **dentro** de un controlador (por `@PreAuthorize` o por `AccessDeniedException` del check-in) sí pasa por el advice: lo responde `handleAccessDenied`.

## 6. Seguridad: quién puede hacer qué y cómo se comprueba

### 6.1 Keycloak, tokens y roles

**Keycloak** es el servidor de identidad. El realm `obras-viales` (importado al arrancar desde `docker/keycloak/realm-obras-viales.json`) tiene:
- los roles `ADMIN`, `PLANIFICADOR`, `CAPATAZ`, `AUDITOR` y `SISTEMA`;
- los usuarios de demo `admin`, `planificador`, `capataz` y `auditor` (contraseña `<usuario>123`, solo para uso local);
- dos clientes: `obras-public-client` (público, para que las personas pidan token con usuario y contraseña) y `servicio-sistema` (confidencial, con secreto, para la cuenta de servicio).

**JWT** (*JSON Web Token*): el token que entrega Keycloak. Tiene tres partes en Base64 separadas por puntos: encabezado, contenido (*claims*) y firma. Los claims que importan acá: `iss` (quién lo emitió), `exp` (vencimiento, 300 s), `preferred_username` y `realm_access.roles` (los roles). La firma se hace con la clave privada de Keycloak; cualquiera puede comprobarla con la clave pública, que Keycloak publica en `/protocol/openid-connect/certs` (JWKS). Por eso los servicios **no** consultan a Keycloak en cada pedido: validan la firma localmente.

**Dos formas de pedir el token:**
- *Password grant*: una persona manda usuario y contraseña al cliente público (lo usan la colección, el panel y la app interna). Solo es aceptable en local con usuarios de demo.
- *Client credentials*: un programa manda `client_id` y `client_secret` y recibe un token a nombre de la cuenta de servicio, con rol SISTEMA. Lo usan el job diario y las llamadas entre servicios (`SistemaTokenService`, sección 8.1). El cliente `servicio-sistema` tiene desactivado el *password grant*: solo puede usar client credentials.

### 6.2 Resource server: cómo valida el token cada servicio

`spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8080/realms/obras-viales` en cada `application.yml` convierte al servicio en un **resource server**: cada pedido tiene que traer `Authorization: Bearer <token>`. Spring Security baja las claves públicas, comprueba la firma, que `iss` coincida y que no esté vencido. Si algo falla: 401.

Dentro de Docker hay una trampa: el token dice `iss = http://localhost:8080/...` (porque se pidió desde afuera), pero dentro de la red `localhost` es el propio contenedor. Solución aplicada (paso 13):
- `KC_HOSTNAME_URL=http://localhost:8080` en Keycloak, para que el `iss` sea siempre ese, se pida desde donde se pida.
- En el perfil docker, `jwk-set-uri: http://keycloak:8080/.../certs`: las claves se bajan por la red interna, y el `issuer-uri` queda solo para comparar el claim.

### 6.3 De roles de Keycloak a autoridades de Spring (`KeycloakConversorRoles`)

Spring Security no sabe dónde pone Keycloak los roles. Por defecto busca un claim `scope`. `KeycloakConversorRoles` implementa `Converter<Jwt, Collection<GrantedAuthority>>`: lee `realm_access.roles` y convierte cada rol en `SimpleGrantedAuthority("ROLE_" + rol)`. Por ejemplo, `PLANIFICADOR` pasa a ser la autoridad `ROLE_PLANIFICADOR`.

¿Por qué el prefijo `ROLE_`? Porque `hasRole('PLANIFICADOR')` en Spring busca la autoridad `ROLE_PLANIFICADOR` (agrega el prefijo solo). Si el conversor no lo pusiera, todo daría 403. Se conecta con el `@Bean jwtAuthenticationConverter` (sección 3.3) y la línea `.jwt(jwt -> jwt.jwtAuthenticationConverter(...))` de la cadena.

### 6.4 La cadena de filtros de los servicios (`BaseResourceServerConfig`)

Un **filtro** es código que corre antes (y después) de que el pedido llegue al controlador. Spring Security arma una **cadena** de filtros (`SecurityFilterChain`) a partir de esta configuración:

```java
http.csrf(csrf -> csrf.disable())
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/**").permitAll()
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
        .anyRequest().authenticated())
    .oauth2ResourceServer(o -> o
        .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint())
        .accessDeniedHandler(new ProblemDetailAccessDeniedHandler())
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
```

Línea por línea, para la defensa:
- **CSRF desactivado.** CSRF es un ataque que aprovecha que el navegador manda las **cookies** solo. Esta API no usa cookies de sesión: el token viaja en un header que el navegador no agrega por su cuenta. La protección no aporta nada y molestaría a los POST.
- **STATELESS.** El servidor no guarda sesión (no hay `JSESSIONID`): cada pedido se autentica con su token. Así cualquier instancia puede atender cualquier pedido.
- **`permitAll` de `/actuator/**`**: el health check de Docker tiene que poder consultar sin token. Swagger también queda abierto para poder ver el contrato.
- **`anyRequest().authenticated()`**: todo lo demás exige token válido. El *rol* se controla en cada método con `@PreAuthorize`.
- `@EnableWebSecurity` activa esta configuración. `@EnableMethodSecurity` activa `@PreAuthorize`.

### 6.5 Autorización por método: `@PreAuthorize`

```java
@PreAuthorize("hasAnyRole('" + Roles.PLANIFICADOR + "', '" + Roles.SISTEMA + "')")
```

- `@PreAuthorize` recibe una expresión **SpEL** (*Spring Expression Language*) que Spring evalúa antes de ejecutar el método. Si da falso, lanza `AccessDeniedException` y el advice responde 403.
- Cómo funciona por dentro: `@EnableMethodSecurity` hace que Spring envuelva el controlador en un **proxy**, un objeto intermedio que intercepta la llamada, evalúa la expresión y recién después llama al método real.
- Las expresiones se arman concatenando las constantes de `Roles` (commons): si alguien escribe mal un rol, no compila. En Recursos están juntas en `Permisos.java` (`SOLO_CAPATAZ`, `LECTURA`).
- **Detalle que conviene saber**: Spring resuelve y valida el `@RequestBody` (con `@Valid`) **antes** de llegar al proxy de `@PreAuthorize`. Un rol sin permiso que manda un cuerpo inválido recibiría 400 y no 403. Por eso el gateway también filtra por rol (sección 6.6): ese pedido ya no llega al servicio.

### 6.6 El gateway: autenticación y reglas gruesas por ruta y método

`api-gateway/.../security/SecurityConfig.java` hace lo mismo que la base (JWT, sin sesiones, sin CSRF) y además decide por ruta y verbo HTTP:

```java
.requestMatchers("/actuator/health/**").permitAll()
.requestMatchers("/api/*/internos/**").hasRole(SISTEMA)
.requestMatchers(HttpMethod.GET,  "/api/obras/**").hasAnyRole(PLANIFICADOR, CAPATAZ, AUDITOR)
.requestMatchers(HttpMethod.POST, "/api/obras/**").hasRole(PLANIFICADOR)
...
.requestMatchers("/api/**").denyAll()
```

- Las reglas se evalúan **en orden** y gana la primera que coincide. Por eso `internos` va antes que las de cada servicio, y `denyAll` al final: cualquier combinación de ruta y verbo que ningún servicio expone recibe 403 sin llegar a los servicios.
- Cada regla deja pasar la **unión** de los roles que algún endpoint de ese servicio y verbo acepta. El permiso fino sigue en `@PreAuthorize`. Esto es **defensa en profundidad**: si alguien saltara el gateway (no puede desde afuera, porque los servicios no se publican), igual chocaría con la seguridad del servicio.
- El gateway no usa commons (decisión del paso 6: el borde es independiente), por eso repite el conversor de roles y las clases de `ProblemJson`.

**Enrutamiento** (`application.yml` del gateway): Spring Cloud Gateway MVC, bajo `spring.cloud.gateway.server.webmvc.routes`. Cada ruta tiene un `id`, una `uri` destino y un predicado `Path=/api/obras/**`. Trampa conocida: sin el nivel `server.webmvc`, las rutas se ignoran y todo da 404.

### 6.7 Las tres pruebas de seguridad que pide el enunciado

Para cada endpoint: sin token → 401; con rol insuficiente → 403; con el rol correcto → 2xx. Están automatizadas:
- en los servicios, con `MockMvc` y `jwt().authorities(new SimpleGrantedAuthority("ROLE_X"))` (de `spring-security-test`), que simula un token con esas autoridades sin Keycloak (`SeguridadEndpointsTest` en Operaciones, `CatalogoControllerSeguridadTest` y `ReservaControllerSeguridadTest` en Recursos, los `*ControllerTest` de Obras);
- en el gateway, con `ReglasPorRolGatewayTests` y `SeguridadGatewayTests`;
- en la colección, en la carpeta `02-Seguridad`.

La matriz completa está en `docs/matriz-permisos.md`.

---

## 7. Persistencia: entidades, repositorios, transacciones y migraciones

### 7.1 Entidades JPA

**JPA** (*Jakarta Persistence API*) es el estándar para mapear clases a tablas; **Hibernate** es la implementación que trae Spring Boot. Ejemplo real (`ms-operaciones/.../modelo/OrdenTrabajo.java`, resumido):

```java
@Entity @Table(name = "ordenes_trabajo")
public class OrdenTrabajo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "estado", nullable = false, length = 40) private EstadoOrden estado;
    @Version private Long version;
    @PrePersist protected void onCreate() { creado = LocalDateTime.now(); actualizado = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { actualizado = LocalDateTime.now(); }
}
```

- `@Entity` y `@Table`: la clase es una tabla. `@Column` fija el nombre, si admite null y el largo.
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`: la clave la genera la base (`BIGSERIAL` en las migraciones). Con `AUTO`, Hibernate esperaría una secuencia propia y la validación del esquema fallaría.
- `@Enumerated(EnumType.STRING)`: guarda `"HABILITADA"` y no un número. Si se reordenara el enum, los datos viejos seguirían significando lo mismo.
- `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cuadrilla_id")` (en `Operario`, `Maquina`, `Reserva`): la relación con la cuadrilla. `LAZY` significa que la cuadrilla no se lee de la base hasta que alguien la usa.
- **`@Version` (bloqueo optimista)**: cada vez que se guarda la orden, Hibernate suma 1 a `version` y agrega `WHERE version = <la que leí>` al `UPDATE`. Si dos procesos leyeron la misma orden y los dos la quieren guardar, el segundo no actualiza ninguna fila y Hibernate lanza `ObjectOptimisticLockingFailureException`. Protege contra, por ejemplo, el job y el PLANIFICADOR habilitando la misma orden a la vez. (En una revisión, un código del job que guardaba una copia vieja de la orden chocaba justamente con esto, y así se detectó el error.)
- `@PrePersist` y `@PreUpdate`: métodos que Hibernate llama solo antes del primer `INSERT` y antes de cada `UPDATE`. Se usan para las fechas de creación y modificación.

### 7.2 Repositorios de Spring Data

Un repositorio es una **interfaz** sin implementación:

```java
public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    Optional<Reserva> findByOrdenTrabajoId(Long ordenTrabajoId);
    @Query("""
        SELECT COUNT(r) > 0 FROM Reserva r
        WHERE r.cuadrilla.id = :cuadrillaId AND r.fechaReserva = :fecha
          AND (r.estado = 'CONFIRMADA' OR (r.estado = 'PROVISORIA' AND r.fechaVencimientoTtl > :ahora))
    """)
    boolean existeReservaSolapada(@Param("cuadrillaId") Long cuadrillaId, @Param("fecha") LocalDate fecha,
                                  @Param("ahora") LocalDateTime ahora);
    @Modifying
    @Query("UPDATE Reserva r SET r.estado = 'EXPIRADA' WHERE r.estado = 'PROVISORIA' AND r.fechaVencimientoTtl <= :ahora")
    int expirarReservasVencidas(@Param("ahora") LocalDateTime ahora);
}
```

- Al arrancar, Spring Data crea un objeto que implementa la interfaz (un proxy). `extends JpaRepository<Reserva, Long>` regala `save`, `findById`, `findAll` y `delete`.
- **Consultas derivadas del nombre**: Spring lee el nombre del método y arma la consulta. `findByOrdenTrabajoId` → `WHERE orden_trabajo_id = ?`. `findByFechaProgramadaAndEstado` → dos condiciones con AND. `countByFaseIdAndVigenteTrue` → cuenta las suspensiones vigentes de una fase (RN-12).
- **`@Query`**: cuando el nombre no alcanza, se escribe **JPQL**, que usa nombres de clases y atributos (`Reserva r`, `r.cuadrilla.id`), no de tablas. `existeReservaSolapada` es RN-05.
- **`@Modifying`**: obligatorio para un `UPDATE` o `DELETE` en `@Query`. Devuelve cuántas filas cambió. `expirarReservasVencidas` lo usa el job de expiración.
- `@Param` une `:fecha` con el parámetro del método.

### 7.3 `@Transactional`

Una **transacción** agrupa varias operaciones en la base: o se confirman todas (*commit*) o ninguna (*rollback*).

- `@Transactional` sobre un método público de un `@Service` hace que Spring lo envuelva en un **proxy**: al entrar abre la transacción, al salir normalmente hace commit, y si sale una `RuntimeException` hace rollback.
- `@Transactional(readOnly = true)` en las consultas: avisa que no hay escrituras, y Hibernate se ahorra revisar cambios.
- **Ejemplo importante, `habilitarOrden`**: es `@Transactional`. Si la confirmación de la reserva en Recursos falla al final, la excepción hace rollback de todo lo local (estado, reserva guardada en la orden): la orden queda como estaba y se puede reintentar. Lo remoto no se deshace solo; para eso está la saga y el TTL (sección 13).
- **`@Transactional(noRollbackFor = ConflictoEstadoException.class)` en `ReservaService.confirmarReserva`**: si la reserva venció, el método la guarda como EXPIRADA y **después** lanza 409. Sin `noRollbackFor`, el rollback borraría también el cambio a EXPIRADA.
- **`SuspensionService` no tiene `@Transactional` propio** a propósito: corre dentro de la transacción de `habilitarOrden` (o del rechazo del CAPATAZ). Si un paso de Obras falla a mitad de la saga, se deshace lo local junto con todo lo demás.
- **Trampa de la autoinvocación**: el proxy solo actúa cuando la llamada viene **de afuera** del objeto. Si un método llama a otro `@Transactional` de la misma clase (`this.otroMetodo()`), la anotación del segundo no se aplica. Pasó en una versión del job; por eso hoy el job solo delega en otro bean (`HabilitacionDiariaService`).

### 7.4 Flyway y la validación del esquema

- **Flyway** versiona la base: cada archivo `V<n>__descripcion.sql` en `src/main/resources/db/migration` se aplica una sola vez, en orden, y queda anotado en la tabla `flyway_schema_history` con una suma de control (*checksum*).
- **Nunca se edita una migración ya aplicada**: Flyway compara el checksum al arrancar y, si cambió, no arranca (`Migration checksum mismatch`). Por eso los cambios van en versiones nuevas. Ejemplos del proyecto: Recursos `V3__quitar_mantenimientos.sql` y `V4__quitar_rn04_y_restos_del_recorte.sql`; Operaciones `V6__recorte_alcance.sql`.
- Las migraciones también cargan los **datos de demo** (obras, cuadrillas, umbrales y las tres órdenes del escenario).
- **`spring.jpa.hibernate.ddl-auto: validate`**: Hibernate no crea ni modifica tablas, solo comprueba al arrancar que las entidades coinciden con el esquema que dejó Flyway. Si no coinciden, la aplicación no arranca. Así el esquema lo controla una sola herramienta.

---

## 8. Comunicación entre servicios

### 8.1 El token de la cuenta SISTEMA (`SistemaTokenService`)

Cuando Operaciones llama a Obras o a Recursos, no hay una persona detrás: usa la cuenta SISTEMA.
- `obtenerToken()` es `synchronized` (dos hilos no piden token a la vez) y guarda el token en memoria. Pide uno nuevo solo si no hay o si faltan menos de 30 segundos para que venza.
- Lo pide con *client credentials* (`grant_type=client_credentials`, `client_id`, `client_secret`), con timeouts de 3 s y 5 s.
- Reintenta hasta 3 veces con espera creciente (500 ms, 1 s) si Keycloak no responde, porque al arrancar el stack Keycloak puede tardar. No reintenta un error 4xx: credenciales mal configuradas no se arreglan reintentando.
- La espera se recibe como una interfaz funcional (`Espera`) para que los tests no tengan que dormir de verdad.

### 8.2 El interceptor (`SistemaRequestInterceptor`)

Implementa `ClientHttpRequestInterceptor`: Spring lo llama **antes de mandar cada pedido** del `sistemaRestClient`. Hace dos cosas:
1. copia el correlation-id del MDC (sección 11) al header `X-Correlation-Id`, para que el pedido se pueda seguir en los logs del otro servicio;
2. pone `Authorization: Bearer <token de SISTEMA>`.

### 8.3 Los clientes y cómo traducen los errores (`ObrasHttpClient`, `RecursosHttpClient`)

Cada cliente implementa una interfaz (`ObrasClient`, `RecursosClient`); los servicios dependen de la interfaz y en los tests se reemplaza por un mock. Los dos traducen lo que responde el otro servicio a excepciones propias, para que el error llegue al usuario con sentido:
- 404 → `RecursoNoEncontradoException`; 409 → `ConflictoEstadoException`; 422 → `ReglaNegocioException` **con el mismo `codigoRegla`** (por ejemplo, el RN-07 de Recursos llega al PLANIFICADOR como RN-07);
- error de red, timeout o 5xx → `ServicioNoDisponibleException` → 503. En la habilitación, la orden queda como estaba (PROGRAMADA) y el job la reintenta.

**Excepción a la regla de SISTEMA**: `ObrasHttpClient.tramosDeObra` (filtro `?obraId=` de `GET /ordenes`) usa el **token del usuario** que hizo el pedido, que saca del `SecurityContextHolder`, con un `RestClient` sin el interceptor de SISTEMA (que lo pisaría). Así Obras decide con los permisos de esa persona.

---

## 9. Integraciones externas y resiliencia

### 9.1 Open-Meteo (clima), en ms-operaciones

`OpenMeteoService.obtenerCondicionesClimaticas(lat, lon, fecha)` arma la URL con `UriComponentsBuilder`:
`?latitude=..&longitude=..&hourly=temperature_2m,precipitation_probability,soil_moisture_0_to_1cm&timezone=auto&start_date=<fecha>&end_date=<fecha>`

- Pide los datos **por hora** de la fecha de la orden (no el clima de este momento). `timezone=auto` hace que las horas vengan en la hora local del tramo.
- Busca las horas de la ventana de trabajo (desde `hora-inicio-jornada: 8`, durante `ventana-ejecucion-horas: 6`, es decir de 08:00 a 13:00) y toma el **peor** valor: la mayor humedad del suelo, la mayor probabilidad de lluvia y la menor temperatura. Si una sola hora no cumple, no se trabaja.
- La humedad del suelo está en **m³/m³** (fracción de agua en volumen: 0,15 es 15 %), no en porcentaje. El umbral también.
- Si falta cualquier dato de la ventana, **no** inventa un valor: lanza error. Eso termina en PENDIENTE_CONFIRMACION_CLIMATICA (RN-10).
- `Coordenada` (commons) valida los rangos antes de llamar.
- Está detrás de la interfaz `InterfazClimaService` (la propuesta la llamaba `ProveedorClima`): se podría cambiar de proveedor sin tocar `habilitarOrden`.

### 9.2 Resilience4j: reintentos y circuit breaker

**Resilience4j** es una librería de tolerancia a fallos. Sus anotaciones funcionan como `@Transactional`: Spring envuelve el bean en un proxy (AOP, programación orientada a aspectos) que intercepta la llamada.

En `OpenMeteoService`:

```java
@Retry(name = "openMeteoRetry")
@CircuitBreaker(name = "openMeteoCircuitBreaker", fallbackMethod = "obtenerCondicionesFallback")
public DatosClimaDTO obtenerCondicionesClimaticas(Double latitud, Double longitud, LocalDate fecha)
```

Configuración (`application.yml` de Operaciones):
- `openMeteoRetry`: `max-attempts: 3`, `wait-duration: 1s`. Si falla, se reintenta hasta 3 veces con 1 s entre intentos (fallas pasajeras de red).
- `openMeteoCircuitBreaker`: `sliding-window-size: 5`, `failure-rate-threshold: 50`, `wait-duration-in-open-state: 10s`.

**Cómo funciona el circuit breaker** (para explicarlo en la defensa):
- **Cerrado** (normal): deja pasar las llamadas y cuenta el resultado de las últimas 5.
- Si falla el 50 % o más, pasa a **abierto**: durante 10 s ni siquiera intenta llamar a Open-Meteo y va directo al *fallback*. Así no se espera el timeout en cada orden mientras la API está caída.
- Pasados los 10 s, queda **semiabierto**: deja pasar unas pocas llamadas de prueba. Si andan, vuelve a cerrado; si no, a abierto.
- El **fallback** es un método con los mismos parámetros más un `Throwable` al final. Acá registra el error y lanza una excepción; `habilitarOrden` la atrapa y deja la orden en PENDIENTE_CONFIRMACION_CLIMATICA. Después el CAPATAZ decide con `PATCH /ordenes/{id}/resolucion-climatica`.

Timeouts del cliente HTTP: 3 s para conectar y 5 s para leer (`construirRestClient`). Sin timeouts, una API colgada dejaría el hilo esperando para siempre.

### 9.3 OSRM (rutas), en ms-recursos

`OsrmProveedorRutas.calcularRuta(origenLat, origenLon, destinoLat, destinoLon)`:
- Llama a `/route/v1/driving/{lon,lat};{lon,lat}?overview=false`. **OSRM espera longitud primero**; `Coordenada.toOsrmString()` arma el par en ese orden.
- Usa `RestTemplate` (el cliente HTTP clásico de Spring) con timeouts de 2 s y 5 s y un interceptor que agrega el `User-Agent` propio, porque la política del servidor público pide identificar al cliente.
- Convierte la duración (segundos) a minutos y la multiplica por `factor-correccion: 1.3`, porque el perfil `driving` no considera tránsito.
- El servidor público está en `app.osrm.url`. Pasar a una instancia propia (como sugiere la guía de la cátedra, con el mapa de Argentina) es cambiar esa clave.

Tres anotaciones sobre el mismo método:

1. **`@Cacheable(value = "rutas", key = "{#origenLat, #origenLon, #destinoLat, #destinoLon}")`**: antes de ejecutar el método, Spring busca en la caché `rutas` una entrada con esa clave (las cuatro coordenadas). Si está, devuelve el resultado guardado **sin llamar a OSRM**; si no, ejecuta y guarda. La activa `@EnableCaching` en `MsRecursosApplication`, y el almacenamiento es el `@Bean cacheManager` (un mapa en memoria).
   - **Vencimiento**: `ConcurrentMapCacheManager` no tiene TTL por entrada. `VencimientoCacheRutas` es un `@Scheduled(fixedRateString = "${app.osrm.cache-ttl:PT6H}")` que pide el `CacheManager` y vacía `rutas` cada 6 horas. Ninguna ruta guardada tiene más de 6 h. `PT6H` es el formato ISO-8601 de duraciones.
2. **`@RateLimiter(name = "osrmLimiter")`**: `limit-for-period: 1`, `limit-refresh-period: 1s`, `timeout-duration: 3s`. Como máximo 1 pedido por segundo, que es la política del servidor público. Si no hay permiso, espera hasta 3 s y, si sigue sin permiso, va al fallback.
3. **`@CircuitBreaker(name = "osrmBreaker")`**: igual que el de Open-Meteo (ventana de 5, 50 %, 10 s abierto, 2 llamadas de prueba en semiabierto).

**Fallback (`fallbackCalcularRuta`)**: si OSRM falla, está limitado o el circuito está abierto, calcula la distancia en línea recta con la **fórmula de Haversine** (distancia sobre la esfera terrestre, radio 6371 km), la multiplica por el factor de desvío 1,3, la convierte a minutos a 50 km/h (mínimo 5 min) y devuelve la ruta con `fuente = "ESTIMADA"`. La reserva guarda esa fuente, así se sabe que el tiempo es aproximado.

---

## 10. Tareas programadas

`@EnableScheduling` (en `MsOperacionesApplication` y `MsRecursosApplication`) activa el planificador de Spring. Después, cualquier método de un bean con `@Scheduled` se ejecuta solo:

| Tarea | Anotación | Qué hace |
|---|---|---|
| `JobHabilitacionDiaria.ejecutarEvaluacionDiaria` (Operaciones) | `@Scheduled(cron = "${app.jobs.habilitacion.cron:0 0 6 * * ?}")` | Todos los días a las 6:00 habilita las órdenes del día: llama a `HabilitacionDiariaService.habilitarDelDia(LocalDate.now())` |
| `ExpiracionReservasJob.expirarVencidas` (Recursos) | `@Scheduled(fixedDelayString = "${app.reservas.expiracion-ms:60000}")` | Cada 60 s pasa a EXPIRADA las reservas PROVISORIAS vencidas |
| `VencimientoCacheRutas.vaciar` (Recursos) | `@Scheduled(fixedRateString = "${app.osrm.cache-ttl:PT6H}")` | Vacía la caché de rutas de OSRM |

- **cron** tiene seis campos (segundo, minuto, hora, día del mes, mes, día de la semana). `0 0 6 * * ?` = a las 6:00:00 de cualquier día.
- **`fixedDelay`** cuenta desde que **terminó** la corrida anterior; **`fixedRate`**, desde que **empezó**.
- **Idempotencia**: correr el job dos veces no hace daño. Solo toma órdenes PROGRAMADA o RECURSOS_ASIGNADOS de la fecha; las ya habilitadas no se tocan. Por eso, si Operaciones se cae a mitad de camino, la próxima corrida retoma.
- **Una orden que falla no frena el lote**: `HabilitacionDiariaService` envuelve cada orden en `try/catch`, registra el motivo y sigue. El job también atrapa fallos generales (por ejemplo, la base caída) para no tirar el planificador.
- **Zona horaria**: el contenedor de Operaciones tiene `TZ=America/Argentina/Buenos_Aires` en el compose. Sin eso, `LocalDate.now()` daría la fecha UTC y a las 21 h de Argentina el job procesaría las órdenes del día siguiente.

---

## 11. Correlation-id: seguir un pedido por los tres servicios

**Para qué sirve**: un pedido del usuario genera llamadas a varios servicios. Con un mismo identificador en todas las líneas de log se puede reconstruir qué pasó.

**En el gateway** (`api-gateway/.../filter/CorrelationIdFilter.java`):
- Hereda de `OncePerRequestFilter` (un filtro que Spring garantiza que corre una sola vez por pedido) y tiene `@Order(Ordered.HIGHEST_PRECEDENCE)`, así que corre antes que todos.
- **Siempre genera un UUID nuevo** e ignora el que mande el cliente: el id del borde es confiable.
- Lo guarda en el **MDC** y lo pone en la respuesta.
- El request de Servlet es de solo lectura, así que lo envuelve (`HttpServletRequestWrapper`) para que, al reenviarlo, el servicio reciba el header con el id nuevo.
- También envuelve la respuesta, para que no salga el header repetido (el servicio también lo devuelve y el proxy lo copiaría).

**En cada servicio** (`obras-commons/.../filter/CorrelationIdFilter.java`): lee `X-Correlation-Id`; si no viene (por ejemplo, el job), genera uno. Lo pone en el MDC y en la respuesta, y lo saca del MDC en un `finally`.

**MDC** (*Mapped Diagnostic Context*, de SLF4J/Logback): un mapa asociado al **hilo** que atiende el pedido. Todo log de ese hilo puede imprimir sus valores. Se limpia en el `finally` porque los hilos de Tomcat se reutilizan: sin limpiar, el pedido siguiente heredaría el id.

**En los logs**: cada `application.yml` tiene `logging.pattern.correlation: "[%X{correlationId:-}] "`. `%X{correlationId}` imprime el valor del MDC (y `-` si no hay).

**Entre servicios**: el interceptor de commons (sección 8.2) copia el id del MDC al pedido saliente. El cuerpo de los errores también trae `correlationId`, así el usuario puede informarlo.

## 12. Las reglas de negocio, una por una

Once reglas (RN-04, la certificación del operario, se quitó el 2026-11-16; los demás números no cambian para que coincidan con el cuestionario). Cada una tiene test automático y request en la colección (`docs/trazabilidad-reglas.md`).

### RN-01: una fase no empieza si la anterior no está completa (Obras, y Operaciones al habilitar)
- **Dónde**: `FaseService.exigirAnteriorCompletada`, llamado desde `registrarAvance` cuando la fase está PENDIENTE (el primer avance es el que la inicia).
- **Cómo**: si la fase tiene `faseAnterior` y esa no está COMPLETADA, lanza `ConflictoEstadoException` → 409. Además, `contexto-ejecucion` informa `faseAnteriorCompletada`, y `habilitarOrden` corta con 409 antes de reservar recursos: no tiene sentido reservar una cuadrilla para una fase que no puede empezar.
- **Test**: `FaseServiceAvanceTest`, `FaseServiceTest`, `OrdenTrabajoServiceHabilitacionTest`.

### RN-02: reprogramación en cascada (Obras)
- **Dónde**: `FaseService.reprogramarFase` y `desplazarEnCascada`.
- **Cómo**: calcula cuántos días se corre la fase (`ChronoUnit.DAYS.between`) y le suma esos días a ella y, **recursivamente**, a cada fase que depende de ella (`findByFaseAnteriorId`). Un `Set` de visitadas detecta ciclos (422 RN-02). Rechaza una fecha anterior al fin de la fase previa (422 RN-02) y una fase COMPLETADA (409). Si la fase estaba EN_RIESGO, vuelve a PENDIENTE: así se resuelve el riesgo de RN-12.
- **Quién la usa**: el PLANIFICADOR (`PATCH /fases/{id}/programacion`) y la saga de suspensión con el token SISTEMA.
- **Test**: `FaseServiceTest`, `FaseServiceAvanceTest`.

### RN-03: materiales según la superficie (Obras)
- **Dónde**: `MaterialService.calcularMateriales`.
- **Cómo**: para el tipo de fase busca en `consumos_material` cuánto se usa por m² y multiplica por la superficie del tramo, con `BigDecimal` redondeado a 2 decimales (`BigDecimal` en lugar de `double` para no arrastrar errores de punto flotante en cantidades). Superficie menor o igual a 0: 422. Ejemplo del seed: tramo de 5000 m², fase BASE con 0,25 m³/m² de piedra → 1250 m³.
- **No se guarda**: se calcula al consultar, así siempre coincide con la superficie actual.
- **Test**: `MaterialServiceTest`, `FaseControllerLecturaTest`.

### RN-05: sin solapamiento de cuadrilla (Recursos)
- **Dónde**: `ReservaRepository.existeReservaSolapada` (JPQL, sección 7.2), primera regla de `ReservaService.evaluarCuadrilla`.
- **Cómo**: una cuadrilla no es elegible si ese día ya tiene una reserva CONFIRMADA o una PROVISORIA que todavía no venció. Una provisoria vencida ya no bloquea, aunque el job todavía no la haya marcado EXPIRADA.
- **Test**: `ReservaServiceReglasTest` (`rn05_...`).

### RN-06: composición mínima de la cuadrilla por tipo de trabajo (Recursos)
- **Dónde**: `evaluarCuadrilla`, con la tabla `composicion_minima_trabajo` (V2).
- **Cómo**: cuenta los operarios ACTIVOS de la cuadrilla y, si el trabajo pide máquina, las máquinas DISPONIBLES de ese tipo. Ejemplo de la V2: CARPETA_ASFALTICA pide 3 operarios y 1 PAVIMENTADORA; BASE, 2 operarios y 1 RODILLO_COMPACTADOR; RELEVAMIENTO, 2 operarios y ninguna máquina. Un tipo de trabajo sin composición configurada: 422 RN-06.
- **Test**: `ReservaServiceReglasTest` (`rn06_...`).

### RN-07: viaje de la cuadrilla al tramo (Recursos, con OSRM)
- **Dónde**: `evaluarCuadrilla`, después de RN-05 y RN-06 (para no gastar una consulta a OSRM en una cuadrilla que igual no sirve).
- **Cómo**: pide a `ProveedorRutas` el tiempo desde la última ubicación de la cuadrilla (la del último check-in del CAPATAZ) hasta el tramo. Rechaza si supera el máximo (90 min en `configuracion_desplazamiento`) o si la ida y vuelta no entra en la jornada (480 min).
- **Ejemplo**: la orden 3 del seed está en un tramo de Río Cuarto: 215 min contra un máximo de 90 → 422 RN-07 y la orden queda PROGRAMADA con el motivo.
- **Test**: `ReservaServiceReglasTest` (`rn07_...`).

### RN-08: ventana de transporte de la mezcla y hora de salida del camión (Recursos, con OSRM)
- **Dónde**: `ReservaService.proponerYReservar`, paso 4, solo si el tipo de trabajo tiene `requiere_mezcla` (CARPETA_ASFALTICA y LOSA_HORMIGON).
- **Cómo**: la cuadrilla llega a las **08:00 + su viaje**. Para cada planta activa pide a OSRM el viaje planta → tramo y elige la primera que llegue dentro de la ventana (60 min). **Salida del camión = llegada de la cuadrilla − viaje de la mezcla − margen (15 min)**. Si ninguna planta llega: 422 RN-08.
- **Ejemplo real** (prueba del 13/11): Alfa llega 08:20, la mezcla tarda 29 min → 08:20 − 29 − 15 = **07:36**.
- **Por qué va después de elegir la cuadrilla**: la mezcla tiene que llegar cuando llega la gente, no antes, porque se enfría esperando.
- **Test**: `ReservaServiceReglasTest` (`rn08_...`).

**Qué regla se informa si ninguna cuadrilla sirve.** `ORDEN_REGLAS = ["RN-05", "RN-06", "RN-07"]`. Se informa la regla de la cuadrilla que llegó más lejos en esa secuencia (la que estuvo más cerca de servir), y el mensaje lista el motivo de **cada** cuadrilla. Ejemplo: "Alfa: RN-07, el viaje es de 215 min y el máximo es 90; Beta Lejana: RN-06, tiene 0 operarios" → `codigoRegla: RN-07`.

### RN-09: umbrales climáticos por tipo de trabajo (Operaciones)
- **Dónde**: `habilitarOrden` compara el resultado de `OpenMeteoService` con `umbrales_climaticos`.
- **Cómo**: cumple si humedad del suelo ≤ máximo, probabilidad de lluvia ≤ máximo y temperatura ≥ mínimo, todo con el peor valor de 08:00 a 13:00 de la fecha de la orden. Cada evaluación se guarda en `evaluaciones_climaticas` (historial: `GET /ordenes/{id}/evaluaciones-climaticas`).
- **Configurable**: el ADMIN cambia los umbrales con `PUT /api/operaciones/umbrales-climaticos/{tipoTrabajo}`. En la demo se usa para forzar una suspensión (umbrales imposibles) o el camino feliz (umbrales permisivos), porque el clima real no se controla.
- **Test**: `OrdenTrabajoServiceHabilitacionTest`, `OpenMeteoServiceTest`.

### RN-10: sin dato climático confiable, la orden no se habilita sola (Operaciones)
- **Dónde**: el `try/catch` alrededor del clima en `habilitarOrden`, más el fallback del circuit breaker.
- **Cómo**: si Open-Meteo no responde (después de 3 intentos), el circuito está abierto o falta algún dato de la ventana, la orden queda **PENDIENTE_CONFIRMACION_CLIMATICA** y **conserva la reserva**. El CAPATAZ decide con `PATCH /ordenes/{id}/resolucion-climatica`: HABILITAR confirma la reserva; SUSPENDER corre la saga como una suspensión por clima.
- **Test**: `OrdenTrabajoServiceHabilitacionTest`, `EjecucionServiceTest`.

### RN-11: solo transiciones de estado válidas (Operaciones)
- **Dónde**: `habilitarOrden` (exige PROGRAMADA o RECURSOS_ASIGNADOS) y `EjecucionService.exigirEstado` (iniciar y rechazar exigen HABILITADA; avances, EN_EJECUCION; resolver el clima, PENDIENTE_CONFIRMACION_CLIMATICA).
- **Cómo**: si el estado no es el esperado lanza `TransicionInvalida`, que hereda de `ReglaNegocioException` con código RN-11 → 422. Ejemplo: intentar habilitar una orden COMPLETADA.
- No hay un endpoint para cambiar el estado a mano: se quitó porque permitía saltear la reserva y el clima. Cada estado se alcanza solo por la acción que corresponde.
- **Test**: `EjecucionServiceTest`, `OrdenTrabajoServiceHabilitacionTest`.

### RN-12: tres suspensiones ponen la fase en riesgo (Operaciones y Obras), simplificada con el recorte
- **Dónde**: `SuspensionService.suspender`.
- **Cómo**: cada suspensión se guarda con `vigente = true`. Se cuentan las vigentes de la fase (`countByFaseIdAndVigenteTrue`). Si llegan al límite (3, en la tabla `configuracion_suspensiones`), en lugar de reprogramar se llama a Obras para pasar la fase a **EN_RIESGO**, y la orden queda SUSPENDIDA sin orden nueva. Con la fase en riesgo, Obras rechaza avances (409).
- **Decisión del PLANIFICADOR**: reprograma la fase (`PATCH /fases/{id}/programacion`, vuelve a PENDIENTE) y crea la orden nueva (`POST /ordenes`). `crearOrden` llama a `cerrarSuspensionesDeLaFase`: las órdenes SUSPENDIDA de esa fase pasan a REPROGRAMADA y las suspensiones quedan no vigentes (el conteo vuelve a cero; el historial se conserva).
- **Test**: `SuspensionServiceTest`, `OrdenTrabajoServiceCrearOrdenTest`.

---

## 13. Estados, reservas y la saga

### 13.1 Máquina de estados de la orden

```
PROGRAMADA ──reserva──► RECURSOS_ASIGNADOS ──► EVALUACION_CLIMATICA ──cumple──► HABILITADA ──inicio──► EN_EJECUCION ──avance 100 %──► COMPLETADA
                                                    │                                │
                                                    ├─sin dato──► PENDIENTE_CONFIRMACION_CLIMATICA ──CAPATAZ──► HABILITADA o suspensión
                                                    └─no cumple──► SUSPENDIDA ──saga──► REPROGRAMADA (hay orden nueva)
                                                                               └─3.ª suspensión: queda SUSPENDIDA (fase en riesgo)
HABILITADA ──rechazo del CAPATAZ──► SUSPENDIDA ──saga──► REPROGRAMADA
```

El estado se guarda como texto (`@Enumerated(EnumType.STRING)`) y cada cambio pasa por el bloqueo optimista (`@Version`).

### 13.2 La reserva y su vencimiento (TTL)

Estados de una reserva (Recursos): **PROVISORIA** → **CONFIRMADA**, **LIBERADA** o **EXPIRADA**; y CONFIRMADA → LIBERADA.
- "Proponer y reservar" crea una PROVISORIA con `fecha_vencimiento_ttl = ahora + 30 min` (TTL, *time to live*). Mientras no vence, bloquea a la cuadrilla ese día (RN-05).
- **Por qué provisoria**: entre reservar y decidir el clima pasa tiempo. Si Operaciones se cae en el medio, nadie confirma ni libera; la reserva vence sola a los 30 min y la cuadrilla vuelve a estar libre. Es la compensación automática del sistema distribuido.
- **Confirmar** (`PATCH .../{id}/confirmacion`): si ya está CONFIRMADA devuelve la misma (idempotente: Operaciones puede reintentar); si no es PROVISORIA, 409; si venció, la guarda EXPIRADA y responde 409 (por eso el `noRollbackFor`, sección 7.3).
- **Liberar** (`PATCH .../{id}/liberacion`): PROVISORIA o CONFIRMADA → LIBERADA; si ya estaba LIBERADA o EXPIRADA, la devuelve igual (idempotente).
- **Idempotencia de "proponer"**: `orden_trabajo_id` es único en `reservas`. Si la orden ya tiene una reserva que bloquea recursos, se devuelve esa; si la anterior venció o se liberó, se reutiliza la misma fila con una evaluación nueva.
- El job `ExpiracionReservasJob` pasa a EXPIRADA las vencidas cada 60 s, para que el estado guardado refleje la realidad.

### 13.3 La saga de suspensión

Una **saga** es una transacción que abarca varios servicios: como no se puede hacer un commit que incluya tres bases distintas, se hace una secuencia de pasos, cada uno con su forma de compensar si algo falla. Acá la orquesta Operaciones (`SuspensionService.suspender`):

1. **Liberar la reserva en Recursos.** Si Recursos no responde, se registra en el log y se sigue: la reserva vence sola por TTL.
2. **Registrar la suspensión** (tabla `suspensiones`, con motivo CLIMA o RECHAZO y el detalle de los valores).
3. **Contar las suspensiones vigentes de la fase** (RN-12).
4. **Debajo del límite**: calcular la fecha nueva = la mayor entre "fecha de la orden + 1 día" y "fin planificado de la fase anterior" (si no, Obras la rechazaría por RN-02); pedir a Obras que reprograme la fase en cascada; crear la **orden nueva** PROGRAMADA para la fecha que devolvió Obras; dejar la vieja REPROGRAMADA.
5. **En el límite**: marcar la fase en riesgo en Obras y dejar la orden SUSPENDIDA.

Si falla un paso de Obras, la excepción hace rollback de todo lo local (la orden vuelve al estado anterior y no queda suspensión registrada); lo único remoto ya hecho es la liberación, que se puede repetir sin daño (es idempotente).

### 13.4 Qué pasa cuando algo se cae (demostración de INT-01)

| Falla | Qué hace el sistema | Dónde |
|---|---|---|
| Open-Meteo no responde | 3 reintentos y circuit breaker; la orden queda PENDIENTE_CONFIRMACION_CLIMATICA con la reserva; decide el CAPATAZ | `OpenMeteoService`, `habilitarOrden` |
| OSRM no responde o limita | Rate limiter y circuit breaker; estimación en línea recta con fuente ESTIMADA | `OsrmProveedorRutas.fallbackCalcularRuta` |
| Recursos u Obras no responde | `ServicioNoDisponibleException` → 503; la orden queda PROGRAMADA y el job la reintenta | `ObrasHttpClient`, `RecursosHttpClient`, `HabilitacionDiariaService` |
| Operaciones se cae a mitad de camino | Las reservas provisorias vencen por TTL; el job es idempotente y retoma las PROGRAMADA y RECURSOS_ASIGNADOS | `ExpiracionReservasJob`, `JobHabilitacionDiaria` |
| Keycloak tarda al arrancar | El token de SISTEMA se reintenta 3 veces | `SistemaTokenService` |

---

## 14. Infraestructura

### 14.1 Dockerfile en dos etapas (por ejemplo `ms-operaciones/Dockerfile`)

```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ms-operaciones -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /build/ms-operaciones/target/ms-operaciones-0.0.1-SNAPSHOT.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- **Dos etapas**: la primera tiene Maven y el JDK y compila; la segunda solo tiene el JRE y el `.jar`. La imagen final es más chica y no lleva el código fuente ni Maven.
- El contexto de build es la **raíz del repo** y se usa `-pl ms-operaciones -am`: `-am` (*also make*) compila también commons y el pom padre, de los que depende el servicio.
- `--mount=type=cache` guarda las dependencias de Maven entre builds.
- `USER app`: el proceso no corre como root dentro del contenedor.

### 14.2 docker-compose.yml

- Seis servicios: `postgres`, `keycloak`, `ms-obras`, `ms-recursos`, `ms-operaciones`, `api-gateway`, en la red `obras-network`.
- **Healthchecks**: Postgres con `pg_isready`; Keycloak y los servicios con `bash` y `/dev/tcp` (las imágenes no traen `curl`): abren una conexión TCP, mandan un `GET /health/ready` o `/actuator/health` y buscan `UP`.
- **`depends_on` con `condition: service_healthy`**: un servicio no arranca hasta que su base y Keycloak estén sanos. Sin eso, Flyway intentaría conectarse antes de que Postgres acepte conexiones.
- **Variables**: `${VAR:?mensaje}` hace que el compose no arranque si falta una variable del `.env`, con un mensaje claro.
- **Publicados**: solo Keycloak (8080) y el gateway (8081); Postgres solo en `127.0.0.1`. Los servicios no tienen `ports:`.
- `SPRING_PROFILES_ACTIVE=docker` activa los `application-docker.yml`.
- Postgres monta `docker/init-db/01-init-schemas.sql`, que se ejecuta solo la primera vez (con el volumen vacío). Por eso, cuando cambian las migraciones de forma incompatible, hay que borrar el volumen: `docker compose down -v`.

### 14.3 Arranque desde cero

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

Con los seis contenedores `healthy`, la colección `coleccion/obras-viales.postman_collection.json` recorre todo (82 requests).

---

## 15. Cómo se prueba

- **Tests unitarios de servicios** con JUnit 5 y **Mockito**: `@ExtendWith(MockitoExtension.class)`, `@Mock` crea objetos simulados de los repositorios y clientes, `@InjectMocks` crea el servicio real pasándole esos mocks, y `when(...).thenReturn(...)` define qué responden. Ejemplo: `ReservaServiceReglasTest` arma cuadrillas, operarios y rutas simuladas y comprueba que cada regla rechaza con su código (casos normales, de límite y de rechazo).
- **Tests de controladores** con `@WebMvcTest(Controlador.class)` y `MockMvc`: levantan solo la capa web (sin base) y simulan pedidos HTTP. Con `jwt().authorities(...)` se simula el token con un rol. Así se prueban los 401, 403 y 2xx de cada endpoint y los 400 de validación.
- **Clientes HTTP** con `MockRestServiceServer`: intercepta el `RestClient` o `RestTemplate` y responde lo que el test indica, sin red (OSRM, Open-Meteo, Obras y Recursos). La propuesta mencionaba WireMock; se usa esto porque cumple lo mismo con una dependencia menos (anotado en DECISIONES).
- **`*ApplicationTests.contextLoads`** (`@SpringBootTest`): arranca la aplicación completa contra un PostgreSQL real. Comprueba que todos los beans se crean, que Flyway aplica las migraciones y que `ddl-auto: validate` acepta las entidades.
- **De punta a punta**: la colección con Newman (`npx newman run ...`) sobre el compose levantado desde cero, y `ms-obras/scripts/e2e-ms-obras.ps1` para Obras.

Para correr los tests: `mvn test` desde la raíz con Postgres arriba y las variables de `.env.example` definidas en la terminal (los `contextLoads` necesitan la base).

---

## 16. Preguntas probables en la defensa (respuestas cortas)

**¿Qué hace `@Bean` y en qué se diferencia de `@Component`?** `@Component` va sobre una clase nuestra y Spring la crea por component scan. `@Bean` va sobre un método de una clase `@Configuration`: Spring lo llama una vez y guarda lo que devuelve. Se usa para objetos de clases ajenas o que hay que configurar, como el `RestClient` con timeouts e interceptor (`SistemaRestClientConfig`) o el `CacheManager` de Recursos.

**¿Cómo sabe Spring qué `RestClient` darle a `ObrasHttpClient`?** Por `@Qualifier("sistemaRestClient")`, que coincide con el nombre del método `@Bean`.

**¿Por qué las clases de commons funcionan en los servicios si están en otro paquete?** Por el archivo `AutoConfiguration.imports` de commons, que Spring Boot lee al arrancar.

**¿Dónde se valida el token? ¿Por qué dos veces?** En el gateway y en cada servicio. Defensa en profundidad: el gateway filtra por ruta y método; el servicio vuelve a validar la firma y controla el rol exacto con `@PreAuthorize`.

**¿Por qué CSRF desactivado y sin sesión?** CSRF ataca con cookies y acá el token va en un header. Sin sesión, cada pedido se autentica solo con su token.

**¿Qué pasa si Open-Meteo está caído?** Tres reintentos; si sigue, el circuit breaker abre y la orden queda PENDIENTE_CONFIRMACION_CLIMATICA conservando la reserva. Decide el CAPATAZ (RN-10). No se inventa un dato.

**¿Qué pasa si OSRM está caído?** Se estima en línea recta (Haversine por 1,3, a 50 km/h) y la ruta queda marcada `ESTIMADA`.

**¿Cómo se evita reservar dos veces la misma cuadrilla?** RN-05 con la consulta `existeReservaSolapada`; además `orden_trabajo_id` es único en `reservas`, y reintentar "proponer" devuelve la misma reserva.

**¿Qué es la saga y quién la orquesta?** La secuencia liberar reserva → registrar suspensión → reprogramar la fase en Obras → crear la orden nueva. La orquesta Operaciones. Si Operaciones se cae, la reserva vence por TTL y el job retoma.

**¿Para qué sirve `@Version`?** Bloqueo optimista: si dos procesos guardan la misma orden a la vez, el segundo falla en lugar de pisar al primero.

**¿Por qué no se edita una migración vieja?** Flyway guarda su suma de control y no arranca si cambió. Los cambios van en una versión nueva (por ejemplo `V4__quitar_rn04_y_restos_del_recorte.sql`).

**¿Por qué cada servicio tiene su base y su usuario?** Para que ninguno lea las tablas de otro: solo se comunican por API, y cada uno puede cambiar su esquema solo.

**¿Por qué `@Transactional` no funciona si un método llama a otro de la misma clase?** Porque la transacción la abre un proxy que solo intercepta llamadas que vienen de afuera del objeto.

**¿Cómo se sigue un pedido entre servicios?** Con el `X-Correlation-Id`: el gateway lo genera, viaja en los headers (el interceptor lo copia), cada servicio lo pone en el MDC y aparece en cada línea de log y en el cuerpo de los errores.

**¿Por qué el job no pisa lo que ya se hizo?** Solo toma órdenes PROGRAMADA o RECURSOS_ASIGNADOS de la fecha; las demás ya avanzaron.

**¿Por qué el clima mira de 08:00 a 13:00 y no la hora actual?** Porque la orden se ejecuta en esa jornada; el clima de "ahora" puede no tener nada que ver. Se toma el peor valor de la ventana: si una hora no cumple, no se trabaja.

**¿Qué se sacó del alcance y por qué?** Las altas y ediciones de recursos y configuración, consultas repetidas, el rol OPERARIO, el cierre con consumo, las fases en riesgo como pantalla aparte, la caché del pronóstico y RN-04. Se fundamentó y se avisó a la cátedra (`docs/aviso-cambios-catedra.md`); el proceso central y sus integraciones quedaron completos.
