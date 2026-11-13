# GOVP — Gestor de Obras Viales y Pavimentación

Trabajo práctico integrador 2026 de Backend de Aplicaciones (UTN FRC, comisión 3k3, equipo 6767).

GOVP ayuda a planificar y controlar obras de pavimentación. Se carga una obra con sus tramos y fases (relevamiento, base, sub-base, carpeta asfáltica o losa de hormigón) y el sistema se encarga de lo más delicado: decidir, día por día, si una orden de trabajo se puede ejecutar. Para eso cruza tres cosas: que la fase anterior esté terminada, que haya una cuadrilla (con operarios certificados, máquinas disponibles y tiempo de viaje razonable) y que el clima acompañe. Si el clima no acompaña, la orden se suspende, se liberan las reservas y la fase se reprograma. Si una fase se suspende demasiadas veces, queda marcada en riesgo para que decida una persona.

Son tres servicios Spring Boot detrás de un API Gateway, con Keycloak para la seguridad y PostgreSQL para los datos.

## Cómo está armado

| Componente | Puerto | Qué hace |
|---|---|---|
| `api-gateway` | 8081 | Única puerta de entrada. Valida el JWT y enruta a los servicios. |
| `ms-obras` | 8082 | Obras, tramos, fases, materiales y cronograma (RN-01 a RN-03). |
| `ms-recursos` | 8083 | Máquinas, operarios, cuadrillas y plantas. Propone y reserva recursos para una orden (RN-04 a RN-08). Usa OSRM para los tiempos de viaje. |
| `ms-operaciones` | 8084 | Órdenes de trabajo y su máquina de estados, evaluación climática con Open-Meteo y la saga de suspensión (RN-09 a RN-12). |
| `obras-commons` | — | Librería Maven que comparten los tres servicios: seguridad, errores estándar, filtro de correlation-id y cliente con token de servicio. No lleva entidades ni lógica de negocio. |
| Keycloak | 8080 | Usuarios, roles y tokens. |
| PostgreSQL | 5432 | Una base por servicio, cada una con su propio usuario. Flyway crea las tablas y carga datos de demo. |

Un detalle de seguridad que conviene tener presente: cuando se levanta con Docker Compose, **solo el gateway (8081) y Keycloak (8080) se pueden alcanzar desde afuera**. Obras, Recursos y Operaciones viven dentro de la red de Docker y se hablan entre sí por nombre. Aun así, cada servicio vuelve a validar el token y controla el rol con `@PreAuthorize`, así que no dependemos de que el gateway sea la única barrera. PostgreSQL se publica solamente en `127.0.0.1`, para poder correr tests desde la máquina.

Versiones: Java 25, Spring Boot 4.1.2, PostgreSQL 16, Keycloak 24.0.5.

## Levantarlo desde cero

Hace falta tener Docker (con Docker Compose) y nada más: no hay que instalar Java ni Maven, porque las imágenes se compilan solas.

1. Clonar el repositorio y copiar las variables de entorno de ejemplo:

   ```powershell
   Copy-Item .env.example .env
   ```

   El `.env.example` trae valores de demo que sirven tal cual para probar en local. Si algún día se despliega en serio, hay que cambiar todas las contraseñas. El `.env` no se sube a git.

2. Construir y levantar todo:

   ```powershell
   docker compose up -d --build
   ```

   La primera vez tarda unos minutos porque compila los cuatro módulos. Compose ordena el arranque solo: Postgres primero, los servicios cuando su base está lista y el gateway al final, cuando todo lo demás ya responde.

3. Esperar a que todo quede sano:

   ```powershell
   docker compose ps
   ```

   Tienen que aparecer los seis contenedores con `(healthy)`. Si alguno todavía dice `starting`, hay que darle unos segundos y repetir.

Para empezar de cero otra vez (esto **borra los datos** de las bases):

```powershell
docker compose down -v
docker compose up -d --build
```

Si algo no arranca, `docker compose logs -f <servicio>` (por ejemplo `ms-operaciones`) suele explicarlo enseguida.

## Probar que funciona

Los usuarios de demo ya vienen cargados en Keycloak. Todos tienen como contraseña `<rol>123` (por ejemplo `planificador123`), y eso es solo para uso local.

| Usuario | Rol |
|---|---|
| `admin` | ADMIN |
| `planificador` | PLANIFICADOR |
| `capataz` | CAPATAZ |
| `operario` | OPERARIO |
| `auditor` | AUDITOR |

Además hay una cuenta de servicio, `servicio-sistema` (rol SISTEMA), que usan los servicios entre sí y el job diario. Se autentica con client credentials y el secreto es `SERVICIO_SISTEMA_SECRET` del `.env`.

Para pedir un token (dura cinco minutos) y llamar al gateway desde PowerShell:

```powershell
$token = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/realms/obras-viales/protocol/openid-connect/token -Body @{grant_type='password'; client_id='obras-public-client'; username='planificador'; password='planificador123'}).access_token

curl.exe -s -o NUL -w "sin token: %{http_code}`n" http://localhost:8081/api/obras
curl.exe -s -w "`ncon token: %{http_code}`n" -H "Authorization: Bearer $token" http://localhost:8081/api/obras
```

Sin token tiene que devolver 401. Con el token del planificador tiene que devolver 200 y la obra de demo ("Ruta Provincial 34"). Si en cambio se prueba con el token de `operario`, devuelve 403: el usuario está autenticado pero su rol no alcanza. Esos tres casos (sin token, rol insuficiente y rol correcto) son los que probamos siempre.

Las rutas del gateway son `/api/obras/**`, `/api/recursos/**` y `/api/operaciones/**`. Las que empiezan con `/api/<servicio>/internos/` son para llamadas entre servicios y exigen el rol SISTEMA.

También se puede comprobar el aislamiento: `curl.exe http://localhost:8082/actuator/health` no responde, porque Obras no está publicado fuera de Docker.

## Correr un servicio sin Docker

Para desarrollar o para correr los tests conviene levantar solo la infraestructura y el servicio que se está tocando:

```powershell
docker compose up -d postgres keycloak
mvn -pl obras-commons -am install -DskipTests
```

El segundo comando instala `obras-commons` y el pom padre en el repositorio local de Maven; sin eso, los servicios no encuentran la dependencia. Hay que repetirlo cada vez que cambie `obras-commons`.

Después, en la carpeta del módulo, con la contraseña de su base definida **en esa misma terminal** (Spring no lee el `.env`, solo lo lee Docker Compose):

```powershell
cd ms-obras
$env:OBRAS_DB_PASSWORD="obras_pass"
./mvnw spring-boot:run
```

Cada servicio usa su variable: `OBRAS_DB_PASSWORD`, `RECURSOS_DB_PASSWORD` y, para operaciones, `OPERACIONES_DB_PASSWORD` junto con `SERVICIO_SISTEMA_SECRET`. Los valores de demo están en `.env.example`. Si falta la variable, el síntoma es `password authentication failed`.

Corriendo así, cada servicio se alcanza directo en su puerto y tiene Swagger en `http://localhost:808x/swagger-ui.html` (OpenAPI en `/v3/api-docs`).

Los tests de un módulo (necesitan Postgres arriba y su variable definida):

```powershell
mvn -pl ms-obras -am clean test
```

Conviene usar `clean` al cambiar de rama: si no, Maven puede arrastrar archivos compilados de la rama anterior (nos pasó con migraciones de Flyway que ya no existían).

## Dónde está la documentación

- [`docs/c2-contenedores.md`](docs/c2-contenedores.md): diagrama de contenedores de lo que levanta el compose, con quién se habla con quién y qué queda expuesto.
- [`docs/DECISIONES.md`](docs/DECISIONES.md): la bitácora de decisiones, con las alternativas que evaluamos y sus consecuencias.
- [`docs/matriz-permisos.md`](docs/matriz-permisos.md): qué rol puede hacer qué.
- [`docs/CONSIDERACIONES.md`](docs/CONSIDERACIONES.md): guía para el equipo con lo que conviene saber antes de tocar cada área.
- [`docs/trazabilidad-ms-obras.md`](docs/trazabilidad-ms-obras.md) y [`docs/trazabilidad-ms-operaciones.md`](docs/trazabilidad-ms-operaciones.md): de cada requisito, dónde se implementa y qué evidencia lo respalda (tests, requests).
- [`docs/ms-obras-api.md`](docs/ms-obras-api.md): guía de la API de Obras.
- [`docs/arquitectura.md`](docs/arquitectura.md): diagrama de contexto (C1), modelo de datos de las tres bases (ER) y secuencia de la habilitación diaria.
- [`docs/matriz-requisitos.md`](docs/matriz-requisitos.md): requisito, implementación y evidencia de cada funcionalidad y escenario comprometido.
- [`docs/trazabilidad-reglas.md`](docs/trazabilidad-reglas.md): las reglas RN-01 a RN-12, con su test y su request.
- [`coleccion/`](coleccion/README.md): colección de Postman y entorno de ejemplo.

## Estado actual (13/11/2026)

Está resuelto el despliegue completo con Docker Compose, la seguridad de punta a punta (gateway, servicios y roles), los tres servicios con sus reglas de negocio y el job diario de habilitación con el escenario sembrado de tres órdenes.

Lo que se está cerrando en paralelo: los endpoints de catálogos, personal y configuración de Recursos y el vencimiento automático de reservas; los permisos finales y la resiliencia de Operaciones; y la colección de Postman completa. La colección, los diagramas y el guion de la demo ya están en el repositorio (ver abajo). Cuando se terminen las ramas pendientes, este apartado se actualiza.

Una limitación a tener en cuenta: con Docker, el Swagger de cada servicio no es accesible desde afuera, justamente porque los servicios no se publican. Es una decisión consciente, explicada en `docs/DECISIONES.md`.

## Servicios externos y atribuciones

**Rutas y tiempos de viaje.** `ms-recursos` calcula tiempos con [OSRM](https://project-osrm.org/), sobre datos de © OpenStreetMap contributors, disponibles bajo la licencia [ODbL](https://www.openstreetmap.org/copyright). Usamos el servidor público de demostración (`router.project-osrm.org`), que es para uso razonable y no comercial, con un máximo de un pedido por segundo y sin garantías de disponibilidad. Por eso el cliente lleva límite de pedidos, caché, un User-Agent propio y un circuito de corte: si OSRM no responde, se estima la distancia en línea recta con un factor de desvío y el resultado queda marcado como `fuente: ESTIMADA`.

**Clima.** `ms-operaciones` consulta el pronóstico de [Open-Meteo](https://open-meteo.com/) (humedad del suelo, probabilidad de precipitación y temperatura). Los datos de Open-Meteo se publican bajo [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Si el servicio no responde, la orden no se habilita sola: queda en `PENDIENTE_CONFIRMACION_CLIMATICA` hasta que alguien la resuelva.
