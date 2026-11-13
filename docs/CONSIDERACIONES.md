# Consideraciones para el equipo

Guía práctica del día a día: qué hay que saber del proyecto y qué cosas pueden romper algo. Las decisiones con sus alternativas y su justificación van en `docs/DECISIONES.md`; este archivo es lo que conviene tener presente mientras se programa. Si descubrís algo parecido, sumalo acá (con el síntoma, la causa y qué hacer).

Estado al 2026-11-13. Si pasó tiempo, mirá `git log` y el README para ver qué cambió.

este proyecto usa **Spring Boot 4.1.2, Spring Framework 7 y Java 25**

## 1. Contexto del proyecto

**GOVP — Gestor de Obras Viales y Pavimentación** (TPI 2026, Backend de Aplicaciones, UTN FRC). Gestiona obras de pavimentación y decide cada día, con control climático, si una orden de trabajo se puede ejecutar.

**Arquitectura** (cada pieza es un módulo Maven en la raíz):

| Módulo | Puerto | Qué hace |
|---|---|---|
| `api-gateway` | 8081 | Único punto de entrada (Spring Cloud Gateway MVC). Valida el JWT y genera el correlation-id |
| `ms-obras` | 8082 | Obras, tramos (con coordenadas), fases (relevamiento, base, sub-base, carpeta asfáltica o losa), materiales, cronograma. RN-01, 02, 03 |
| `ms-recursos` | 8083 | Máquinas, operarios, certificaciones, mantenimiento, cuadrillas, plantas de mezcla. "Proponer y reservar" (reserva con TTL, idempotente) o rechazo motivado. Integra OSRM. RN-04 a 08 |
| `ms-operaciones` | 8084 | Órdenes de trabajo diarias y su máquina de estados, evaluación climática (es el único que habla con Open-Meteo), historial de suspensiones, orquestador de la saga. RN-09 a 12 |
| `obras-commons` | — | Librería (JAR) compartida: conversor de roles de Keycloak, config base de seguridad, errores estándar y handler global, excepciones de dominio, constantes de roles, filtro de correlation-id, cliente HTTP con token de servicio, `Coordenada` |
| Keycloak | 8080 | Autenticación (realm `obras-viales`) |
| PostgreSQL 16 | 5432 | Una base por servicio, cada una con su usuario dueño |

**Reglas de arquitectura que no se negocian:**
- Ningún servicio lee las tablas de otro: solo se hablan por API.
- `obras-commons` **no** lleva entidades, lógica de negocio ni DTOs de contrato entre servicios (cada servicio define los suyos; compartirlos los acopla).
- Defensa en profundidad: el gateway valida el JWT y **cada servicio vuelve a validarlo** y autoriza por rol con `@PreAuthorize`. Los servicios no se exponen fuera de la red de Docker (cuando estén dockerizados).
- Capas: controller → service → repository. Las reglas de negocio van en el service o el dominio, nunca en controllers ni repositories.

**Roles:** `ADMIN`, `PLANIFICADOR`, `CAPATAZ`, `OPERARIO`, `AUDITOR`, más la cuenta de servicio `SISTEMA` (client credentials) que usan el job diario y las llamadas entre servicios.

**Proceso central (habilitación diaria de una orden):** 1) Operaciones pide a Obras el tramo y la fase (coordenadas, tipo de trabajo, materiales, fase previa completa); 2) pide a Recursos "proponer y reservar" (certificación, composición, disponibilidad, mantenimiento, ruta con OSRM) y recibe una reserva con TTL o un rechazo; 3) consulta Open-Meteo (ventana de 6 h); 4) aplica los umbrales climáticos por tipo de trabajo: si cumple la orden queda HABILITADA, si no queda SUSPENDIDA y se dispara una saga de compensación (liberar reservas, reprogramar la fase y sus dependientes, crear una orden nueva); 5) tras N suspensiones la fase queda "en riesgo" y decide el PLANIFICADOR.

Estados de una orden: `PROGRAMADA → RECURSOS_ASIGNADOS → EVALUACION_CLIMATICA → HABILITADA → EN_EJECUCION → COMPLETADA`, con desvíos a `PENDIENTE_CONFIRMACION_CLIMATICA`, `SUSPENDIDA → REPROGRAMADA` o `CANCELADA`.

**Reglas de negocio** (cada una necesita un test automático y un request en la colección): RN-01 una fase no inicia sin la anterior completa · RN-02 replanificación en cascada · RN-03 materiales según la superficie · RN-04 un operario solo con certificación vigente · RN-05 sin solapamiento de operario ni máquina · RN-06 composición mínima de la cuadrilla por tipo de trabajo · RN-07 viaje de la cuadrilla al tramo dentro del máximo y de la jornada · RN-08 ventana máxima de transporte de la mezcla y hora de salida del camión · RN-09 umbrales climáticos por tipo de trabajo · RN-10 sin dato climático confiable la orden no se habilita sola · RN-11 solo transiciones de estado válidas · RN-12 N suspensiones → fase en riesgo.

**Convenciones:** validar entradas con Bean Validation; errores en formato estándar vía el handler global de commons; OpenAPI/Swagger (springdoc) en cada servicio; un test por regla de negocio (normal, límite y rechazo); una rama por tarea, un commit por paso, PR a `main` revisado por otra persona (no se pushea directo a `main`).

**Estado:** terminados la base de datos por servicio, los usuarios del realm, los secretos fuera del código, las versiones, `obras-commons` , Flyway + springdoc y la seguridad del gateway. También están los tres servicios con sus reglas de negocio, el Docker Compose completo y la seguridad de punta a punta. Lo que falta está en el «Estado actual» del README.

---

## 2. Lo que hay que tener a mano para trabajar

- **Variables de entorno por servicio** (los valores demo están en `.env.example`; el `.env` real **no** se sube nunca): `ms-obras` → `OBRAS_DB_PASSWORD`; `ms-recursos` → `RECURSOS_DB_PASSWORD`; `ms-operaciones` → `OPERACIONES_DB_PASSWORD` y `SERVICIO_SISTEMA_SECRET`.
- **Infra:** `docker compose up -d` levanta Postgres, Keycloak, el gateway y los tres servicios (seis contenedores); para desarrollar un servicio solo, conviene levantar únicamente `postgres keycloak` (ver el README).
- **Compilar todo:** `mvn clean package -DskipTests` desde la raíz.
- **Correr un servicio solo** (primera vez): `mvn -pl obras-commons -am install -DskipTests` desde la raíz, y después, dentro de la carpeta del módulo, definir la variable y `./mvnw spring-boot:run` (en PowerShell: `$env:OBRAS_DB_PASSWORD="obras_pass"`).
- **Tokens para probar** (PowerShell; las cuentas demo tienen contraseña `<rol>123`, solo para local; el token dura 5 minutos):
  - Un usuario con rol: `(Invoke-RestMethod -Method Post -Uri http://localhost:8080/realms/obras-viales/protocol/openid-connect/token -Body @{grant_type='password'; client_id='obras-public-client'; username='planificador'; password='planificador123'}).access_token`
  - La cuenta SISTEMA: el mismo `-Uri` con `-Body @{grant_type='client_credentials'; client_id='servicio-sistema'; client_secret='sistema_secret_demo'}`
  - Usarlo con `curl.exe -H "Authorization: Bearer $TOKEN" http://localhost:8082/...`

---

## 3. Qué puede romper algo, por área

### 3.1 Entorno y arranque

| Síntoma | Causa | Qué hacer |
|---|---|---|
| `password authentication failed for user "usr_..."` al arrancar un servicio **[P]** | Falta la variable de entorno de la contraseña. Spring **no lee el `.env`** (solo lo lee Docker Compose) y deja el texto `${VAR}` sin resolver | Definir la variable en esa terminal antes de arrancar |
| `Could not resolve dependencies ... obras-commons` al hacer `spring-boot:run` dentro de un módulo **[P]** | Commons no está instalado en el repositorio local de Maven (`~/.m2`) | `mvn -pl obras-commons -am install -DskipTests` desde la raíz |
| `obras-viales-parent:pom ... (absent)` **[P]** | Se instaló solo commons (`-pl obras-commons`) sin `-am`: falta el pom padre | El mismo comando con `-am` |
| Cambié `obras-commons` pero el servicio sigue con el comportamiento viejo **[C]** | Cuando corrés un módulo solo, usa la copia de commons instalada en `~/.m2`, que no se actualiza sola | Repetir el `install`, o compilar desde la raíz (`mvn -DskipTests package`) y correr el `.jar` |
| `mvn test` falla en un servicio **[P]** | Los tests de arranque necesitan Postgres arriba y las variables definidas | Levantar Docker y definir las variables, o usar `-DskipTests` |
| Avisos de `sun.misc.Unsafe` (Lombok) y `HTTP Status: 401` de `flyway-community-db-support` / `MavenFeed` **[P]** | Ruido conocido de Lombok con Java 25 y de repositorios que declara Flyway | Se ignoran: no son el error |
| `release version 25 not supported` **[C]** | El JDK instalado es más viejo que el que pide el proyecto | Instalar JDK 25 |
| Cambié el realm de Keycloak (`docker/keycloak/realm-obras-viales.json`) y no se nota **[C]** | El import del realm no pisa un realm que ya existe en la base | `docker compose down -v` (ver la sección 4: borra los datos locales) |
| No puedo levantar una segunda copia del compose **[P]** | Los contenedores tienen nombre fijo (`obras-postgres`, `obras-keycloak`) y puertos fijos | Bajar la copia anterior |
| El token deja de funcionar a los 5 minutos **[P]** | El realm configura 300 segundos de vida del token | Pedir un token nuevo |

### 3.2 Spring Boot 4 y dependencias

- **Los nombres de los starters cambiaron respecto de Boot 3** **[C]**. Los de este proyecto: `spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-flyway` (más `flyway-database-postgresql`), `spring-boot-starter-validation`. Si una IA o un tutorial propone `spring-boot-starter-web` u otros nombres de Boot 3, desconfiá: Federico tuvo que corregir los de commons.
- **Las propiedades de Spring Cloud Gateway cambian según la variante.** El proyecto usa la variante MVC (`spring-cloud-starter-gateway-server-webmvc`) y sus rutas van bajo `spring.cloud.gateway.server.webmvc.routes`. Si las ponés bajo `spring.cloud.gateway.routes` (como en casi todos los ejemplos de internet, que son de la variante reactiva), Spring **no avisa** y el gateway arranca sin rutas **[P]**.
- **springdoc:** la versión 3.x es la que corresponde a Boot 4; está fijada en una sola propiedad (`springdoc.version`) del pom padre. La 2.5.0 arranca pero está fuera del soporte oficial **[P]**. Si cambiás la versión, cambiala en ese lugar, no en cada servicio.
- **Dependencias nuevas:** agregalas en el `pom.xml` del módulo que las usa, no en el padre, salvo que las usen todos. No agregues librerías sin justificarlo (el proyecto evalúa las decisiones).

### 3.3 `obras-commons`

- **Hay que registrar cada clase nueva de commons en `obras-commons/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.** El escaneo de componentes de cada servicio parte de su propio paquete (`com.obrasviales.ms_obras`, etc.) y commons (`com.obrasviales.commons`) no es un subpaquete, así que una `@Configuration`, `@Component` o `@RestControllerAdvice` nueva en commons **no se activa sola**. Sin ese archivo, ninguna clase de commons funcionaba **[P]**.
- **No definas un bean de tipo `RestClient.Builder` en commons** **[C]**. Sería el único de ese tipo y cualquier componente que lo inyecte (por ejemplo el que llame a Open-Meteo u OSRM) lo recibiría con el interceptor que agrega el token SISTEMA: ese token saldría hacia un tercero. Por eso commons expone un `RestClient` ya armado con nombre propio: se usa con `@Qualifier("sistemaRestClient") RestClient` y **solo** para llamar a otros microservicios nuestros.
- **No pongas en commons** entidades JPA, lógica de negocio ni DTOs de contrato entre servicios.
- **`SistemaTokenService` lo usa `ms-operaciones` para llamar a Obras y Recursos** **[P]**: si esas llamadas dan 401, revisá el secreto (`SERVICIO_SISTEMA_SECRET`), la URL del token y su expiración (5 minutos).

### 3.4 Seguridad

- **Toda ruta exige JWT, salvo `/actuator/**` y Swagger (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`) en los servicios, y `/actuator/health/**` en el gateway** **[P]**. Si agregás un endpoint público, hay que abrirlo en la configuración base de commons (servicios) o en la `SecurityConfig` del gateway, no en cada controlador.
- **`@PreAuthorize("hasRole('ADMIN')")`** va con el nombre del rol **sin** el prefijo `ROLE_`: el conversor de commons lo agrega solo **[P]**. Usá las constantes de `Roles` en vez de escribir los nombres a mano.
- **Los roles se leen de `realm_access.roles` del token** **[P]**. Un rol cargado en otro lugar del token (por ejemplo `resource_access`) no se ve.
- **Códigos de respuesta:** sin token → 401 con el cuerpo **vacío**; rol insuficiente → 403 en formato `ProblemDetail` **[P]**. Probá siempre los tres casos por endpoint: sin token (401), rol insuficiente (403) y rol correcto.
- **Swagger UI y `/v3/api-docs` se abren sin token** **[P]**. En producción se apagan con `SPRINGDOC_API_DOCS_ENABLED=false` y `SPRINGDOC_SWAGGER_UI_ENABLED=false`; apagados, esas rutas responden 500 por el handler genérico (ver 3.5) **[P]**.
- **Secretos:** nunca los pongas en el código ni en un `application.yml` con valor por defecto (`${VAR:valor}` deja el secreto en el historial de Git para siempre). Los valores demo (`<rol>123`, `sistema_secret_demo`) son públicos y solo valen para local.
- **Keycloak:** el cliente `servicio-sistema` tiene habilitado el flujo de contraseña aunque es una cuenta de servicio, y `obras-public-client` es público con ese flujo. Es aceptable solo en local.

### 3.5 Errores HTTP (el handler global de commons)

- **Limitación conocida:** el handler genérico (`@ExceptionHandler(Exception.class)`) convierte una ruta inexistente (que debería ser 404), un método no permitido (405) y un JSON ilegible (400) en **500**, y **no registra el error en el log** **[P]**. Si escribís un test que espera 404, 405 o 400 de Spring, va a fallar por esto, no por tu código. Está pendiente de resolver.
- **Lo que sí funciona** **[P]**: `ReglaNegocioException(mensaje, "RN-xx")` → 422 con `codigoRegla`; `RecursoNoEncontradoException` → 404; `ConflictoEstadoException` → 409; un `@Valid` que falla → 400 con un mapa `errores`; `AccessDeniedException` → 403.
- **Usá las excepciones de commons** para los errores de negocio, no `RuntimeException` sueltas: una excepción desconocida termina en un 500 genérico.
- **`Coordenada` lanza `IllegalArgumentException` si el rango es inválido** **[C]**, y hoy eso termina en un 500. El service tiene que validar antes y devolver 400 o 422.

### 3.6 Gateway

- **Las rutas se aplican solo con `spring.cloud.gateway.server.webmvc.routes`** **[P]**. Si falta el nivel `server`, o si se usa `spring.cloud.gateway.routes`, Spring lo ignora sin avisar y todo da 404. Para saber si una ruta está aplicada, probá con el servicio apagado: una ruta aplicada da un error 5xx, no 404.
- **El gateway genera un `X-Correlation-Id` nuevo por pedido e ignora el del cliente** **[P]**; lo reenvía y los servicios lo reutilizan. La respuesta lleva un solo header. Todavía no aparece en las líneas de log: falta un patrón con `%X{correlationId}` **[C]**.
- **`/actuator/health` del gateway da 200 sin token** **[P]** (necesita `spring-boot-starter-actuator`). El resto exige JWT. El gateway solo autentica; los roles los revisa cada servicio.
- **El gateway no depende de `obras-commons`**: tiene su propia `SecurityConfig` y su filtro, sin conversor de roles.
- **Swagger no pasa por el gateway**: cada servicio publica el suyo en su puerto.
- **Las URI de las rutas apuntan a `localhost:808x`** y el `issuer-uri` a `localhost:8080`: cuando todo esté en Docker hay que cambiar las URI por los nombres de servicio y revisar de dónde se leen las claves del token **[C]**.

### 3.7 Entre servicios y reglas del dominio

- **Entre servicios solo se habla por API**, con el token SISTEMA y el `RestClient` `sistemaRestClient`. Nunca consultes las tablas de otro servicio, aunque esté a un `SELECT` de distancia.
- **Coordenadas:** OSRM espera **longitud primero, después latitud** (`lon,lat`). `Coordenada.toOsrmString()` ya lo hace bien; no lo armes a mano. Latitud entre -90 y 90, longitud entre -180 y 180.
- **Open-Meteo:** `soil_moisture_0_to_1cm` viene en m³/m³ (un valor entre 0 y 1), **no** en porcentaje; los umbrales tienen que estar en la misma unidad **[C]**.
- **OSRM público:** el servidor de demostración tiene límite de uso (alrededor de 1 pedido por segundo) y sin garantías de disponibilidad **[C]**; la decisión entre instancia propia y servidor público se toma al llegar a `ms-recursos`. Todo pedido a OSRM o Open-Meteo necesita timeout, caché y un plan si falla (estimación en línea recta marcada `fuente=ESTIMADA`; sin dato climático confiable la orden pasa a `PENDIENTE_CONFIRMACION_CLIMATICA`).
- **Reservas con TTL e idempotencia:** repetir un "proponer y reservar" con la misma clave no debe duplicar reservas, y una reserva sin confirmar tiene que vencer sola.

### 3.8 Git y documentos

- **Marcadores de conflicto commiteados:** ya pasó en este proyecto: se subieron los `<<<<<<<` / `=======` / `>>>>>>>` sin resolver y se rompió el `.env.example`, y con eso `docker compose` no arrancaba **[P]**. Antes de commitear un merge: `git grep -nE "^(<<<<<<<|=======|>>>>>>>)"` (no debe imprimir nada) y probar `docker compose --env-file .env.example config -q`.
- **`docs/DECISIONES.md` es el archivo que más conflictos da**, porque todos agregan su entrada al final **[P]**. Al resolver, conservá **todas** las entradas y revisá que no se pierda ninguna línea.
- **`git add .` arrastra archivos que no son de tu paso.** Agregá por nombre.
- **Nunca commitees el `.env`** (está en `.gitignore`). Sí se commitea `.env.example`, con valores demo.
- **Una rama por tarea, desde `main` actualizado** (`git pull` antes de crearla), y PR revisado por otra persona. Si trabajás sobre la rama de otro, avisale antes de pushear.
- **Al terminar un merge**, avisale al dueño de la rama antes de borrarla en GitHub; cada integrante corre `git fetch --prune` y empieza lo siguiente desde `main`.

### 3.9 Windows y PowerShell

- PowerShell 5.1 **no** entiende `&&` (usá comandos separados o `;`) **[P]**.
- `curl` en PowerShell es otro comando (`Invoke-WebRequest`): usá `curl.exe` **[P]**.
- `$env:VARIABLE="valor"` define la variable **solo en esa terminal**: si abrís otra, hay que repetirlo **[P]**.
- Las comillas importan: `./mvnw dependency:tree "-Dincludes=a,b"` necesita comillas por la coma **[P]**.

---

## 4. Migraciones de base de datos (Flyway)

### Cómo funciona

Cada servicio tiene su propia base de datos y su propia carpeta de migraciones: `src/main/resources/db/migration/`. Al arrancar, Flyway mira qué archivos hay, ve cuáles ya se aplicaron a esa base (los registra en la tabla `flyway_schema_history`) y aplica solo los que faltan, en orden de versión. Además `ddl-auto` está en `validate`: Hibernate **no crea ni modifica tablas**, solo comprueba al arrancar que cada entidad coincida con la tabla real, y si no coincide el servicio no levanta.

Hoy solo `ms-obras` tiene migraciones:

| Archivo | Qué contiene |
|---|---|
| `V1__init.sql` | Esquema: tablas `obras`, `tramos` y `fases` |
| `V2__datos_demo.sql` | Datos de demo (1 obra, 1 tramo, 2 fases) |

Los tres servicios ya tienen sus migraciones (`V1` en adelante). Cuando un servicio no tiene ninguna, arranca con el aviso `No migrations found`, que es esperado **[P]**; no se suben archivos vacíos (se explica abajo por qué).

### Regla de oro: lo que ya está en `main` no se edita

Flyway guarda un **checksum** (una huella) de cada archivo cuando lo aplica. Si después alguien cambia el contenido de `V1` o `V2`, en toda base que ya los aplicó Flyway detecta que el archivo cambió y **el servicio no arranca**.

- Para cambiar el esquema, agregá un archivo **nuevo**: `V3__descripcion.sql`, `V4__...`. Ejemplo para agregar fechas a las fases (lo necesita RN-02):

```sql
-- V3__fases_fechas.sql
ALTER TABLE fases ADD COLUMN fecha_inicio_prevista DATE;
ALTER TABLE fases ADD COLUMN fecha_fin_prevista DATE;
```

- El nombre es `V<número>__<descripción>.sql`, con **dos guiones bajos** después del número.
- Nunca reutilices un número ni lo cambies.
- Cada servicio lleva su propia numeración: `ms-obras` y `ms-recursos` pueden tener los dos un `V1`.
- Si dos personas trabajan en el mismo servicio, avísense el número que van a usar: si cada uno crea un `V3` distinto en su rama, al mergear hay un choque de versiones.
- Mantené el esquema y los datos de demo en archivos separados.
- No subas migraciones vacías: si después se llenan, cambia el checksum y falla en las bases que ya las corrieron.

### Tipos de datos: tienen que coincidir con la entidad

Con `validate`, el tipo de la columna tiene que coincidir con el tipo del atributo Java:

| Atributo Java | Columna en PostgreSQL |
|---|---|
| `Long` (ids y claves foráneas) | `BIGSERIAL` / `BIGINT` |
| `Double` (latitud y longitud) | `DOUBLE PRECISION` |
| `BigDecimal` (`superficie_m2`) | `DECIMAL(10,2)` |

Si no coinciden, el servicio no arranca y el error empieza con algo como `Schema-validation: wrong column type encountered`.

**Ids:** las entidades tienen que usar `@GeneratedValue(strategy = GenerationType.IDENTITY)`. Con `AUTO`, Hibernate espera una secuencia por entidad (por ejemplo `obras_seq`) que nuestras tablas no tienen, y `validate` falla.

**Coordenadas:** las columnas son `DOUBLE PRECISION` porque `Coordenada` (en `obras-commons`) usa `Double`. Tienen un `CHECK` que repite los rangos de `Coordenada` (latitud de -90 a 90, longitud de -180 a 180). `Coordenada` no es una entidad JPA y no se mapea sola. La propuesta provisional, a confirmar en el paso 8, es que `Tramo` tenga dos campos `Double` y que el service arme un `Coordenada` cuando valide o llame a OSRM. La alternativa `@Embedded` obligaría a meter `jakarta.persistence` en commons, que por decisión no lleva entidades.

**Validar antes de guardar:** si se intenta guardar una latitud de 200, la base la rechaza con el `CHECK`, pero eso llega como un error de base de datos. El service tiene que validar el rango antes y devolver 400 o 422 con un mensaje claro.

### Orden recomendado al agregar una tabla o columna

1. Escribí la migración nueva (`V3__...sql`).
2. Arrancá el servicio y mirá en el log que Flyway la aplique (`Migrating schema "public" to version "3 - ..."`).
3. Recién entonces escribí o ajustá la entidad, y volvé a arrancar: si `validate` se queja, el tipo no coincide.

### Si algo falla

| Síntoma | Qué pasó | Qué hacer |
|---|---|---|
| El servicio no arranca y habla de **checksum** o de una migración que cambió | Alguien editó un `V` que ya estaba aplicado | Restaurar el archivo a lo que está en `main`. Si es una base local de desarrollo y no se puede, recrearla (abajo) |
| `Schema-validation: wrong column type` o `missing column` | La entidad y la tabla no coinciden | Corregir el tipo en la entidad, o agregar una migración nueva. No editar `V1` ni `V2` |
| `No migrations found` | El servicio no tiene archivos en `db/migration` (hoy los tres ya los tienen) | Es esperado solo en un servicio nuevo sin esquema; si aparece en uno existente, mirá `git status` y `mvn clean` |
| Cambié de rama y aparece una migración que mi base no conoce | La base tiene aplicadas versiones que la rama no tiene (o al revés) | Recrear la base local |

### Volver a una base local limpia

Desde la raíz del repo:

```powershell
docker compose down -v
docker compose up -d
```

**Qué hace:** `down -v` apaga los contenedores y **borra los volúmenes**, es decir, todos los datos de Postgres (las bases de los tres servicios) y de Keycloak. Al volver a levantar, `docker/init-db/01-init-schemas.sql` recrea las bases y los usuarios, el realm de Keycloak se importa de nuevo y Flyway aplica las migraciones desde cero. Es seguro en desarrollo porque todo se regenera desde el repositorio; **no lo uses** si necesitás conservar datos que cargaste a mano. Los scripts de `init-db` solo corren cuando el volumen está vacío **[C]**.

### Comprobado

Sobre una base vacía creada con el `init-db` real de `main`: Flyway aplicó `V1` y `V2` desde cero (ambas figuran con éxito en `flyway_schema_history`), quedaron 1 obra, 1 tramo y 2 fases, las columnas de ids son `bigint` y las de coordenadas `double precision`, y el `CHECK` rechazó una latitud de 200 **[P]**.
