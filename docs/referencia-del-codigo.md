# Referencia del código de GOVP

Explica **cada archivo y cada método** del programa, con qué otros archivos y sistemas se comunica, y **cómo funciona cada mecanismo** que usa. Cuando una clase usa algo como `@Bean`, `@Transactional` o `@PreAuthorize`, acá se explica para qué se usa en ese lugar y qué hace Spring por dentro, no solo que "se usa".

Cómo está organizada:
- **Parte 1 (secciones 1 a 9): cómo funcionan los mecanismos**, cada uno con el código real del repositorio: el contenedor de Spring y la inyección de dependencias, los cinco `@Bean` del proyecto, la auto-configuración de `obras-commons`, la capa web, los errores, la seguridad, la persistencia, la comunicación entre servicios, Open-Meteo, OSRM y Resilience4j, las tareas programadas y el correlation-id.
- **Parte 2 (sección 10): glosario de todas las anotaciones** que aparecen en el código, con quién las lee y qué hace con ellas.
- **Parte 3 (secciones 11 a 16): los 32 endpoints y el recorrido archivo por archivo y método por método**, módulo por módulo. Al final de cada módulo hay una tabla de conexiones generada del código (anotaciones, clases que usa, quién la usa y sistemas externos).

Para entender el programa sin bajar al código, leer antes [`vision-general.md`](vision-general.md). Las reglas de negocio en detalle, la saga, los diagramas, el guion de la demo y las preguntas de defensa están en [`guia-del-proyecto.md`](guia-del-proyecto.md).

Estado que describe: `main` del 2026-11-16 (recorte aprobado por la cátedra y RN-04 quitada): 32 endpoints, 11 reglas de negocio, roles ADMIN, PLANIFICADOR, CAPATAZ y AUDITOR más la cuenta de servicio SISTEMA.

---

# Parte 1. Cómo funcionan los mecanismos

## 1. Spring desde cero: los mecanismos que aparecen en todo el código

### 1.1 El contenedor, los beans y la inyección de dependencias

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

### 1.2 Los estereotipos: `@Component`, `@Service`, `@Repository`, `@RestController`

Los cuatro hacen lo mismo para el contenedor (registran la clase como bean); cambian lo que **dicen** y algún comportamiento extra:
- `@Component`: genérico. Se usa para piezas técnicas: `OsrmProveedorRutas`, `ObrasHttpClient`, `CorrelationIdFilter`, los jobs.
- `@Service`: lógica de negocio (`ReservaService`, `FaseService`, `OrdenTrabajoServiceImpl`). Es la capa donde viven las reglas.
- `@Repository`: acceso a datos. En este proyecto los repositorios son interfaces de Spring Data (sección 5.2) y ni siquiera necesitan la anotación, porque Spring Data los detecta por heredar de `JpaRepository`.
- `@RestController` = `@Controller` + `@ResponseBody`: cada método devuelve un objeto que Spring convierte a JSON con Jackson, en vez de buscar una vista HTML.

**Regla de capas del proyecto**: controller → service → repository. Los controladores solo reciben el pedido, validan la entrada y llaman al servicio; las reglas de negocio están en los servicios. Por eso, por ejemplo, RN-01 está en `FaseService.exigirAnteriorCompletada` y no en `FaseController`.

### 1.3 `@Configuration` y `@Bean`: cuándo y cómo se usan acá

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

2. **`BaseResourceServerConfig.jwtAuthenticationConverter`** (obras-commons): crea un `JwtAuthenticationConverter` y le pone `KeycloakConversorRoles` para que los roles de Keycloak se conviertan en autoridades de Spring (sección 4.3).

3. **`BaseResourceServerConfig.baseSecurityFilterChain`** (obras-commons): arma la cadena de filtros de seguridad de los tres servicios (sección 4.4). Recibe `HttpSecurity`, un objeto que Spring Security le pasa ya preparado para configurar.
   - Detalle fino: este método llama a `jwtAuthenticationConverter()`. Como la clase es `@Configuration`, Spring la envuelve en un proxy (una subclase generada con CGLIB) que intercepta esa llamada y devuelve **el mismo bean** ya creado, no uno nuevo.

4. **`MsRecursosApplication.cacheManager`** (ms-recursos):
   ```java
   @Bean
   public CacheManager cacheManager() { return new ConcurrentMapCacheManager("rutas"); }
   ```
   Le dice a Spring que la caché de `@Cacheable` se guarda en un `ConcurrentHashMap` en memoria, con una caché llamada `rutas`. `VencimientoCacheRutas` pide este mismo bean para vaciar la caché (sección 7.3).

5. **`SecurityConfig.securityFilterChain`** (api-gateway): la cadena de filtros del gateway, con las reglas por ruta y método (sección 4.5).

### 1.4 Cómo entran a cada servicio las clases de obras-commons (auto-configuración)

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

### 1.5 Configuración externa: `application.yml`, variables, `@Value`, `@ConfigurationProperties` y perfiles

- **`application.yml`** de cada servicio: puerto, base de datos, Keycloak, Resilience4j, URLs. Los secretos no están escritos: `password: ${OBRAS_DB_PASSWORD}`. Spring reemplaza `${...}` por la variable de entorno. Si falta, queda el texto literal y el error es `password authentication failed`. Docker Compose lee el `.env` (que no se commitea; los valores demo están en `.env.example`) y se las pasa a cada contenedor.
- **`@Value("${clave:valorPorDefecto}")`** inyecta un valor suelto. Ejemplos: `OpenMeteoService` recibe `@Value("${app.open-meteo.ventana-ejecucion-horas:6}") int ventanaHoras` (si la clave no está, vale 6). `OsrmProveedorRutas` usa `@Value("${app.osrm.url}")` **sin** valor por defecto, a propósito: si falta la clave, la aplicación no arranca, en lugar de arrancar apuntando a cualquier lado.
- **`@ConfigurationProperties(prefix = "obrasviales.sistema-client")`** en `SistemaClientProperties`: liga un grupo de claves a un objeto. `obrasviales.sistema-client.token-url` se carga en `setTokenUrl(...)` (Spring traduce `token-url` a `tokenUrl`; se llama *relaxed binding*). Es mejor que tres `@Value` sueltos: las claves quedan agrupadas y con tipo.
- **Perfiles.** Cada módulo tiene un `application-docker.yml` que solo se activa con `SPRING_PROFILES_ACTIVE=docker` (lo pone el compose). Cambia lo que es distinto dentro de Docker: la URL de Postgres (`postgres:5432` en lugar de `localhost`), las URLs de los otros servicios y `jwk-set-uri` hacia `http://keycloak:8080`. Trampa conocida: las **listas** del yml (como las rutas del gateway) no se fusionan con las del archivo base, se reemplazan enteras; por eso `api-gateway/.../application-docker.yml` repite las tres rutas completas.

## 2. La capa web: cómo entra un pedido y cómo sale la respuesta

### 2.1 Controladores y mapeo de rutas

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

### 2.2 Validación de entradas (Bean Validation)

Los DTO de entrada tienen anotaciones de `jakarta.validation`: `@NotNull`, `@NotBlank`, `@Size`, `@DecimalMin`, `@DecimalMax`, `@Min`, `@Max`, `@Positive`, `@FutureOrPresent`. Ejemplo: `SolicitudReservaRequest` en Recursos usa `@NotNull @DecimalMin("-90") @DecimalMax("90") Double tramoLat`.

- `@Valid` delante del `@RequestBody` hace que Spring valide antes de entrar al método. Si algo no cumple, lanza `MethodArgumentNotValidException` y el handler de commons responde 400 con un mapa `errores` campo → mensaje.
- Por qué `Double` y no `double`: con el primitivo, un campo que falta en el JSON llega como `0.0` y `@NotNull` no lo detecta (un primitivo nunca es null). Pasó en Recursos y se corrigió.

### 2.3 Formato de los DTO de respuesta

Las respuestas son `record` de Java con un método estático `desde(entidad)`. Ejemplo: `OperarioResponse.desde(Operario o)`. Así la API no expone la entidad JPA (con sus relaciones perezosas y campos internos) y el contrato queda explícito.

### 2.4 OpenAPI / Swagger

Cada servicio tiene springdoc y una clase `OpenApiConfig` con `@OpenAPIDefinition` (título y descripción; en Recursos incluye la atribución de OpenStreetMap/ODbL que pide OSRM) y `@SecurityScheme` de tipo `bearer`, que agrega el botón *Authorize* para pegar el token. `@Operation` y `@Tag` describen cada endpoint. La UI (`/swagger-ui.html`) y el JSON (`/v3/api-docs`) están abiertos sin token (`permitAll` en `BaseResourceServerConfig`), pero solo se ven corriendo el servicio por fuera de Docker, porque dentro de Docker los servicios no se publican (decisión del 13/11).

## 3. Errores: un solo formato para toda la API

### 3.1 Excepciones de dominio

En commons hay tres excepciones genéricas, todas `RuntimeException` (no obligan a declarar `throws`):

| Excepción | Cuándo se lanza | Respuesta |
|---|---|---|
| `RecursoNoEncontradoException` | id inexistente | 404 |
| `ConflictoEstadoException` | el estado actual no permite la acción (por ejemplo, RN-01, reserva no PROVISORIA) | 409 |
| `ReglaNegocioException(mensaje, codigoRegla)` | se incumple una regla | 422 con `codigoRegla` (por ejemplo `RN-07`) |

Operaciones agrega `ServicioNoDisponibleException` (503) para cuando Obras o Recursos no responden, y `OrdenNoEncontrada`/`TransicionInvalida`, que heredan de las de commons.

### 3.2 `@RestControllerAdvice` y `@ExceptionHandler`

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

### 3.3 Por qué los 401 y 403 se escriben a mano (`ProblemJson`)

Los 401 (sin token o token inválido) y los 403 del filtro de seguridad ocurren **antes** de llegar al controlador: los filtros de Spring Security corren antes que el `DispatcherServlet`, que es quien aplica los `@RestControllerAdvice`. Por eso el handler no los ve. Para que tengan el mismo formato:
- `ProblemDetailAuthenticationEntryPoint` implementa `AuthenticationEntryPoint` (lo que Spring Security llama cuando falta autenticación). Primero deja que `BearerTokenAuthenticationEntryPoint` ponga el 401 y el header `WWW-Authenticate` (lo pide el estándar de tokens Bearer) y después escribe el JSON con `ProblemJson.escribir`.
- `ProblemDetailAccessDeniedHandler` implementa `AccessDeniedHandler` para el 403.
- `ProblemJson` arma el JSON con un `StringBuilder` y escapa comillas y barras, porque fuera de Spring MVC no hay conversor de mensajes disponible.
- El gateway tiene copias propias de estas tres clases porque no depende de commons.

Un 403 lanzado **dentro** de un controlador (por `@PreAuthorize` o por `AccessDeniedException` del check-in) sí pasa por el advice: lo responde `handleAccessDenied`.

## 4. Seguridad: quién puede hacer qué y cómo se comprueba

### 4.1 Keycloak, tokens y roles

**Keycloak** es el servidor de identidad. El realm `obras-viales` (importado al arrancar desde `docker/keycloak/realm-obras-viales.json`) tiene:
- los roles `ADMIN`, `PLANIFICADOR`, `CAPATAZ`, `AUDITOR` y `SISTEMA`;
- los usuarios de demo `admin`, `planificador`, `capataz` y `auditor` (contraseña `<usuario>123`, solo para uso local);
- dos clientes: `obras-public-client` (público, para que las personas pidan token con usuario y contraseña) y `servicio-sistema` (confidencial, con secreto, para la cuenta de servicio).

**JWT** (*JSON Web Token*): el token que entrega Keycloak. Tiene tres partes en Base64 separadas por puntos: encabezado, contenido (*claims*) y firma. Los claims que importan acá: `iss` (quién lo emitió), `exp` (vencimiento, 300 s), `preferred_username` y `realm_access.roles` (los roles). La firma se hace con la clave privada de Keycloak; cualquiera puede comprobarla con la clave pública, que Keycloak publica en `/protocol/openid-connect/certs` (JWKS). Por eso los servicios **no** consultan a Keycloak en cada pedido: validan la firma localmente.

**Dos formas de pedir el token:**
- *Password grant*: una persona manda usuario y contraseña al cliente público (lo usan la colección, el panel y la app interna). Solo es aceptable en local con usuarios de demo.
- *Client credentials*: un programa manda `client_id` y `client_secret` y recibe un token a nombre de la cuenta de servicio, con rol SISTEMA. Lo usan el job diario y las llamadas entre servicios (`SistemaTokenService`, sección 6.1). El cliente `servicio-sistema` tiene desactivado el *password grant*: solo puede usar client credentials.

### 4.2 Resource server: cómo valida el token cada servicio

`spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8080/realms/obras-viales` en cada `application.yml` convierte al servicio en un **resource server**: cada pedido tiene que traer `Authorization: Bearer <token>`. Spring Security baja las claves públicas, comprueba la firma, que `iss` coincida y que no esté vencido. Si algo falla: 401.

Dentro de Docker hay una trampa: el token dice `iss = http://localhost:8080/...` (porque se pidió desde afuera), pero dentro de la red `localhost` es el propio contenedor. Solución aplicada (paso 13):
- `KC_HOSTNAME_URL=http://localhost:8080` en Keycloak, para que el `iss` sea siempre ese, se pida desde donde se pida.
- En el perfil docker, `jwk-set-uri: http://keycloak:8080/.../certs`: las claves se bajan por la red interna, y el `issuer-uri` queda solo para comparar el claim.

### 4.3 De roles de Keycloak a autoridades de Spring (`KeycloakConversorRoles`)

Spring Security no sabe dónde pone Keycloak los roles. Por defecto busca un claim `scope`. `KeycloakConversorRoles` implementa `Converter<Jwt, Collection<GrantedAuthority>>`: lee `realm_access.roles` y convierte cada rol en `SimpleGrantedAuthority("ROLE_" + rol)`. Por ejemplo, `PLANIFICADOR` pasa a ser la autoridad `ROLE_PLANIFICADOR`.

¿Por qué el prefijo `ROLE_`? Porque `hasRole('PLANIFICADOR')` en Spring busca la autoridad `ROLE_PLANIFICADOR` (agrega el prefijo solo). Si el conversor no lo pusiera, todo daría 403. Se conecta con el `@Bean jwtAuthenticationConverter` (sección 1.3) y la línea `.jwt(jwt -> jwt.jwtAuthenticationConverter(...))` de la cadena.

### 4.4 La cadena de filtros de los servicios (`BaseResourceServerConfig`)

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

### 4.5 Autorización por método: `@PreAuthorize`

```java
@PreAuthorize("hasAnyRole('" + Roles.PLANIFICADOR + "', '" + Roles.SISTEMA + "')")
```

- `@PreAuthorize` recibe una expresión **SpEL** (*Spring Expression Language*) que Spring evalúa antes de ejecutar el método. Si da falso, lanza `AccessDeniedException` y el advice responde 403.
- Cómo funciona por dentro: `@EnableMethodSecurity` hace que Spring envuelva el controlador en un **proxy**, un objeto intermedio que intercepta la llamada, evalúa la expresión y recién después llama al método real.
- Las expresiones se arman concatenando las constantes de `Roles` (commons): si alguien escribe mal un rol, no compila. En Recursos están juntas en `Permisos.java` (`SOLO_CAPATAZ`, `LECTURA`).
- **Detalle que conviene saber**: Spring resuelve y valida el `@RequestBody` (con `@Valid`) **antes** de llegar al proxy de `@PreAuthorize`. Un rol sin permiso que manda un cuerpo inválido recibiría 400 y no 403. Por eso el gateway también filtra por rol (sección 4.6): ese pedido ya no llega al servicio.

### 4.6 El gateway: autenticación y reglas gruesas por ruta y método

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

### 4.7 Las tres pruebas de seguridad que pide el enunciado

Para cada endpoint: sin token → 401; con rol insuficiente → 403; con el rol correcto → 2xx. Están automatizadas:
- en los servicios, con `MockMvc` y `jwt().authorities(new SimpleGrantedAuthority("ROLE_X"))` (de `spring-security-test`), que simula un token con esas autoridades sin Keycloak (`SeguridadEndpointsTest` en Operaciones, `CatalogoControllerSeguridadTest` y `ReservaControllerSeguridadTest` en Recursos, los `*ControllerTest` de Obras);
- en el gateway, con `ReglasPorRolGatewayTests` y `SeguridadGatewayTests`;
- en la colección, en la carpeta `02-Seguridad`.

La matriz completa está en `docs/matriz-permisos.md`.

## 5. Persistencia: entidades, repositorios, transacciones y migraciones

### 5.1 Entidades JPA

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

### 5.2 Repositorios de Spring Data

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

### 5.3 `@Transactional`

Una **transacción** agrupa varias operaciones en la base: o se confirman todas (*commit*) o ninguna (*rollback*).

- `@Transactional` sobre un método público de un `@Service` hace que Spring lo envuelva en un **proxy**: al entrar abre la transacción, al salir normalmente hace commit, y si sale una `RuntimeException` hace rollback.
- `@Transactional(readOnly = true)` en las consultas: avisa que no hay escrituras, y Hibernate se ahorra revisar cambios.
- **Ejemplo importante, `habilitarOrden`**: es `@Transactional`. Si la confirmación de la reserva en Recursos falla al final, la excepción hace rollback de todo lo local (estado, reserva guardada en la orden): la orden queda como estaba y se puede reintentar. Lo remoto no se deshace solo; para eso está la saga y el TTL (`guia-del-proyecto.md` (saga y reservas)).
- **`@Transactional(noRollbackFor = ConflictoEstadoException.class)` en `ReservaService.confirmarReserva`**: si la reserva venció, el método la guarda como EXPIRADA y **después** lanza 409. Sin `noRollbackFor`, el rollback borraría también el cambio a EXPIRADA.
- **`SuspensionService` no tiene `@Transactional` propio** a propósito: corre dentro de la transacción de `habilitarOrden` (o del rechazo del CAPATAZ). Si un paso de Obras falla a mitad de la saga, se deshace lo local junto con todo lo demás.
- **Trampa de la autoinvocación**: el proxy solo actúa cuando la llamada viene **de afuera** del objeto. Si un método llama a otro `@Transactional` de la misma clase (`this.otroMetodo()`), la anotación del segundo no se aplica. Pasó en una versión del job; por eso hoy el job solo delega en otro bean (`HabilitacionDiariaService`).

### 5.4 Flyway y la validación del esquema

- **Flyway** versiona la base: cada archivo `V<n>__descripcion.sql` en `src/main/resources/db/migration` se aplica una sola vez, en orden, y queda anotado en la tabla `flyway_schema_history` con una suma de control (*checksum*).
- **Nunca se edita una migración ya aplicada**: Flyway compara el checksum al arrancar y, si cambió, no arranca (`Migration checksum mismatch`). Por eso los cambios van en versiones nuevas. Ejemplos del proyecto: Recursos `V3__quitar_mantenimientos.sql` y `V4__quitar_rn04_y_restos_del_recorte.sql`; Operaciones `V6__recorte_alcance.sql`.
- Las migraciones también cargan los **datos de demo** (obras, cuadrillas, umbrales y las tres órdenes del escenario).
- **`spring.jpa.hibernate.ddl-auto: validate`**: Hibernate no crea ni modifica tablas, solo comprueba al arrancar que las entidades coinciden con el esquema que dejó Flyway. Si no coinciden, la aplicación no arranca. Así el esquema lo controla una sola herramienta.

## 6. Comunicación entre servicios

### 6.1 El token de la cuenta SISTEMA (`SistemaTokenService`)

Cuando Operaciones llama a Obras o a Recursos, no hay una persona detrás: usa la cuenta SISTEMA.
- `obtenerToken()` es `synchronized` (dos hilos no piden token a la vez) y guarda el token en memoria. Pide uno nuevo solo si no hay o si faltan menos de 30 segundos para que venza.
- Lo pide con *client credentials* (`grant_type=client_credentials`, `client_id`, `client_secret`), con timeouts de 3 s y 5 s.
- Reintenta hasta 3 veces con espera creciente (500 ms, 1 s) si Keycloak no responde, porque al arrancar el stack Keycloak puede tardar. No reintenta un error 4xx: credenciales mal configuradas no se arreglan reintentando.
- La espera se recibe como una interfaz funcional (`Espera`) para que los tests no tengan que dormir de verdad.

### 6.2 El interceptor (`SistemaRequestInterceptor`)

Implementa `ClientHttpRequestInterceptor`: Spring lo llama **antes de mandar cada pedido** del `sistemaRestClient`. Hace dos cosas:
1. copia el correlation-id del MDC (sección 9) al header `X-Correlation-Id`, para que el pedido se pueda seguir en los logs del otro servicio;
2. pone `Authorization: Bearer <token de SISTEMA>`.

### 6.3 Los clientes y cómo traducen los errores (`ObrasHttpClient`, `RecursosHttpClient`)

Cada cliente implementa una interfaz (`ObrasClient`, `RecursosClient`); los servicios dependen de la interfaz y en los tests se reemplaza por un mock. Los dos traducen lo que responde el otro servicio a excepciones propias, para que el error llegue al usuario con sentido:
- 404 → `RecursoNoEncontradoException`; 409 → `ConflictoEstadoException`; 422 → `ReglaNegocioException` **con el mismo `codigoRegla`** (por ejemplo, el RN-07 de Recursos llega al PLANIFICADOR como RN-07);
- error de red, timeout o 5xx → `ServicioNoDisponibleException` → 503. En la habilitación, la orden queda como estaba (PROGRAMADA) y el job la reintenta.

**Excepción a la regla de SISTEMA**: `ObrasHttpClient.tramosDeObra` (filtro `?obraId=` de `GET /ordenes`) usa el **token del usuario** que hizo el pedido, que saca del `SecurityContextHolder`, con un `RestClient` sin el interceptor de SISTEMA (que lo pisaría). Así Obras decide con los permisos de esa persona.

## 7. Integraciones externas y resiliencia

### 7.1 Open-Meteo (clima), en ms-operaciones

`OpenMeteoService.obtenerCondicionesClimaticas(lat, lon, fecha)` arma la URL con `UriComponentsBuilder`:
`?latitude=..&longitude=..&hourly=temperature_2m,precipitation_probability,soil_moisture_0_to_1cm&timezone=auto&start_date=<fecha>&end_date=<fecha>`

- Pide los datos **por hora** de la fecha de la orden (no el clima de este momento). `timezone=auto` hace que las horas vengan en la hora local del tramo.
- Busca las horas de la ventana de trabajo (desde `hora-inicio-jornada: 8`, durante `ventana-ejecucion-horas: 6`, es decir de 08:00 a 13:00) y toma el **peor** valor: la mayor humedad del suelo, la mayor probabilidad de lluvia y la menor temperatura. Si una sola hora no cumple, no se trabaja.
- La humedad del suelo está en **m³/m³** (fracción de agua en volumen: 0,15 es 15 %), no en porcentaje. El umbral también.
- Si falta cualquier dato de la ventana, **no** inventa un valor: lanza error. Eso termina en PENDIENTE_CONFIRMACION_CLIMATICA (RN-10).
- `Coordenada` (commons) valida los rangos antes de llamar.
- Está detrás de la interfaz `InterfazClimaService` (la propuesta la llamaba `ProveedorClima`): se podría cambiar de proveedor sin tocar `habilitarOrden`.

### 7.2 Resilience4j: reintentos y circuit breaker

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

### 7.3 OSRM (rutas), en ms-recursos

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

## 8. Tareas programadas

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

## 9. Correlation-id: seguir un pedido por los tres servicios

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

**Entre servicios**: el interceptor de commons (sección 6.2) copia el id del MDC al pedido saliente. El cuerpo de los errores también trae `correlationId`, así el usuario puede informarlo.

---

# Parte 2. Glosario de anotaciones

## 10. Glosario de anotaciones

Todas las anotaciones que aparecen en el código principal, agrupadas por para qué sirven. Una **anotación** (`@Algo`) es una marca en el código que no hace nada por sí sola: la lee alguien (el compilador, Lombok, Spring, Hibernate, Jackson) y actúa en consecuencia. Por eso en cada fila se dice **quién la lee y qué hace con ella**. La columna "Más" indica la sección de esta guía que lo explica a fondo. Qué clase usa cada una está en las tablas de conexiones de la sección 12.

### 10.1 Arranque y contenedor de Spring

| Anotación | Quién la lee y qué hace | Más |
|---|---|---|
| `@SpringBootApplication` | Spring Boot. Junta tres: `@Configuration` (la clase puede declarar beans), `@EnableAutoConfiguration` (Boot configura solo lo que encuentra en el classpath: Tomcat, Jackson, JPA, seguridad) y `@ComponentScan` (busca componentes en el paquete de la clase y sus subpaquetes). Va en la clase con el `main` de cada módulo | 1.1, 1.4 |
| `@Component` | Spring. Registra la clase como bean al hacer el component scan. Para piezas técnicas: clientes HTTP, filtros, jobs | 1.2 |
| `@Service` | Spring. Igual que `@Component`, pero indica que es lógica de negocio | 1.2 |
| `@Repository` | Spring. Igual que `@Component` para acceso a datos; además traduce excepciones de la base a las de Spring. En las interfaces de Spring Data es opcional | 1.2, 5.2 |
| `@Configuration` | Spring. La clase declara beans con métodos `@Bean`. Spring la envuelve en un proxy para que llamar a un método `@Bean` desde otro devuelva el mismo bean | 1.3 |
| `@Bean` | Spring. Sobre un método de una clase `@Configuration`: Spring lo llama una vez al arrancar y guarda lo que devuelve como bean (por defecto, singleton). Los parámetros del método se inyectan. El nombre del bean es el del método | 1.3 |
| `@Autowired` | Spring. Marca qué constructor usar para inyectar cuando la clase tiene más de uno (acá: uno público para Spring y otro de paquete para los tests) | 1.1 |
| `@Qualifier("nombre")` | Spring. Elige un bean por nombre cuando hay más de uno del mismo tipo. `ObrasHttpClient` y `RecursosHttpClient` piden `@Qualifier("sistemaRestClient")` | 1.3 |
| `@Value("${clave:defecto}")` | Spring. Inyecta un valor de la configuración (yml o variable de entorno). Sin `:defecto`, si falta la clave la aplicación no arranca | 1.5 |
| `@ConfigurationProperties(prefix = "...")` | Spring Boot. Liga un grupo de claves a los setters de un objeto (`obrasviales.sistema-client.token-url` → `setTokenUrl`) | 1.5 |
| `@Order(Ordered.HIGHEST_PRECEDENCE)` | Spring. Fija la prioridad entre componentes del mismo tipo: el filtro de correlation-id corre antes que los demás filtros, y `ManejadorServiciosExternos` se consulta antes que el handler de commons | 3.2, 9 |
| `@EnableScheduling` | Spring. Activa el planificador: desde ahí, los métodos `@Scheduled` se ejecutan solos | 8 |
| `@Scheduled(cron / fixedDelay / fixedRate)` | Spring. Ejecuta el método periódicamente en un hilo del planificador. `cron`: a una hora fija; `fixedDelay`: N tiempo después de que terminó la corrida anterior; `fixedRate`: cada N tiempo desde que empezó | 8 |
| `@EnableCaching` | Spring. Activa el soporte de caché: desde ahí, `@Cacheable` funciona (con un proxy) | 7.3 |
| `@Cacheable(value, key)` | Spring. Antes de ejecutar el método busca el resultado en la caché con esa clave; si está, lo devuelve sin ejecutar; si no, ejecuta y guarda | 7.3 |

### 10.2 Web (controladores y entrada de datos)

| Anotación | Quién la lee y qué hace | Más |
|---|---|---|
| `@RestController` | Spring MVC. `@Controller` + `@ResponseBody`: lo que devuelve cada método se convierte a JSON con Jackson | 2.1 |
| `@RequestMapping("/api/...")` | Spring MVC. Prefijo de ruta para todos los métodos de la clase | 2.1 |
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping` | Spring MVC. Asocian el método a un verbo HTTP y a una ruta | 2.1 |
| `@PathVariable` | Spring MVC. Toma un pedazo de la URL (`{id}`) y lo convierte al tipo del parámetro | 2.1 |
| `@RequestParam` | Spring MVC. Toma un parámetro de consulta (`?fecha=...`) | 2.1 |
| `@RequestBody` | Spring MVC. Convierte el JSON del cuerpo en el objeto del parámetro (con Jackson) | 2.1 |
| `@DateTimeFormat(iso = DATE)` | Spring MVC. Cómo convertir un texto de la URL a fecha (`2026-11-20` → `LocalDate`) | 2.1 |
| `@ResponseStatus` | Spring MVC. Fija el código HTTP de la respuesta sin usar `ResponseEntity` (por ejemplo 201 al crear) | 2.1 |
| `@AuthenticationPrincipal` | Spring Security. Inyecta el usuario autenticado; con JWT, el `Jwt` ya validado. Lo usa el check-in para leer `preferred_username` | 2.1 |
| `@Valid` | Bean Validation, disparado por Spring MVC. Valida el objeto antes de entrar al método; si falla, lanza `MethodArgumentNotValidException` → 400 | 2.2 |
| `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`, `@Positive`, `@FutureOrPresent` | Bean Validation (Hibernate Validator). Restricciones sobre cada campo del DTO de entrada: no nulo, no vacío, largo, rangos, positivo, fecha de hoy en adelante | 2.2 |

### 10.3 Errores

| Anotación | Quién la lee y qué hace | Más |
|---|---|---|
| `@RestControllerAdvice` | Spring MVC. La clase se aplica a todos los controladores: sus `@ExceptionHandler` atrapan excepciones de cualquier controlador y arman la respuesta (JSON) | 3.2 |
| `@ExceptionHandler(Tipo.class)` | Spring MVC. El método atrapa ese tipo de excepción cuando sale de un controlador y devuelve la respuesta de error (acá, un `ProblemDetail`) | 3.2 |

### 10.4 Seguridad

| Anotación | Quién la lee y qué hace | Más |
|---|---|---|
| `@EnableWebSecurity` | Spring Security. Activa la configuración propia de la cadena de filtros (`SecurityFilterChain`) | 4.4 |
| `@EnableMethodSecurity` | Spring Security. Activa `@PreAuthorize`: Spring envuelve los beans en un proxy que evalúa la expresión antes de cada método | 4.5 |
| `@PreAuthorize("hasRole(...)")` | Spring Security. Expresión SpEL que se evalúa antes del método; si da falso, `AccessDeniedException` → 403 | 4.5 |

### 10.5 Persistencia (JPA, Hibernate y Spring Data)

| Anotación | Quién la lee y qué hace | Más |
|---|---|---|
| `@Entity` | JPA (Hibernate). La clase representa una tabla; Hibernate puede guardarla y leerla | 5.1 |
| `@Table(name = "...")` | JPA. Nombre de la tabla (si no, usaría el nombre de la clase) | 5.1 |
| `@Id` | JPA. El atributo es la clave primaria | 5.1 |
| `@GeneratedValue(strategy = IDENTITY)` | JPA. La clave la genera la base al insertar (columna `BIGSERIAL`) | 5.1 |
| `@Column(name, nullable, length, unique, updatable)` | JPA. Cómo se mapea el atributo a la columna; con `ddl-auto: validate` Hibernate comprueba que coincida | 5.1 |
| `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name = "...")` | JPA. Relación "muchos a uno" con otra entidad, por la columna de clave foránea indicada. `LAZY`: la otra entidad se lee recién cuando se usa | 5.1 |
| `@Enumerated(EnumType.STRING)` | JPA. Guarda el enum como texto (`"HABILITADA"`) y no como número | 5.1 |
| `@Version` | JPA. Bloqueo optimista: Hibernate suma 1 en cada `UPDATE` y exige que la versión no haya cambiado desde que se leyó | 5.1 |
| `@PrePersist`, `@PreUpdate` | JPA. Métodos que Hibernate llama solo antes del primer `INSERT` y antes de cada `UPDATE` (fechas de creación y modificación) | 5.1 |
| `@Query("JPQL")` | Spring Data. Consulta escrita a mano en JPQL (con nombres de clases y atributos) cuando el nombre del método no alcanza | 5.2 |
| `@Modifying` | Spring Data. Obligatoria en un `@Query` que hace `UPDATE` o `DELETE`; devuelve la cantidad de filas afectadas | 5.2 |
| `@Param("nombre")` | Spring Data. Une el `:nombre` de la consulta con el parámetro del método | 5.2 |
| `@Transactional(readOnly, noRollbackFor)` | Spring. Proxy que abre una transacción al entrar al método, hace commit al salir y rollback si sale una `RuntimeException`. `readOnly` para consultas; `noRollbackFor` para no deshacer ante una excepción esperada | 5.3 |

### 10.6 Resiliencia (Resilience4j)

| Anotación | Quién la lee y qué hace | Más |
|---|---|---|
| `@Retry(name = "...")` | Resilience4j, con un proxy de Spring. Si el método lanza una excepción, lo vuelve a llamar según la configuración del yml (`openMeteoRetry`: 3 intentos, 1 s entre cada uno) | 7.2 |
| `@CircuitBreaker(name, fallbackMethod)` | Resilience4j. Cuenta los fallos de las últimas llamadas; si superan el umbral, deja de llamar por un tiempo y va directo al método de respaldo (fallback) | 7.2, 7.3 |
| `@RateLimiter(name, fallbackMethod)` | Resilience4j. Limita cuántas llamadas por período se permiten (OSRM: 1 por segundo); si no hay permiso a tiempo, va al fallback | 7.3 |

### 10.7 JSON (Jackson) y documentación (springdoc / OpenAPI)

| Anotación | Quién la lee y qué hace | Más |
|---|---|---|
| `@JsonIgnoreProperties(ignoreUnknown = true)` | Jackson. Al leer un JSON, ignora los campos que la clase no tiene. Lo usan los DTO que reciben respuestas de otros servicios o APIs: si el otro agrega un campo, no se rompe | 6.3 |
| `@JsonProperty("nombre")` | Jackson. Une un campo Java con un nombre de JSON distinto (por ejemplo `soil_moisture_0_to_1cm` → `soilMoisture0To1cm` en la respuesta de Open-Meteo) | 7.1 |
| `@JsonIgnore` | Jackson. El campo no se escribe en el JSON. Está en dos relaciones perezosas: `EvaluacionClimatica.ordenTrabajo` (la evaluación y la orden se apuntan entre sí y el JSON entraría en un ciclo) y `Maquina.cuadrilla` | 2.3 |
| `@OpenAPIDefinition`, `@Info` | springdoc. Título, versión y descripción del documento OpenAPI del servicio (en Recursos, con la atribución de OpenStreetMap/ODbL) | 2.4 |
| `@SecurityScheme`, `@SecurityRequirement` | springdoc. Declaran el esquema `bearerAuth` (token JWT) y que los endpoints lo requieren: agrega el botón *Authorize* en Swagger | 2.4 |
| `@Tag`, `@Operation` | springdoc. Agrupan los endpoints y describen cada uno en Swagger | 2.4 |

### 10.8 Lombok (generan código al compilar)

Lombok es un procesador de anotaciones: corre durante la compilación y **agrega métodos al `.class`** que no están escritos en el `.java`.

| Anotación | Qué genera |
|---|---|
| `@Getter`, `@Setter` | Un `getX()` y un `setX(...)` por cada campo |
| `@NoArgsConstructor` | Un constructor sin parámetros (JPA lo necesita para crear la entidad al leer de la base) |
| `@AllArgsConstructor` | Un constructor con todos los campos |
| `@RequiredArgsConstructor` | Un constructor con los campos `final`: es el que usa Spring para inyectar dependencias en los servicios |
| `@Builder` | Un "constructor por partes": `DatosClimaDTO.builder().temperatura(..).humedadSuelo(..).build()` |
| `@Slf4j` | Un campo `private static final Logger log` para escribir en el log |

### 10.9 De Java

| Anotación | Qué hace |
|---|---|
| `@Override` | El compilador comprueba que el método realmente sobreescribe uno de la clase padre o de la interfaz |
| `@FunctionalInterface` | El compilador comprueba que la interfaz tiene un solo método abstracto, para poder pasarla como lambda (`SistemaTokenService.Espera`) |
| `@SuppressWarnings("unchecked")` | Le pide al compilador que no avise de una conversión de tipos genéricos que no puede comprobar (al leer los claims del token como `Map`) |

---

# Parte 3. Endpoints y archivo por archivo

## 11. Los 32 endpoints

Todos exigen un JWT salvo que se diga lo contrario. Sin token: 401. Con un rol que no corresponde: 403. Las rutas `/internos/` son para la cuenta SISTEMA: los servicios se las llaman entre sí, sin pasar por el gateway.

### 11.1 ms-obras (12)

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

### 11.2 ms-recursos (7)

| Método y ruta | Roles | Qué hace | Respuestas |
|---|---|---|---|
| `GET /api/recursos/maquinas` | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | Lista las máquinas cargadas, con tipo, estado, horas de uso y cuadrilla. | 200 |
| `GET /api/recursos/operarios` | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | Lista los operarios con su cuadrilla, rol operativo y estado. | 200 |
| `GET /api/recursos/cuadrillas` | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | Lista las cuadrillas con su última ubicación, la fecha del check-in y los ids de sus operarios y máquinas. | 200 |
| `POST /api/recursos/cuadrillas/{id}/ubicaciones` | CAPATAZ (solo el de esa cuadrilla) | Check-in: actualiza la ubicación de la cuadrilla, que es la que RN-07 usa para calcular el viaje al tramo. | 200; 400; 403; 404 |
| `POST /api/recursos/internos/reservas` | SISTEMA | «Proponer y reservar»: busca una cuadrilla elegible (RN-05, RN-06, RN-07) y una planta si hay mezcla (RN-08), y crea una reserva PROVISORIA con TTL de 30 min. Es idempotente por orden. | 201; 400; 422 con `codigoRegla` |
| `PATCH /api/recursos/internos/reservas/{id}/confirmacion` | SISTEMA | Confirma una reserva PROVISORIA. Si el TTL venció, la pasa a EXPIRADA. Repetirla no cambia nada. | 200; 404; 409 |
| `PATCH /api/recursos/internos/reservas/{id}/liberacion` | SISTEMA | Libera una reserva PROVISORIA o CONFIRMADA. Repetirla no cambia nada. | 200; 404 |

### 11.3 ms-operaciones (13)

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

## 12. Archivo por archivo

En cada servicio el código se reparte en capas:
- **controller**: recibe el pedido HTTP, valida el cuerpo y controla el rol. No tiene lógica de negocio.
- **service**: las reglas de negocio.
- **repository**: interfaces de Spring Data JPA. Spring arma la consulta SQL a partir del nombre del método; por ejemplo, `findByTramoId` busca por la columna del tramo.
- **entity/domain/modelo**: clases mapeadas a tablas.
- **dto**: lo que entra y sale por la API. Cada servicio define los suyos; no se comparten.

Las entidades y los DTO casi no tienen métodos propios (getters y setters, o los que genera Lombok con `@Getter`/`@Setter`). Por eso se describen por sus campos y solo se detallan los métodos con lógica.

### 12.1 obras-commons (librería compartida)

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

**Conexiones de cada clase de obras-commons** (tabla generada del código el 2026-11-16: qué anotaciones tiene, qué clases del proyecto usa, quién la usa y con qué sistema externo habla; las anotaciones se explican en la sección 10). Un «—» en «La usan» en un controlador, un job o una configuración significa que no lo llama otra clase del proyecto: lo llama Spring (por un pedido HTTP, por el planificador o al arrancar):

| Clase | Anotaciones | Usa | La usan | Externo |
|---|---|---|---|---|
| `SistemaClientProperties` | @Component, @ConfigurationProperties | — | `SistemaTokenService` | — |
| `SistemaRequestInterceptor` | @Component | `CorrelationIdFilter`, `SistemaTokenService` | `SistemaRestClientConfig` | — |
| `SistemaRestClientConfig` | @Bean, @Configuration | `SistemaRequestInterceptor` | — | — |
| `SistemaTokenService` | @Autowired, @Service | `SistemaClientProperties` | `SistemaRequestInterceptor` | Keycloak (token) |
| `Coordenada` | — | — | `OpenMeteoService`, `OsrmProveedorRutas` | — |
| `ConflictoEstadoException` | — | — | `EjecucionService`, `FaseService`, `GlobalExceptionHandler`, `HabilitacionDiariaService`, `ObrasHttpClient`, `OrdenTrabajoServiceImpl`, `RecursosHttpClient`, `ReservaService` | — |
| `GlobalExceptionHandler` | @ExceptionHandler, @RestControllerAdvice | `ConflictoEstadoException`, `RecursoNoEncontradoException`, `ReglaNegocioException` | — | — |
| `RecursoNoEncontradoException` | — | — | `CuadrillaService`, `FaseService`, `GlobalExceptionHandler`, `HabilitacionDiariaService`, `ObraService`, `ObrasHttpClient`, `OperacionService`, `OrdenNoEncontrada`, `RecursosHttpClient`, `ReservaService` | — |
| `ReglaNegocioException` | — | — | `FaseService`, `GlobalExceptionHandler`, `HabilitacionDiariaService`, `MaterialService`, `ObrasHttpClient`, `OrdenTrabajoServiceImpl`, `RecursosHttpClient`, `ReservaService`, `TransicionInvalida` | — |
| `CorrelationIdFilter` | @Component, @Order | — | `ObrasHttpClient`, `ProblemJson`, `SistemaRequestInterceptor` | — |
| `BaseResourceServerConfig` | @Bean, @Configuration, @EnableMethodSecurity, @EnableWebSecurity | `KeycloakConversorRoles` | — | — |
| `KeycloakConversorRoles` | — | — | `BaseResourceServerConfig` | — |
| `ProblemDetailAccessDeniedHandler` | — | `ProblemJson` | `SecurityConfig` | — |
| `ProblemDetailAuthenticationEntryPoint` | — | `ProblemJson` | `SecurityConfig` | — |
| `ProblemJson` | — | `CorrelationIdFilter` | `ProblemDetailAccessDeniedHandler`, `ProblemDetailAuthenticationEntryPoint` | — |
| `Roles` | — | — | `EjecucionController`, `HabilitacionDiariaController`, `OTController`, `Permisos`, `ReservaController`, `SuspensionController`, `UmbralClimaticoController` | — |

### 12.2 api-gateway

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

**`security/ProblemJson`, `ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler`**: iguales a los de commons (ver 12.1), para que el cliente vea el mismo formato de 401 y 403 venga del gateway o del servicio.

**Conexiones de cada clase de api-gateway** (tabla generada del código el 2026-11-16: qué anotaciones tiene, qué clases del proyecto usa, quién la usa y con qué sistema externo habla; las anotaciones se explican en la sección 10). Un «—» en «La usan» en un controlador, un job o una configuración significa que no lo llama otra clase del proyecto: lo llama Spring (por un pedido HTTP, por el planificador o al arrancar):

| Clase | Anotaciones | Usa | La usan | Externo |
|---|---|---|---|---|
| `ApiGatewayApplication` | @SpringBootApplication | — | — | — |
| `CorrelationIdFilter` | @Component, @Order | — | `ObrasHttpClient`, `ProblemJson`, `SistemaRequestInterceptor` | — |
| `ProblemDetailAccessDeniedHandler` | — | `ProblemJson` | `SecurityConfig` | — |
| `ProblemDetailAuthenticationEntryPoint` | — | `ProblemJson` | `SecurityConfig` | — |
| `ProblemJson` | — | `CorrelationIdFilter` | `ProblemDetailAccessDeniedHandler`, `ProblemDetailAuthenticationEntryPoint` | — |
| `SecurityConfig` | @Bean, @Configuration, @EnableWebSecurity | `ProblemDetailAccessDeniedHandler`, `ProblemDetailAuthenticationEntryPoint` | — | — |

### 12.3 ms-obras

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

**Controladores (`controller/`)**: cada método controla el rol con `@PreAuthorize` y delega en el servicio. Las rutas y roles están en la tabla de la sección 11.1.
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

**Conexiones de cada clase de ms-obras** (tabla generada del código el 2026-11-16: qué anotaciones tiene, qué clases del proyecto usa, quién la usa y con qué sistema externo habla; las anotaciones se explican en la sección 10). Un «—» en «La usan» en un controlador, un job o una configuración significa que no lo llama otra clase del proyecto: lo llama Spring (por un pedido HTTP, por el planificador o al arrancar):

| Clase | Anotaciones | Usa | La usan | Externo |
|---|---|---|---|---|
| `OpenApiConfig` | @Configuration, @Info, @OpenAPIDefinition, @SecurityRequirement, @SecurityScheme | — | — | — |
| `FaseController` | @GetMapping, @PatchMapping, @PathVariable, @PreAuthorize, @RequestBody, @RequestMapping, @RequiredArgsConstructor, @RestController, @Valid | `FaseResponse`, `FaseService`, `MaterialCalculado`, `ReprogramarFaseRequest` | — | — |
| `FaseInternaController` | @PatchMapping, @PathVariable, @PostMapping, @PreAuthorize, @RequestBody, @RequestMapping, @RequiredArgsConstructor, @ResponseStatus, @RestController, @Valid | `FaseResponse`, `FaseService`, `RegistrarAvanceRequest` | — | — |
| `ObraController` | @GetMapping, @PathVariable, @PostMapping, @PreAuthorize, @RequestBody, @RequestMapping, @RequiredArgsConstructor, @ResponseStatus, @RestController, @Valid | `CrearObraRequest`, `CrearTramoRequest`, `FaseResponse`, `ObraDetalleResponse`, `ObraResponse`, `ObraResumenResponse`, `ObraService`, `TramoResponse` | — | — |
| `OperacionController` | @GetMapping, @PathVariable, @PreAuthorize, @RequestMapping, @RequiredArgsConstructor, @RestController | `DatosOperacionResponse`, `OperacionService` | — | — |
| `TramoController` | @PathVariable, @PostMapping, @PreAuthorize, @RequestBody, @RequestMapping, @RequiredArgsConstructor, @ResponseStatus, @RestController, @Valid | `CrearFaseRequest`, `FaseResponse`, `FaseService` | — | — |
| `ConsumoMaterial` | @Column, @Entity, @Enumerated, @GeneratedValue, @Getter, @Id, @NoArgsConstructor, @Setter, @Table | `TipoFase` | `ConsumoMaterialRepository` | — |
| `EstadoFase` | — | — | `DatosOperacionResponse`, `Fase`, `FaseResponse`, `FaseService`, `ObraService`, `OperacionService` | — |
| `Fase` | @Column, @Entity, @Enumerated, @GeneratedValue, @Getter, @Id, @JoinColumn, @ManyToOne, @NoArgsConstructor, @Setter, @Table | `EstadoFase`, `TipoFase`, `Tramo` | `FaseRepository`, `FaseResponse`, `FaseService`, `ObraService`, `OperacionService` | — |
| `Obra` | @Column, @Entity, @GeneratedValue, @Getter, @Id, @NoArgsConstructor, @Setter, @Table | — | `ObraRepository`, `ObraResponse`, `ObraService`, `Tramo` | — |
| `TipoFase` | — | — | `ConsumoMaterial`, `ConsumoMaterialRepository`, `CrearFaseRequest`, `DatosOperacionResponse`, `Fase`, `FaseResponse`, `FaseService`, `MaterialService` | — |
| `Tramo` | @Column, @Entity, @GeneratedValue, @Getter, @Id, @JoinColumn, @ManyToOne, @NoArgsConstructor, @Setter, @Table | `Obra` | `Fase`, `FaseService`, `ObraService`, `OperacionService`, `TramoRepository`, `TramoResponse` | — |
| `CrearFaseRequest` | @NotNull | `TipoFase` | `TramoController` | — |
| `CrearObraRequest` | @NotBlank, @Size | — | `ObraController` | — |
| `CrearTramoRequest` | @DecimalMax, @DecimalMin, @NotBlank, @NotNull, @Positive, @Size | — | `ObraController` | — |
| `DatosOperacionResponse` | — | `EstadoFase`, `MaterialCalculado`, `TipoFase` | `OperacionController`, `OperacionService` | — |
| `FaseResponse` | — | `EstadoFase`, `Fase`, `TipoFase` | `FaseController`, `FaseInternaController`, `ObraController`, `ObraDetalleResponse`, `ObraService`, `TramoController` | — |
| `MaterialCalculado` | — | — | `DatosOperacionResponse`, `FaseController`, `FaseService`, `MaterialService`, `OperacionService` | — |
| `ObraDetalleResponse` | — | `FaseResponse`, `TramoResponse` | `ObraController`, `ObraService` | — |
| `ObraResponse` | — | `Obra` | `ObraController` | — |
| `ObraResumenResponse` | — | — | `ObraController`, `ObraService` | — |
| `RegistrarAvanceRequest` | @Max, @Min, @NotNull | — | `FaseInternaController` | — |
| `ReprogramarFaseRequest` | @NotNull | — | `FaseController` | — |
| `TramoResponse` | — | `Tramo` | `ObraController`, `ObraDetalleResponse`, `ObraService` | — |
| `MsObrasApplication` | @SpringBootApplication | — | — | — |
| `ConsumoMaterialRepository` | — | `ConsumoMaterial`, `TipoFase` | `MaterialService` | PostgreSQL (JPA) |
| `FaseRepository` | — | `Fase` | `FaseService`, `ObraService`, `OperacionService` | PostgreSQL (JPA) |
| `ObraRepository` | — | `Obra` | `ObraService` | PostgreSQL (JPA) |
| `TramoRepository` | — | `Tramo` | `FaseService`, `ObraService` | PostgreSQL (JPA) |
| `FaseService` | @RequiredArgsConstructor, @Service, @Transactional | `ConflictoEstadoException`, `EstadoFase`, `Fase`, `FaseRepository`, `MaterialCalculado`, `MaterialService`, `RecursoNoEncontradoException`, `ReglaNegocioException`, `TipoFase`, `Tramo`, `TramoRepository` | `FaseController`, `FaseInternaController`, `TramoController` | — |
| `MaterialService` | @RequiredArgsConstructor, @Service, @Transactional | `ConsumoMaterialRepository`, `MaterialCalculado`, `ReglaNegocioException`, `TipoFase` | `FaseService`, `OperacionService` | — |
| `ObraService` | @RequiredArgsConstructor, @Service, @Transactional | `EstadoFase`, `Fase`, `FaseRepository`, `FaseResponse`, `Obra`, `ObraDetalleResponse`, `ObraRepository`, `ObraResumenResponse`, `RecursoNoEncontradoException`, `Tramo`, `TramoRepository`, `TramoResponse` | `ObraController` | — |
| `OperacionService` | @RequiredArgsConstructor, @Service, @Transactional | `DatosOperacionResponse`, `EstadoFase`, `Fase`, `FaseRepository`, `MaterialCalculado`, `MaterialService`, `RecursoNoEncontradoException`, `Tramo` | `OperacionController` | — |

### 12.4 ms-recursos

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

**Conexiones de cada clase de ms-recursos** (tabla generada del código el 2026-11-16: qué anotaciones tiene, qué clases del proyecto usa, quién la usa y con qué sistema externo habla; las anotaciones se explican en la sección 10). Un «—» en «La usan» en un controlador, un job o una configuración significa que no lo llama otra clase del proyecto: lo llama Spring (por un pedido HTTP, por el planificador o al arrancar):

| Clase | Anotaciones | Usa | La usan | Externo |
|---|---|---|---|---|
| `OsrmProveedorRutas` | @Cacheable, @CircuitBreaker, @Component, @RateLimiter, @Value | `Coordenada`, `ProveedorRutas` | — | OSRM |
| `ProveedorRutas` | — | — | `OsrmProveedorRutas`, `ReservaService` | — |
| `VencimientoCacheRutas` | @Component, @Scheduled | — | — | — |
| `OpenApiConfig` | @Configuration, @Info, @OpenAPIDefinition, @SecurityRequirement, @SecurityScheme | — | — | — |
| `CatalogoController` | @GetMapping, @PreAuthorize, @RequestMapping, @RestController | `CatalogoDtos`, `CatalogoService`, `Permisos` | — | — |
| `CuadrillaController` | @AuthenticationPrincipal, @GetMapping, @PathVariable, @PostMapping, @PreAuthorize, @RequestBody, @RequestMapping, @RestController, @Valid | `CatalogoDtos`, `CuadrillaService`, `Permisos` | — | — |
| `Permisos` | — | `Roles` | `CatalogoController`, `CuadrillaController` | — |
| `ReservaController` | @DecimalMax, @DecimalMin, @NotBlank, @NotNull, @PatchMapping, @PathVariable, @PostMapping, @PreAuthorize, @RequestBody, @RequestMapping, @RestController, @Valid | `ReservaResponse`, `ReservaService`, `Roles` | — | — |
| `CatalogoDtos` | @DecimalMax, @DecimalMin, @NotNull | `Cuadrilla`, `Maquina`, `Operario` | `CatalogoController`, `CatalogoService`, `CuadrillaController`, `CuadrillaService` | — |
| `ReservaResponse` | — | `Reserva` | `ReservaController` | — |
| `ComposicionMinimaTrabajo` | @Column, @Entity, @Id, @Table | — | `ComposicionMinimaRepository`, `ReservaService` | — |
| `ConfiguracionDesplazamiento` | @Column, @Entity, @Id, @Table | — | `ConfiguracionDesplazamientoRepository`, `ReservaService` | — |
| `Cuadrilla` | @Column, @Entity, @GeneratedValue, @Id, @Table | — | `CatalogoDtos`, `CuadrillaRepository`, `CuadrillaService`, `Maquina`, `Operario`, `Reserva`, `ReservaService` | — |
| `Maquina` | @Column, @Entity, @GeneratedValue, @Id, @JoinColumn, @JsonIgnore, @ManyToOne, @Table | `Cuadrilla` | `CatalogoDtos`, `MaquinaRepository` | — |
| `Operario` | @Column, @Entity, @GeneratedValue, @Id, @JoinColumn, @ManyToOne, @Table | `Cuadrilla` | `CatalogoDtos`, `CuadrillaService`, `OperarioRepository`, `ReservaService` | — |
| `PlantaMezcla` | @Column, @Entity, @GeneratedValue, @Id, @Table | — | `PlantaMezclaRepository`, `Reserva`, `ReservaService` | — |
| `Reserva` | @Column, @Entity, @GeneratedValue, @Id, @JoinColumn, @ManyToOne, @Table | `Cuadrilla`, `PlantaMezcla` | `ReservaRepository`, `ReservaResponse`, `ReservaService` | — |
| `MsRecursosApplication` | @Bean, @EnableCaching, @EnableScheduling, @SpringBootApplication | — | — | — |
| `ComposicionMinimaRepository` | — | `ComposicionMinimaTrabajo` | `ReservaService` | PostgreSQL (JPA) |
| `ConfiguracionDesplazamientoRepository` | — | `ConfiguracionDesplazamiento` | `ReservaService` | PostgreSQL (JPA) |
| `CuadrillaRepository` | — | `Cuadrilla` | `CuadrillaService`, `ReservaService` | PostgreSQL (JPA) |
| `MaquinaRepository` | — | `Maquina` | `CatalogoService`, `CuadrillaService`, `ReservaService` | PostgreSQL (JPA) |
| `OperarioRepository` | — | `Operario` | `CatalogoService`, `CuadrillaService`, `ReservaService` | PostgreSQL (JPA) |
| `PlantaMezclaRepository` | — | `PlantaMezcla` | `ReservaService` | PostgreSQL (JPA) |
| `ReservaRepository` | @Modifying, @Param, @Query | `Reserva` | `ReservaService` | PostgreSQL (JPA) |
| `CatalogoService` | @Service, @Transactional | `CatalogoDtos`, `MaquinaRepository`, `OperarioRepository` | `CatalogoController` | — |
| `CuadrillaService` | @Service, @Transactional | `CatalogoDtos`, `Cuadrilla`, `CuadrillaRepository`, `MaquinaRepository`, `Operario`, `OperarioRepository`, `RecursoNoEncontradoException` | `CuadrillaController` | — |
| `ExpiracionReservasJob` | @Component, @Scheduled | `ReservaService` | — | — |
| `ReservaService` | @Service, @Transactional | `ComposicionMinimaRepository`, `ComposicionMinimaTrabajo`, `ConfiguracionDesplazamiento`, `ConfiguracionDesplazamientoRepository`, `ConflictoEstadoException`, `Cuadrilla`, `CuadrillaRepository`, `MaquinaRepository`, `Operario`, `OperarioRepository`, `PlantaMezcla`, `PlantaMezclaRepository`, `ProveedorRutas`, `RecursoNoEncontradoException`, `ReglaNegocioException`, `Reserva`, `ReservaRepository` | `ExpiracionReservasJob`, `ReservaController` | — |

### 12.5 ms-operaciones

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

**Controladores (`controller/`)**: las rutas y roles están en la sección 11.3.
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
- `habilitarOrden(ordenId)`: el proceso central (descrito en `vision-general.md`, sección 5), en este orden:
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

**Conexiones de cada clase de ms-operaciones** (tabla generada del código el 2026-11-16: qué anotaciones tiene, qué clases del proyecto usa, quién la usa y con qué sistema externo habla; las anotaciones se explican en la sección 10). Un «—» en «La usan» en un controlador, un job o una configuración significa que no lo llama otra clase del proyecto: lo llama Spring (por un pedido HTTP, por el planificador o al arrancar):

| Clase | Anotaciones | Usa | La usan | Externo |
|---|---|---|---|---|
| `ObrasClient` | — | `ContextoFaseDTO`, `FaseDTO` | `EjecucionService`, `ObrasHttpClient`, `OrdenTrabajoServiceImpl`, `SuspensionService` | — |
| `ObrasHttpClient` | @Autowired, @Component, @JsonIgnoreProperties, @Qualifier, @Slf4j, @Value | `ConflictoEstadoException`, `ContextoFaseDTO`, `CorrelationIdFilter`, `FaseDTO`, `ObrasClient`, `RecursoNoEncontradoException`, `ReglaNegocioException`, `ServicioNoDisponibleException` | — | ms-obras por HTTP |
| `RecursosClient` | — | `ReservaDTO` | `EjecucionService`, `OrdenTrabajoServiceImpl`, `RecursosHttpClient`, `SuspensionService` | — |
| `RecursosHttpClient` | @Component, @JsonIgnoreProperties, @Qualifier, @Slf4j, @Value | `ConflictoEstadoException`, `RecursoNoEncontradoException`, `RecursosClient`, `ReglaNegocioException`, `ReservaDTO`, `ServicioNoDisponibleException`, `SolicitudReservaDTO` | — | ms-recursos por HTTP |
| `OpenApiConfig` | @Configuration, @Info, @OpenAPIDefinition, @SecurityRequirement, @SecurityScheme | — | — | — |
| `EjecucionController` | @Operation, @PatchMapping, @PathVariable, @PostMapping, @PreAuthorize, @RequestBody, @RequestMapping, @RequiredArgsConstructor, @RestController, @Tag, @Valid | `AvanceOrdenRequest`, `AvanceOrdenResponse`, `EjecucionService`, `OrdenTrabajoDTO`, `RechazoOrdenRequest`, `ResolucionClimaticaRequest`, `Roles` | — | — |
| `HabilitacionDiariaController` | @Operation, @PostMapping, @PreAuthorize, @RequestBody, @RequestMapping, @RequiredArgsConstructor, @RestController, @Tag, @Valid | `HabilitacionDiariaRequest`, `HabilitacionDiariaService`, `ResultadoHabilitacionDTO`, `Roles` | — | — |
| `OTController` | @DateTimeFormat, @GetMapping, @Operation, @PathVariable, @PostMapping, @PreAuthorize, @RequestBody, @RequestMapping, @RequestParam, @RequiredArgsConstructor, @RestController, @Tag, @Valid | `CrearOrdenRequest`, `EstadoOrden`, `EvaluacionClimaticaResponseDTO`, `OTServiceInterfaz`, `OrdenTrabajoDTO`, `Roles` | — | — |
| `SuspensionController` | @GetMapping, @Operation, @PreAuthorize, @RequestMapping, @RequestParam, @RequiredArgsConstructor, @RestController, @Tag | `Roles`, `SuspensionDTO`, `SuspensionService` | — | — |
| `UmbralClimaticoController` | @GetMapping, @Operation, @PathVariable, @PreAuthorize, @PutMapping, @RequestBody, @RequestMapping, @RequiredArgsConstructor, @RestController, @Tag, @Valid | `Roles`, `TipoTrabajo`, `UmbralClimatico`, `UmbralClimaticoRequest`, `UmbralClimaticoService` | — | — |
| `AvanceOrdenRequest` | @Max, @Min, @NotNull | — | `EjecucionController` | — |
| `AvanceOrdenResponse` | — | `EstadoOrden` | `EjecucionController`, `EjecucionService` | — |
| `ContextoFaseDTO` | @JsonIgnoreProperties | — | `ObrasClient`, `ObrasHttpClient`, `OrdenTrabajoServiceImpl` | — |
| `CrearOrdenRequest` | @FutureOrPresent, @Getter, @NotNull, @Setter | — | `OTController`, `OTServiceInterfaz`, `OrdenTrabajoServiceImpl` | — |
| `DatosClimaDTO` | @AllArgsConstructor, @Builder, @Getter, @NoArgsConstructor, @Setter | — | `InterfazClimaService`, `OpenMeteoService`, `OrdenTrabajoServiceImpl` | — |
| `EvaluacionClimaticaResponseDTO` | — | `EvaluacionClimatica` | `OTController` | — |
| `FaseDTO` | @JsonIgnoreProperties | — | `EjecucionService`, `ObrasClient`, `ObrasHttpClient`, `SuspensionService` | — |
| `HabilitacionDiariaRequest` | @NotNull | — | `HabilitacionDiariaController` | — |
| `OpenMeteoResponseDTO` | @Getter, @JsonIgnoreProperties, @JsonProperty, @NoArgsConstructor, @Setter | — | `OpenMeteoService` | — |
| `OrdenTrabajoDTO` | @Getter, @Setter | `EstadoOrden`, `OrdenTrabajo` | `EjecucionController`, `EjecucionService`, `HabilitacionDiariaService`, `OTController`, `OTServiceInterfaz`, `OrdenTrabajoServiceImpl` | — |
| `RechazoOrdenRequest` | @NotBlank, @Size | — | `EjecucionController` | — |
| `ReservaDTO` | @JsonIgnoreProperties | — | `OrdenTrabajoServiceImpl`, `RecursosClient`, `RecursosHttpClient` | — |
| `ResolucionClimaticaRequest` | @NotNull, @Size | — | `EjecucionController`, `EjecucionService` | — |
| `ResultadoHabilitacionDTO` | — | `EstadoOrden` | `HabilitacionDiariaController`, `HabilitacionDiariaService`, `JobHabilitacionDiaria` | — |
| `SolicitudReservaDTO` | — | — | `RecursosHttpClient` | — |
| `SuspensionDTO` | — | `MotivoSuspension`, `Suspension` | `SuspensionController`, `SuspensionService` | — |
| `UmbralClimaticoRequest` | @NotNull | — | `UmbralClimaticoController`, `UmbralClimaticoService` | — |
| `ManejadorServiciosExternos` | @ExceptionHandler, @Order, @RestControllerAdvice | `ServicioNoDisponibleException` | — | — |
| `OrdenNoEncontrada` | — | `RecursoNoEncontradoException` | `EjecucionService`, `OrdenTrabajoServiceImpl` | — |
| `ServicioNoDisponibleException` | — | — | `HabilitacionDiariaService`, `ManejadorServiciosExternos`, `ObrasHttpClient`, `RecursosHttpClient` | — |
| `TransicionInvalida` | — | `ReglaNegocioException` | `EjecucionService`, `OrdenTrabajoServiceImpl` | — |
| `ConfiguracionSuspensiones` | @Column, @Entity, @Getter, @Id, @NoArgsConstructor, @Setter, @Table | — | `ConfiguracionSuspensionesRepository`, `SuspensionService` | — |
| `EstadoOrden` | — | — | `AvanceOrdenResponse`, `EjecucionService`, `HabilitacionDiariaService`, `OTController`, `OTServiceInterfaz`, `OrdenTrabajo`, `OrdenTrabajoDTO`, `OrdenTrabajoRepository`, `OrdenTrabajoServiceImpl`, `ResultadoHabilitacionDTO`, `SuspensionService` | — |
| `EvaluacionClimatica` | @Column, @Entity, @GeneratedValue, @Getter, @Id, @JoinColumn, @JsonIgnore, @ManyToOne, @NoArgsConstructor, @PrePersist, @Setter, @Table | `OrdenTrabajo` | `EvaluacionClimaticaRepository`, `EvaluacionClimaticaResponseDTO`, `OTServiceInterfaz`, `OrdenTrabajoServiceImpl` | — |
| `MotivoSuspension` | — | — | `EjecucionService`, `OrdenTrabajoServiceImpl`, `Suspension`, `SuspensionDTO`, `SuspensionService` | — |
| `OrdenTrabajo` | @Column, @Entity, @Enumerated, @GeneratedValue, @Getter, @Id, @NoArgsConstructor, @PrePersist, @PreUpdate, @Setter, @Table, @Version | `EstadoOrden` | `EjecucionService`, `EvaluacionClimatica`, `HabilitacionDiariaService`, `OrdenTrabajoDTO`, `OrdenTrabajoRepository`, `OrdenTrabajoServiceImpl`, `SuspensionService` | — |
| `Suspension` | @Column, @Entity, @Enumerated, @GeneratedValue, @Getter, @Id, @NoArgsConstructor, @PrePersist, @Setter, @Table | `MotivoSuspension` | `SuspensionDTO`, `SuspensionRepository`, `SuspensionService` | — |
| `TipoTrabajo` | — | — | `OrdenTrabajoServiceImpl`, `UmbralClimatico`, `UmbralClimaticoController`, `UmbralClimaticoRepository`, `UmbralClimaticoService` | — |
| `UmbralClimatico` | @Column, @Entity, @Enumerated, @GeneratedValue, @Getter, @Id, @NoArgsConstructor, @Setter, @Table | `TipoTrabajo` | `OrdenTrabajoServiceImpl`, `UmbralClimaticoController`, `UmbralClimaticoRepository`, `UmbralClimaticoService` | — |
| `MsOperacionesApplication` | @EnableScheduling, @SpringBootApplication | — | — | — |
| `ConfiguracionSuspensionesRepository` | @Repository | `ConfiguracionSuspensiones` | `SuspensionService` | PostgreSQL (JPA) |
| `EvaluacionClimaticaRepository` | @Param, @Query, @Repository | `EvaluacionClimatica` | `OrdenTrabajoServiceImpl` | PostgreSQL (JPA) |
| `OrdenTrabajoRepository` | @Repository | `EstadoOrden`, `OrdenTrabajo` | `EjecucionService`, `HabilitacionDiariaService`, `OrdenTrabajoServiceImpl`, `SuspensionService` | PostgreSQL (JPA) |
| `SuspensionRepository` | @Modifying, @Param, @Query, @Repository | `Suspension` | `SuspensionService` | PostgreSQL (JPA) |
| `UmbralClimaticoRepository` | @Repository | `TipoTrabajo`, `UmbralClimatico` | `OrdenTrabajoServiceImpl`, `UmbralClimaticoService` | PostgreSQL (JPA) |
| `EjecucionService` | @RequiredArgsConstructor, @Service, @Transactional | `AvanceOrdenResponse`, `ConflictoEstadoException`, `EstadoOrden`, `FaseDTO`, `MotivoSuspension`, `ObrasClient`, `OrdenNoEncontrada`, `OrdenTrabajo`, `OrdenTrabajoDTO`, `OrdenTrabajoRepository`, `RecursosClient`, `ResolucionClimaticaRequest`, `SuspensionService`, `TransicionInvalida` | `EjecucionController` | — |
| `HabilitacionDiariaService` | @RequiredArgsConstructor, @Service, @Slf4j | `ConflictoEstadoException`, `EstadoOrden`, `OTServiceInterfaz`, `OrdenTrabajo`, `OrdenTrabajoDTO`, `OrdenTrabajoRepository`, `RecursoNoEncontradoException`, `ReglaNegocioException`, `ResultadoHabilitacionDTO`, `ServicioNoDisponibleException` | `HabilitacionDiariaController`, `JobHabilitacionDiaria` | — |
| `InterfazClimaService` | — | `DatosClimaDTO` | `OpenMeteoService`, `OrdenTrabajoServiceImpl` | — |
| `JobHabilitacionDiaria` | @Component, @RequiredArgsConstructor, @Scheduled, @Slf4j | `HabilitacionDiariaService`, `ResultadoHabilitacionDTO` | — | — |
| `OpenMeteoService` | @Autowired, @CircuitBreaker, @Retry, @Service, @Slf4j, @Value | `Coordenada`, `DatosClimaDTO`, `InterfazClimaService`, `OpenMeteoResponseDTO` | — | Open-Meteo |
| `OrdenTrabajoServiceImpl` | @RequiredArgsConstructor, @Service, @Transactional | `ConflictoEstadoException`, `ContextoFaseDTO`, `CrearOrdenRequest`, `DatosClimaDTO`, `EstadoOrden`, `EvaluacionClimatica`, `EvaluacionClimaticaRepository`, `InterfazClimaService`, `MotivoSuspension`, `OTServiceInterfaz`, `ObrasClient`, `OrdenNoEncontrada`, `OrdenTrabajo`, `OrdenTrabajoDTO`, `OrdenTrabajoRepository`, `RecursosClient`, `ReglaNegocioException`, `ReservaDTO`, `SuspensionService`, `TipoTrabajo`, `TransicionInvalida`, `UmbralClimatico`, `UmbralClimaticoRepository` | — | — |
| `OTServiceInterfaz` | — | `CrearOrdenRequest`, `EstadoOrden`, `EvaluacionClimatica`, `OrdenTrabajoDTO` | `HabilitacionDiariaService`, `OTController`, `OrdenTrabajoServiceImpl` | — |
| `SuspensionService` | @RequiredArgsConstructor, @Service, @Slf4j, @Transactional | `ConfiguracionSuspensiones`, `ConfiguracionSuspensionesRepository`, `EstadoOrden`, `FaseDTO`, `MotivoSuspension`, `ObrasClient`, `OrdenTrabajo`, `OrdenTrabajoRepository`, `RecursosClient`, `Suspension`, `SuspensionDTO`, `SuspensionRepository` | `EjecucionService`, `OrdenTrabajoServiceImpl`, `SuspensionController` | — |
| `UmbralClimaticoService` | @RequiredArgsConstructor, @Service, @Transactional | `TipoTrabajo`, `UmbralClimatico`, `UmbralClimaticoRepository`, `UmbralClimaticoRequest` | `UmbralClimaticoController` | — |

## 13. Infraestructura

- **`docker-compose.yml`**: levanta Postgres, Keycloak, los tres servicios y el gateway, cada uno con un *healthcheck*. Cada servicio arranca recién cuando lo que necesita está sano. Solo el gateway (8081) y Keycloak (8080) se publican; Postgres se publica solo en `127.0.0.1`. Las contraseñas salen del `.env` (los valores de demo están en `.env.example`).
- **`*/Dockerfile`**: construcción en dos etapas. Una imagen con Maven y Java 25 compila el módulo; la imagen final solo tiene el JRE y el `.jar`.
- **`docker/init-db/01-init-schemas.sql`**: crea las tres bases, cada una con su usuario dueño, y les quita el acceso público (`REVOKE CONNECT ... FROM PUBLIC`).
- **`docker/keycloak/realm-obras-viales.json`**: se importa al arrancar Keycloak. Define:
  - el realm `obras-viales` y sus roles;
  - los usuarios de demo `admin`, `planificador`, `capataz` y `auditor`, con contraseña `<rol>123`, solo para uso local;
  - el cliente público `obras-public-client`, con el que se loguean las personas;
  - el cliente `servicio-sistema`, la cuenta de servicio con *client credentials*.

## 14. Tests

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

## 15. Cómo se relacionan los servicios (resumen)

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

## 16. Código sin uso

Al escribir esta guía aparecieron restos del recorte que el código principal no llamaba. Se borraron el 2026-11-14, sin que cambiara ningún test:
- `ms-obras`: la clase vacía `CronogramaService` y su test sin casos, `ObraService.buscarTramo`, `FaseRepository.findByTramoId` y `findByTramoIdOrderByFechaInicioPlanificadaAsc`, y `TramoRepository.findByObraId`.
- `ms-recursos`: `CertificacionRepository.findByOperarioId` (después, el 2026-11-16, se borró toda la entidad al quitar RN-04) y `ReservaRepository.findByCuadrillaIdAndFechaReserva`.
- `ms-operaciones`: `OrdenTrabajoRepository.findByFaseId`, `UmbralClimaticoService.obtenerUmbralPorTipo` y `EstadoOrden.puedeTransicionarHacia` (una tabla de transiciones que no consultaba nadie: RN-11 se controla en los servicios, ver 12.5).
