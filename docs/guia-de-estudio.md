# Guía de estudio de GOVP

Esta guía es para que cualquier integrante pueda explicar el sistema completo en la defensa, sin haber escrito cada parte. Va de lo general a lo particular: primero qué problema resuelve, después cómo está armado, el proceso central paso a paso, las reglas, las integraciones, la seguridad y los datos, y al final las decisiones con sus alternativas y las preguntas que conviene tener preparadas.

Cada sección dice en qué archivo está lo que se explica, para poder abrirlo y mostrarlo. Los otros documentos entran en detalle en temas puntuales y esta guía los enlaza:

| Para ver… | Abrí |
|---|---|
| Diagrama de contexto (C1), modelo de datos (ER) y secuencia de la habilitación | [`arquitectura.md`](arquitectura.md) |
| Diagrama de contenedores (C2) | [`c2-contenedores.md`](c2-contenedores.md) |
| Qué pide cada requisito y dónde está la evidencia | [`matriz-requisitos.md`](matriz-requisitos.md) |
| Regla → test → request | [`trazabilidad-reglas.md`](trazabilidad-reglas.md) y las trazabilidades por servicio |
| Qué rol puede hacer qué | [`matriz-permisos.md`](matriz-permisos.md) |
| Por qué se decidió cada cosa | [`DECISIONES.md`](DECISIONES.md) |
| Guion de la demo y preguntas de defensa | [`guion-demo.md`](guion-demo.md) |
| Cómo levantarlo | [`../README.md`](../README.md) |

---

## 1. El problema en una frase

Una repartición vial planifica obras de pavimentación y, **cada día**, tiene que decidir si una cuadrilla con sus máquinas y operarios puede trabajar en un tramo concreto. La decisión depende de cinco cosas: que la fase anterior de la obra esté terminada, que haya recursos certificados y libres, que la cuadrilla y la mezcla lleguen a tiempo (distancia real por calle, con OSRM), que el clima lo permita (Open-Meteo) y las reglas técnicas de cada tipo de trabajo.

Si el clima no acompaña, la orden se **suspende** y eso tiene consecuencias en cadena: se liberan los recursos, se corre la fase (y las que dependen de ella) y se crea una orden nueva. Ese recorrido es el **proceso central** y es lo que más puntos da en la rúbrica (NEG-01, 15 puntos).

**Glosario mínimo:**
- **Obra**: el proyecto (por ejemplo "Ruta Provincial 34").
- **Tramo**: un pedazo de la obra, con coordenadas y superficie en m².
- **Fase**: una etapa del trabajo en un tramo: relevamiento, base, sub-base, carpeta asfáltica o losa de hormigón. Cada fase puede tener una **fase previa** que tiene que estar completa antes.
- **Tipo de trabajo**: el tipo de la fase (`RELEVAMIENTO`, `BASE`, `SUBBASE`, `CARPETA_ASFALTICA`, `LOSA_HORMIGON`). Es el vocabulario común de los tres servicios.
- **Cuadrilla**: un grupo de operarios y máquinas que trabaja junto; tiene una última ubicación conocida (check-in).
- **Planta de mezcla**: de donde sale el camión con la mezcla asfáltica u hormigón.
- **Orden de trabajo**: "la cuadrilla X trabaja en la fase Y el día Z". Tiene una máquina de estados.
- **Reserva**: los recursos apartados para una orden. Primero es **provisoria** (con vencimiento) y se **confirma** si la orden se habilita.

---

## 2. Cómo está armado

### 2.1 Las piezas

```
Cliente (Postman / panel)
        │  HTTP + JWT
        ▼
  api-gateway :8081 ─── valida el token, aplica reglas por método y rol, genera el correlation-id
        │  (red interna de Docker: la "trusted zone")
        ├──► ms-obras       :8082  obras, tramos, fases, materiales, cronograma       (RN-01 a RN-03)
        ├──► ms-recursos    :8083  máquinas, operarios, cuadrillas, reservas, OSRM     (RN-04 a RN-08)
        └──► ms-operaciones :8084  órdenes, clima (Open-Meteo), saga, job diario       (RN-09 a RN-12)

  Keycloak :8080  emite los tokens (OIDC)        PostgreSQL :5432  una base por servicio
```

- **Solo el gateway (8081) y Keycloak (8080) se publican** fuera de Docker. Los tres servicios viven en la red interna: desde la máquina, `curl.exe http://localhost:8082/...` no responde. Eso es la **trusted zone** que pide el enunciado.
- Los servicios se hablan entre ellos **directo** (Operaciones llama a Obras y Recursos por la red interna, sin pasar por el gateway), con el token de la cuenta de servicio **SISTEMA**.
- Todo se levanta con `docker compose up -d --build` (archivo `docker-compose.yml`). Cada módulo tiene un `Dockerfile` en dos etapas: una imagen con Maven compila, y una imagen solo con Java ejecuta el `.jar` con un usuario sin privilegios.

### 2.2 Por qué tres servicios

Cada uno tiene **datos propios y reglas propias**, y cambia por razones distintas:
- **Obras** sabe de planificación: qué fases hay, en qué orden y cuándo. No sabe de máquinas ni de clima.
- **Recursos** sabe quién y qué está disponible, y si llega a tiempo. No sabe de órdenes ni de clima.
- **Operaciones** orquesta el día: toma una orden, le pregunta a Obras, le pide recursos a Recursos, consulta el clima y decide.

Ningún servicio lee tablas de otro: si Operaciones necesita la fase, la pide por HTTP a Obras (`GET /api/obras/internos/fases/{id}/contexto-ejecucion`). Las bases se refieren a ids ajenos sin claves foráneas.

### 2.3 Capas dentro de cada servicio

Todos siguen **controller → service → repository**:
- El **controller** recibe el HTTP, valida la entrada con Bean Validation (`@Valid`, `@NotNull`, `@DecimalMin`…) y autoriza con `@PreAuthorize`. No tiene reglas de negocio.
- El **service** tiene las reglas (por ejemplo `ReservaService` en Recursos o `OrdenTrabajoServiceImpl` en Operaciones) y maneja las transacciones (`@Transactional`).
- El **repository** es una interfaz de Spring Data JPA (`JpaRepository`) que habla con la base.
- Los **DTO** (records Java en `dto/` o `dtos/`) son lo que entra y sale por HTTP; las **entidades** JPA no se exponen.

---

## 3. El proceso central paso a paso

Se dispara de dos formas: el PLANIFICADOR llama a `POST /api/operaciones/habilitaciones-diarias` (o a `POST /ordenes/{id}/habilitacion` para una sola), o el **job diario** (`JobHabilitacionDiaria`, con `@Scheduled`, cron configurable en `app.jobs.habilitacion.cron`, por defecto a las 6 h) usando la cuenta SISTEMA. Los dos terminan en `HabilitacionDiariaService`, que toma las órdenes del día en estado `PROGRAMADA` o `RECURSOS_ASIGNADOS` y llama a `OrdenTrabajoServiceImpl.habilitarOrden` para cada una.

El diagrama de secuencia completo está en [`arquitectura.md`, sección 3](arquitectura.md).

**Paso 1. Pedir el contexto a Obras.** Operaciones llama a `GET /api/obras/internos/fases/{id}/contexto-ejecucion` (cliente `ObrasHttpClient`). Obras devuelve el tipo de trabajo, las coordenadas del tramo, los materiales y si la **fase previa está completa** (RN-01). Si no lo está, la orden no avanza y se informa el motivo.

**Paso 2. Pedir recursos a Recursos.** Operaciones llama a `POST /api/recursos/internos/reservas` (cliente `RecursosHttpClient`) con la orden, el tipo de trabajo, la fecha y las coordenadas del tramo. Recursos (`ReservaService.proponerYReservar`) revisa cada cuadrilla con RN-05, RN-06, RN-04 y RN-07, y la planta con RN-08, consultando OSRM para los tiempos de viaje. Si alguna sirve, crea una **reserva PROVISORIA con vencimiento de 30 minutos**, la hora de llegada de la cuadrilla y la **hora de salida del camión**. Si ninguna sirve, responde **422** con el `codigoRegla` y el motivo de cada cuadrilla. La orden pasa a `RECURSOS_ASIGNADOS`.

**Paso 3. Consultar el clima.** La orden pasa a `EVALUACION_CLIMATICA`. `OpenMeteoService` pide a Open-Meteo los datos horarios **de la fecha de la orden** y toma el **peor valor de la ventana de ejecución** (de 08:00 a 13:00 por defecto, configurable con `app.open-meteo.hora-inicio-jornada` y `ventana-ejecucion-horas`): la mayor humedad del suelo, la mayor probabilidad de lluvia y la menor temperatura.

**Paso 4. Decidir.** Se comparan esos valores con los **umbrales del tipo de trabajo** (tabla `umbrales_climaticos`, los cambia el ADMIN):
- **Cumple** → `recursosClient.confirmar(...)` confirma la reserva y la orden queda `HABILITADA`. Después el CAPATAZ la inicia.
- **No cumple** → la orden se suspende y arranca la **saga** (paso 5).
- **No hay dato confiable** (Open-Meteo caído o sin algún dato) → `PENDIENTE_CONFIRMACION_CLIMATICA` (RN-10) y decide el CAPATAZ con `PATCH /ordenes/{id}/resolucion-climatica`.

**Paso 5. Saga de suspensión** (`SuspensionService`). Una **saga** es una transacción repartida entre servicios, donde cada paso tiene una compensación en lugar de un rollback de base de datos:
1. Recursos **libera** la reserva (`PATCH /internos/reservas/{id}/liberacion`). Si Recursos no responde, se registra y la reserva vence sola por TTL.
2. Se registra la suspensión en el historial.
3. Si la fase todavía no llegó al límite de suspensiones: Obras **reprograma la fase** y corre en cascada las dependientes (RN-02), y Operaciones crea la **orden nueva** para la fecha nueva; la vieja queda `REPROGRAMADA`. La fecha nueva es la mayor entre "el día siguiente" y el fin de la fase anterior.
4. Si llegó al límite (3 por defecto, `PUT /configuracion/limite-suspensiones`): la fase se marca **en riesgo** en Obras y el PLANIFICADOR decide si se reprograma o se cancela (RN-12, `POST /fases-en-riesgo/{faseId}/decisiones`).

**Paso 6. Ejecución.** El CAPATAZ inicia la orden (`PATCH /inicio`, pasa a `EN_EJECUCION`), registra avances (`POST /avances`, que llegan a Obras) y la cierra (`PATCH /cierre`) con el consumo real de materiales y las horas de máquina. Al llegar al 100 %, la fase queda completa en Obras y la orden `COMPLETADA`. También puede **rechazarla** (`PATCH /rechazo`), lo que dispara la misma saga.

### 3.1 La máquina de estados de la orden (RN-11)

```
PROGRAMADA → RECURSOS_ASIGNADOS → EVALUACION_CLIMATICA → HABILITADA → EN_EJECUCION → COMPLETADA
                                          │                    │
                                          ├→ PENDIENTE_CONFIRMACION_CLIMATICA
                                          └→ SUSPENDIDA → REPROGRAMADA o CANCELADA
```

Las transiciones válidas están en el enum `EstadoOrden` (`ms-operaciones/.../modelo/EstadoOrden.java`). Una transición inválida lanza `TransicionInvalida`, que es una `ReglaNegocioException` con código `RN-11`, y el cliente recibe **422**. No existe un endpoint para cambiar el estado a mano: el equipo quitó `PATCH /ordenes/{id}/estado` porque permitía saltear la reserva y el clima.

---

## 4. Las doce reglas de negocio

| Regla | Qué dice | Dónde está | Qué pasa si no se cumple |
|---|---|---|---|
| RN-01 | Una fase no inicia sin la anterior completa | Obras (`FaseService`) y Operaciones (`habilitarOrden` mira `faseAnteriorCompletada`) | 409 en Obras; la orden no avanza |
| RN-02 | Correr una fase corre en cascada las dependientes; no se aceptan ciclos | Obras (`FaseService`, `PATCH /fases/{id}/programacion`) | 422 si la fecha queda antes del fin de la previa o hay un ciclo |
| RN-03 | Materiales = superficie × rendimiento del tipo de trabajo | Obras (`MaterialService`, `GET /fases/{id}/materiales`) | 422 con superficie ≤ 0 |
| RN-04 | Un operario solo opera máquinas con certificación vigente | Recursos (`ReservaService` al reservar, `CuadrillaService` al armar la cuadrilla) | 422 `RN-04` |
| RN-05 | Ni la cuadrilla ni sus recursos con dos reservas el mismo día | Recursos (`ReservaRepository.existeReservaSolapada`) | 422 `RN-05`; se prueba otra cuadrilla |
| RN-06 | La cuadrilla cumple la composición mínima del tipo de trabajo (operarios y máquinas, sin las que están en mantenimiento ese día) | Recursos (`ReservaService`, tabla `composicion_minima_trabajo`) | 422 `RN-06` |
| RN-07 | El viaje cuadrilla → tramo no supera el máximo (90 min) y la ida y vuelta entra en la jornada | Recursos (OSRM, `configuracion_desplazamiento`) | 422 `RN-07` |
| RN-08 | La mezcla llega dentro de su ventana de transporte (60 min); se calcula la hora de salida del camión = inicio − viaje − margen | Recursos (OSRM a la planta) | 422 `RN-08` |
| RN-09 | Se habilita solo si humedad del suelo y lluvia no superan el máximo y la temperatura alcanza el mínimo del tipo de trabajo | Operaciones (`habilitarOrden`, `umbrales_climaticos`) | `SUSPENDIDA` y saga |
| RN-10 | Sin dato climático confiable, no se habilita sola | Operaciones (`OpenMeteoService` no inventa datos; reintentos y fallback) | `PENDIENTE_CONFIRMACION_CLIMATICA`, decide el CAPATAZ |
| RN-11 | Solo transiciones de estado válidas | Operaciones (`EstadoOrden`) | 422 `RN-11` |
| RN-12 | N suspensiones de una fase la ponen en riesgo y exigen decisión manual | Operaciones (`SuspensionService`, `FaseEnRiesgoService`) | La fase queda en riesgo; decide el PLANIFICADOR |

Cada regla tiene tests automáticos (casos normales, límite y rechazo) y requests en la colección: la tabla completa está en [`trazabilidad-reglas.md`](trazabilidad-reglas.md). Ejemplos de tests para mostrar: `ReservaServiceReglasTest` (RN-04 a RN-08), `FaseServiceTest` (RN-01, RN-02), `SuspensionServiceTest` (saga y RN-12), `OpenMeteoServiceTest` (RN-09 y RN-10).

**Por qué los rechazos son 422 y no 400:** 400 es "el pedido está mal formado" (falta un campo, un número no es número). 422 es "el pedido está bien formado, pero el negocio no lo permite". El cliente distingue los dos casos solo por el código. El 409 se usa para conflictos de estado (por ejemplo confirmar una reserva ya liberada).

---

## 5. Integraciones externas y qué pasa cuando fallan

### 5.1 Open-Meteo (clima) — la usa solo Operaciones

- API pública **sin clave**. Variables: `soil_moisture_0_to_1cm` (humedad del suelo, **fracción en m³/m³, no porcentaje**: 0,15 es 15 % de agua en volumen), `precipitation_probability` (%) y `temperature_2m` (°C).
- Está detrás de la interfaz `InterfazClimaService` (cumple el papel de `ProveedorClima` de la propuesta): el resto de Operaciones no sabe que existe Open-Meteo, así que cambiar de proveedor es escribir otra implementación.
- **Resiliencia** (librería Resilience4j, configurada en el `application.yml` de Operaciones):
  - **Timeouts**: 3 s para conectar y 5 s para leer.
  - **Reintentos** (`@Retry`): 3 intentos con 1 s entre ellos.
  - **Circuit breaker** (`@CircuitBreaker`): si fallan muchas llamadas seguidas, deja de llamar un rato y va directo al fallback, para no colgar al servicio.
  - **Caché del último pronóstico**: 15 minutos por coordenada y fecha (`app.open-meteo.cache-ttl`).
  - **Sin dato confiable**: el fallback lanza un error y la orden queda en `PENDIENTE_CONFIRMACION_CLIMATICA`. Nunca se asume un valor (RN-10).

### 5.2 OSRM (rutas por calle) — la usa solo Recursos

- Servidor público de demostración (`https://router.project-osrm.org`). Calcula tiempo y distancia de viaje en auto. Las coordenadas van como **longitud,latitud** (al revés que casi todo): por eso `Coordenada.toOsrmString()` de commons las arma así.
- Está detrás de `ProveedorRutas`; la implementación es `OsrmProveedorRutas`.
- **Factor de corrección** 1,3 (`app.osrm.factor-correccion`): OSRM no tiene perfil de camión, así que se usa el de auto y se alarga el tiempo.
- **Resiliencia y uso responsable**:
  - **Rate limiter**: 1 pedido por segundo, que es la política del servidor público; si no hay permiso, espera hasta 3 s.
  - **Caché de rutas**: se vacía cada 6 h (`VencimientoCacheRutas`).
  - **User-Agent propio**, que identifica la aplicación.
  - **Circuit breaker con fallback**: si OSRM cae, se estima en línea recta (fórmula de Haversine) × factor y la reserva queda con `fuenteRuta: ESTIMADA`.
- **Atribución**: datos © OpenStreetMap contributors bajo licencia ODbL, en el README y en el Swagger de Recursos.

### 5.3 Cuando falla un servicio propio

- **Obras o Recursos no responden**: la orden queda como estaba (por ejemplo `PROGRAMADA`) con el motivo, y la próxima corrida del job la reintenta. Es seguro reintentar porque la reserva es **idempotente por orden**: pedirla dos veces devuelve la misma.
- **Operaciones cae en medio de una saga**: las reservas provisorias vencen a los 30 minutos y `ExpiracionReservasJob` (cada 60 s) las pasa a `EXPIRADA`. No quedan recursos bloqueados.

---

## 6. Seguridad

### 6.1 Quién es quién

Keycloak (realm `obras-viales`, importado solo al arrancar desde `docker/keycloak/`) tiene cinco usuarios de demo, uno por rol, con contraseña `<rol>123`, y una cuenta de servicio:

| Rol | Qué hace |
|---|---|
| ADMIN | Catálogos de recursos, umbrales climáticos, configuración |
| PLANIFICADOR | Obras, tramos y fases; arma cuadrillas; emite órdenes; decide fases en riesgo |
| CAPATAZ | Check-in de su cuadrilla; inicia, rechaza, registra avance y cierra órdenes; resuelve el clima incierto |
| OPERARIO | Solo lectura de sus propias asignaciones |
| AUDITOR | Solo lectura de historial, órdenes y evaluaciones |
| SISTEMA | Cuenta de servicio (client credentials): el job diario y las llamadas entre servicios |

La matriz completa está en [`matriz-permisos.md`](matriz-permisos.md).

### 6.2 Cómo viaja la identidad

1. El usuario pide un token a Keycloak (`POST /realms/obras-viales/protocol/openid-connect/token`). Keycloak devuelve un **JWT**: un texto firmado con los datos del usuario y sus roles (claim `realm_access.roles`), que vence en 5 minutos.
2. Cada pedido lleva `Authorization: Bearer <token>`.
3. El **gateway** valida la firma y la vigencia del token, y aplica **reglas gruesas por ruta y método** (`api-gateway/.../security/SecurityConfig.java`): por ejemplo, solo PLANIFICADOR puede hacer `PUT /api/obras/**`, y `/api/*/internos/**` solo deja pasar a la cuenta SISTEMA (cualquier otro rol recibe 403).
4. El **servicio vuelve a validar** el token y aplica la **autorización fina** con `@PreAuthorize` en cada endpoint. Esto es **defensa en profundidad**: si alguien llegara a un servicio sin pasar por el gateway, igual lo frena.
5. `KeycloakConversorRoles` (commons) traduce los roles del token a `ROLE_PLANIFICADOR`, etc., que es lo que entiende `hasRole(...)`.

**Respuestas:** sin token o con token vencido → **401**; con token válido pero sin el rol → **403**. Las dos salen en el formato estándar de error (sección 8), también desde el gateway.

**Tres casos a probar siempre**, para cualquier endpoint: sin token (401), rol insuficiente (403), rol correcto (2xx). Los tests parametrizados `SeguridadEndpointsTest` (Operaciones) y `CatalogoControllerSeguridadTest` (Recursos) lo hacen con los seis roles.

### 6.3 Secretos

Ninguna contraseña está en el código: los `application.yml` usan `${VARIABLE}` y el compose las toma de `.env`, que **no se commitea**. `.env.example` tiene los valores de demo. Spring no lee `.env`: si corrés un servicio sin Docker, tenés que definir las variables en la terminal (`$env:OBRAS_DB_PASSWORD="obras_pass"`).

---

## 7. Datos y persistencia

- **Un PostgreSQL con una base por servicio** (`ms_obras`, `ms_recursos`, `ms_operaciones`), cada una con **su usuario dueño** y sin acceso para los demás (`docker/init-db/01-init-schemas.sql`, con `REVOKE CONNECT ... FROM PUBLIC`). Ningún servicio puede leer la base de otro aunque quiera.
- **Flyway** versiona el esquema: cada cambio es un archivo `V<n>__descripcion.sql` en `src/main/resources/db/migration`. Flyway guarda qué versiones aplicó y un checksum de cada una: **una migración ya aplicada no se edita nunca**, se agrega otra. Hibernate está en `ddl-auto: validate`, o sea, solo verifica que las entidades coincidan con las tablas.
- **Ids `BIGSERIAL`** (enteros autoincrementales), no uuid como decía el cuestionario: es más simple y los ids no cruzan bases como claves foráneas.
- **Datos de demo**: obra "Ruta Provincial 34" con dos tramos; cuadrillas Alfa (con operarios certificados) y Beta Lejana (sin operarios); una planta de mezcla; umbrales por tipo de trabajo; y tres órdenes sembradas con una fecha cada una (hoy, mañana y pasado mañana, en hora de Argentina). La tercera está en el tramo de Río Cuarto, lejos de la cuadrilla, para mostrar el rechazo por distancia (RN-07).
- El ER de cada base está en [`arquitectura.md`, sección 2](arquitectura.md).

---

## 8. La librería común `obras-commons`

Es un **JAR Maven** (módulo `obras-commons/`) que usan los tres servicios. Se registra solo en cada servicio con `META-INF/spring/...AutoConfiguration.imports`, así no hace falta escanear su paquete.

**Qué tiene (lo técnico y transversal):**
- `KeycloakConversorRoles` y `BaseResourceServerConfig`: configuración de seguridad común (JWT, sin sesiones, Swagger y `/actuator/**` abiertos, el resto autenticado).
- `Roles`: las constantes de los seis roles.
- `GlobalExceptionHandler`: convierte cualquier error en el **formato estándar** RFC 7807 (`application/problem+json`) con `type`, `title`, `status`, `detail`, `instance`, `timestamp` y `correlationId`. Traduce `RecursoNoEncontradoException` a 404, `ConflictoEstadoException` a 409, `ReglaNegocioException` a 422 (con `codigoRegla`), los errores de validación a 400 (con el detalle por campo), ruta inexistente a 404, método no permitido a 405, y cualquier otra cosa a 500 sin mostrar el detalle interno (lo deja en el log).
- `ProblemDetailAuthenticationEntryPoint` y `ProblemDetailAccessDeniedHandler`: el 401 y el 403 en el mismo formato.
- `CorrelationIdFilter`: pone el id de correlación en el MDC (el contexto de logs) y en la respuesta.
- `SistemaTokenService` y `sistemaRestClient`: cliente HTTP que pide el token de SISTEMA a Keycloak (lo guarda hasta que vence, con timeouts y reintento) y lo adjunta en cada llamada entre servicios.
- `Coordenada`: value object que valida latitud y longitud y arma el par para OSRM. Lo usan Recursos y Operaciones.

**Qué NO tiene, a propósito:** entidades, lógica de negocio ni los DTO de contrato entre servicios. Si Operaciones y Recursos compartieran la clase de la reserva, cualquier cambio en Recursos obligaría a recompilar y redesplegar Operaciones: eso es acoplamiento, un "monolito distribuido". Cada consumidor define su propio DTO con los campos que usa (por ejemplo `ReservaDTO` en Operaciones y `ReservaResponse` en Recursos).

---

## 9. Trazabilidad: el correlation-id

Cada pedido que entra por el gateway recibe un **id de correlación** nuevo (`X-Correlation-Id`, generado por `api-gateway/.../filter/CorrelationIdFilter.java`; el gateway ignora el que mande el cliente). El id viaja a los servicios, aparece en cada línea de log (patrón `logging.pattern.correlation` en los `application.yml`) y vuelve en la respuesta y en el cuerpo de los errores. Así, con un solo id se sigue un pedido por todos los servicios.

---

## 10. Pruebas

- **Tests automáticos**: `mvn clean test` desde la raíz (necesita Postgres arriba y las variables de las contraseñas). Hay tests de cada regla, de seguridad (401/403/2xx por rol), de los clientes HTTP y de los errores.
- **APIs externas simuladas**: Open-Meteo, OSRM y los otros servicios se simulan con `MockRestServiceServer` de Spring, que intercepta el cliente HTTP y responde lo que el test indica, sin red. (La propuesta mencionaba WireMock; se eligió esto porque cumple lo mismo con una dependencia menos.)
- **Colección Postman** (`coleccion/`): 111 requests organizadas en `00-Auth`, `01-Setup-Datos`, `02-Seguridad`, `03-Flujo-Principal`, `04-Reglas-Negocio` y `05-Fallas-y-Resiliencia`, cada una con su test. Se corre con `npx newman run coleccion/obras-viales.postman_collection.json -e coleccion/local.postman_environment.json` sobre el stack recién levantado (`docker compose down -v` antes, porque el flujo principal completa la orden 1).
- **Swagger**: cada servicio documenta su API con springdoc. La interfaz se ve corriendo el servicio por terminal (`http://localhost:8083/swagger-ui.html`); en Docker los servicios no se publican.

---

## 11. Decisiones importantes y sus alternativas

La rúbrica evalúa que se puedan justificar las decisiones. Estas son las que más probablemente pregunten (el detalle de todas está en [`DECISIONES.md`](DECISIONES.md)):

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| **Orquestación** de la saga desde Operaciones | Coreografía con eventos (cada servicio reacciona a mensajes) | Un solo lugar muestra el flujo completo; no hace falta un broker de mensajes; más fácil de seguir y probar |
| Una **base por servicio** en un mismo PostgreSQL | Una base compartida | Cada servicio es dueño de sus datos; un PostgreSQL alcanza para la demo |
| Reserva **provisoria con TTL** | Reservar en firme y deshacer a mano | Si algo falla a mitad de camino, los recursos se liberan solos |
| Rechazos con **422 y `codigoRegla`** | 200 con un campo "rechazada" | El cliente decide por el código HTTP |
| Clima con el **peor valor de la ventana** | El valor de la hora actual o el promedio | Una sola hora de lluvia arruina el trabajo; la orden es para su fecha, no para "ahora" |
| **Spring Boot 4.1.2** con Java 25 | Spring Boot 3.2 (lo que decía el cuestionario) | 3.2 no soporta Java 25 |
| Gateway que **autentica y aplica reglas gruesas**, servicios con `@PreAuthorize` | Confiar solo en el gateway | Defensa en profundidad |
| Rutas del gateway **sin recortar el prefijo** | `StripPrefix` | Los controladores ya mapean `/api/<servicio>/...`; la ruta es la misma adentro y afuera |
| **OSRM público** | Instancia propia en Docker (mapa de Argentina de ~3 GB) | La propuesta dice que la instancia propia es una extensión posible, no un requisito |
| Swagger **no publicado** en Docker | Publicar los puertos de los servicios | Rompería la trusted zone; Swagger se ve corriendo el servicio por terminal |

**Diferencias con lo comprometido** (anotadas en [`matriz-requisitos.md`](matriz-requisitos.md), sección 4): versión de Spring Boot, BIGSERIAL en lugar de uuid, RN-11 y RN-05 con 422 en lugar de 409, RN-07 sin estado `SIN_RECURSOS`, HTTP en lugar de HTTPS, solo `/route` de OSRM (no `/table`), sin confirmación del CAPATAZ cuando la ruta es estimada, `MockRestServiceServer` en lugar de WireMock y las horas de máquina sin llegar a Recursos. Los cambios significativos hay que **avisarlos a la cátedra** (documento de Alcance, §9): anotarlos no alcanza.

---

## 12. Preguntas para practicar

Respondelas en voz alta sin mirar. Las respuestas cortas están en [`guion-demo.md`, sección 5](guion-demo.md); acá van otras para completar.

1. **¿Qué pasa exactamente cuando el PLANIFICADOR dispara la habilitación de hoy?** Recorré los seis pasos de la sección 3 diciendo qué servicio actúa en cada uno.
2. **¿Cómo sabe Operaciones si la fase previa está completa, si no puede leer la base de Obras?** Se lo pregunta por HTTP: el contexto de ejecución trae `faseAnteriorCompletada`.
3. **¿Por qué la reserva vence a los 30 minutos?** Para que una saga interrumpida no deje recursos bloqueados.
4. **¿Qué es idempotente y dónde se usa?** Una operación que repetida da el mismo resultado: proponer una reserva para la misma orden, confirmar y liberar. Permite reintentar sin miedo.
5. **¿Por qué la humedad del suelo se compara con 0,40 y no con 40?** Porque Open-Meteo la da como fracción volumétrica (m³/m³).
6. **¿Qué ve el cliente si OSRM está caído?** La reserva se crea igual con la estimación en línea recta y `fuenteRuta: ESTIMADA`.
7. **¿Qué pasa si se suspende la misma fase tres veces?** La fase queda en riesgo y el PLANIFICADOR decide.
8. **¿Por qué un OPERARIO recibe 403 al listar órdenes, si está logueado?** Porque autenticado no es autorizado: el endpoint exige PLANIFICADOR, CAPATAZ o AUDITOR.
9. **¿Cómo se demuestra un error de integración controlado?** Apagando un servicio (`docker compose stop ms-recursos`) o apuntando Open-Meteo a un host inválido, y mostrando que la orden queda en un estado coherente.
10. **¿Por qué no se edita una migración vieja?** Flyway guarda su checksum; si cambia, el servicio no arranca.
11. **¿Qué compartieron en commons y qué no, y por qué?** Sección 8.
12. **¿Qué es la trusted zone y por qué solo se publica el gateway?** Sección 2.1 y 6.2.

---

## 13. Cómo levantarlo y probarlo (resumen)

```powershell
docker compose down -v
docker compose up -d --build
docker compose ps
```

Esperá los seis contenedores en `(healthy)`. Después:
- Colección: `npx newman run coleccion/obras-viales.postman_collection.json -e coleccion/local.postman_environment.json`.
- Tests: con Postgres y Keycloak arriba, definí `OBRAS_DB_PASSWORD`, `RECURSOS_DB_PASSWORD`, `OPERACIONES_DB_PASSWORD` y `SERVICIO_SISTEMA_SECRET` (valores en `.env.example`) y corré `mvn clean test`.
- Panel interno del equipo (no es parte de la entrega): `herramientas/panel-equipo/`, ver su README.

El detalle está en el [README](../README.md).
