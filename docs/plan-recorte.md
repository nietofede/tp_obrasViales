# Plan del recorte de alcance

Hecho primero en una copia de trabajo (`tp_obrasViales-recorte`, rama `recorte/alcance-reducido`, salida de `main` = `5e22a2a`, sin commits) y después pasado al repo del equipo en la rama `feat/recorte-alcance` (fase B, al final). La cátedra aprobó el recorte el 2026-11-16. Lo que se recorta es lo del mail `docs/aviso-cambios-catedra.md` (54 → 32 endpoints: 31 del cuestionario más `GET /obras/{id}/tramos`; la habilitación de una orden suelta se recuperó el 2026-11-15), más los extras no comprometidos que nadie usa.

Reglas: no se toca el proceso central ni se elimina ninguna regla de negocio; las migraciones ya aplicadas no se editan (los cambios de esquema van en migraciones nuevas); cada módulo queda con sus tests en verde antes de pasar al siguiente.

## Tareas

### 1. ms-recursos (20 → 7 endpoints)
- [x] Quitar `POST /maquinas`, `PUT /maquinas/{id}`, `POST /operarios`, `POST /operarios/{id}/certificaciones`, `POST` y `GET /plantas`, `GET /operarios/me/asignaciones`.
- [x] Quitar `POST /cuadrillas`, `PUT /cuadrillas/{id}` y el extra `GET /cuadrillas/{id}`.
- [x] Quitar `ConfiguracionController` entero (composición mínima y desplazamiento) y `ConfiguracionService`.
- [x] Quitar `GET /reservas/{id}` (`ReservaConsultaController`) y `ReservaService.obtener`.
- [x] Quitar el chequeo de mantenimiento al reservar, la entidad y el repositorio de `Mantenimiento`, y el filtro `?fecha=` de máquinas; migración nueva que borra la tabla `mantenimientos`.
- [x] Quedan: `GET /maquinas`, `GET /operarios`, `GET /cuadrillas`, `POST /cuadrillas/{id}/ubicaciones` y las 3 rutas internas de reservas.
- [x] Tests: borrar los de lo quitado y ajustar los que quedan.
- Control: `mvn -pl ms-recursos -am clean test` verde.

### 2. ms-obras (15 comprometidos → 11, más 2 extras)
- [x] Quitar `PUT /obras/{id}`, `PUT /tramos/{id}`, `GET /tramos/{id}`, `GET /fases/{id}`.
- [x] Quitar los extras `GET /tramos/{id}/fases` y `POST /fases/{id}/iniciar` (el avance desde Operaciones ya pone la fase en curso).
- [x] Se mantiene `GET /obras/{id}/tramos`: lo usa Operaciones para filtrar órdenes por obra.
- [x] Tests: borrar los de lo quitado y ajustar los que quedan.
- Control: `mvn -pl ms-obras -am clean test` verde.

### 3. ms-operaciones (19 → 13 endpoints)
- [x] Quitar `PATCH /ordenes/{id}/cancelacion` y `PATCH /ordenes/{id}/reprogramacion`.
- [x] Quitar `PUT /configuracion/limite-suspensiones` (el límite queda en 3, sembrado).
- [x] RN-12 simplificada: quitar `GET /fases-en-riesgo`, `POST /fases-en-riesgo/{faseId}/decisiones`, `FaseEnRiesgoService` y la entidad. A la tercera suspensión la fase queda en riesgo en Obras y la orden SUSPENDIDA; cuando el PLANIFICADOR crea una orden nueva para esa fase, las órdenes suspendidas de la fase pasan a REPROGRAMADA y el conteo de suspensiones vuelve a cero.
- [x] Cierre simplificado: quitar `PATCH /ordenes/{id}/cierre`, `CierreOrden` y su consumo; cuando el avance llega al 100 % la orden pasa a COMPLETADA.
- [x] Quitar la caché del último pronóstico en `OpenMeteoService` (quedan reintentos y circuit breaker).
- [x] Migración nueva que borra `fases_en_riesgo`, `cierres_orden` y `cierre_consumos`.
- [x] Tests: borrar los de lo quitado, ajustar `SeguridadEndpointsTest`, `SuspensionServiceTest`, `EjecucionServiceTest` y los de clima.
- Control: `mvn -pl ms-operaciones -am clean test` verde.

### 4. Gateway, roles y realm
- [x] Quitar el rol OPERARIO: constante en `Roles` (commons), reglas del gateway, realm de Keycloak (rol y usuario de demo) y cualquier `@PreAuthorize` que lo nombre.
- [x] Ajustar las reglas por ruta y método del gateway a los endpoints que quedan y sus tests.
- Control: `mvn -pl api-gateway -am clean test` verde.

### 5. Tests de todo el proyecto
- Control: `mvn clean test` desde la raíz, los 5 módulos en verde.

### 6. Colección, documentación y herramientas
- [x] Colección Postman: quitar los requests de lo eliminado y pasar el cierre a avance del 100 %.
- [x] `docs/`: matriz de permisos, matriz de requisitos, trazabilidades, arquitectura (ER y secuencia), guía de estudio, guion de demo, README y una entrada en DECISIONES.md.
- [x] Panel del equipo: sacar del catálogo lo eliminado.
- Control: buscar en el repo referencias a rutas eliminadas (`grep`) y que no quede ninguna fuera de DECISIONES.md.

### 7. Verificación desde cero
- [x] Stack temporal con Docker (puertos distintos al stack del equipo), los 6 contenedores `healthy`.
- [x] Colección completa con Newman, en verde.
- [x] Proceso central a mano: habilitar el día, suspensión por clima, rechazo por distancia, avance al 100 % que completa la orden, tercera suspensión que deja la fase en riesgo y orden nueva del PLANIFICADOR.

## Registro

(Se completa a medida que se avanza. Los tests se corren sin `*ApplicationTests` porque Docker estaba cerrado: ese test arranca la app contra Postgres y queda para el paso 7.)

- **1. ms-recursos** — hecho. 7 endpoints. Borrados `ConfiguracionController`, `ReservaConsultaController`, `ConfiguracionService`, `ConfiguracionDtos`, `Mantenimiento` y su repositorio; `CatalogoService`, `CuadrillaService`, `CatalogoController`, `CuadrillaController`, `Permisos` y `CatalogoDtos` reducidos; RN-06 ya no mira mantenimiento; migración `V3__quitar_mantenimientos.sql`. Control: 93 tests verdes.
- **2. ms-obras** — hecho. 12 endpoints (11 del cuestionario + `GET /obras/{id}/tramos`). RN-01 sigue en `FaseService.registrarAvance` (primer avance) y en Operaciones (`faseAnteriorCompletada`). Control: 105 tests verdes.
- **3. ms-operaciones** — hecho. 12 endpoints. Borrados `ConfiguracionController`, `FaseEnRiesgoController`, `FaseEnRiesgoService`, `FaseEnRiesgo`, `CierreOrden`, `ConsumoMaterial`, sus repositorios y DTOs, y `ReprogramacionOrdenRequest`. RN-12: a la tercera suspensión `SuspensionService` marca la fase en riesgo en Obras y la orden queda SUSPENDIDA; `crearOrden` llama a `cerrarSuspensionesDeLaFase` (suspendidas → REPROGRAMADA y `marcarNoVigentes`). Cierre: `registrarAvance` deja la orden COMPLETADA cuando Obras devuelve la fase COMPLETADA (la respuesta suma `estadoOrden`). `OpenMeteoService` sin caché (constructores sin `ttl` ni `Clock`). Migración `V6__recorte_alcance.sql`. Tests: borrados `FaseEnRiesgoServiceTest`, `OpenMeteoServiceCacheTest` y `OrdenTrabajoServiceCancelacionTest`; nuevos: avance al 100 % completa la orden, orden completada no acepta avances, cierre de suspensiones de la fase (2), las 8 rutas quitadas dan 404. Control: 124 tests verdes.
- **Cuenta de endpoints:** 7 + 12 + 12 = 31 en la API, de los cuales 30 son del cuestionario (el mail dice 54 → 31: coincide con lo desplegado si se cuenta el extra `GET /obras/{id}/tramos`).
- **4. Roles y gateway** — hecho. `Roles.OPERARIO` borrado; realm sin el rol ni el usuario `operario`; gateway: POST de obras solo PLANIFICADOR, PATCH de operaciones solo CAPATAZ, GET de recursos sin SISTEMA, POST de recursos solo CAPATAZ y sin regla PUT (cae en `denyAll`). Los tests que usaban OPERARIO como rol insuficiente pasan a ADMIN, SISTEMA o PLANIFICADOR. El `rol_operativo` "OPERARIO" de la tabla `operarios` de Recursos es otra cosa (puesto en la cuadrilla) y queda.
- **5. Tests de todo el proyecto** — hecho sin los `*ApplicationTests`: gateway 40, commons 36, ms-obras 105, ms-recursos 93, ms-operaciones 124 (398, 0 fallos). Los 4 `contextLoads` quedan para el paso 7 (necesitan Postgres).
- **6. Colección, docs y panel** — hecho. Colección: 111 → 81 requests (34 quitados; nuevos: orden 1 COMPLETADA tras el avance 100, check-in del PLANIFICADOR 403, 404 de cierre y cancelación); RN-01 pasa al primer avance por el endpoint interno (409), RN-04 a una reserva con fecha 2028-03-01 (las certificaciones del seed vencen el 2027-12-31: 422 RN-04), RN-06 a un tipo sin composición (422 RN-06) y RN-05 además comprueba que Beta Lejana figure con RN-06. Docs: matriz de permisos reescrita (31 endpoints, sin OPERARIO), matriz de requisitos (F-01, F-04, F-13, F-14 reducidas, F-15 fuera, escenario 4 con CAPATAZ), trazabilidades, C1 y ER de arquitectura, guía, guion, README, CONSIDERACIONES, ms-obras-api (filas tachadas) y entrada «Recorte de alcance» al final de DECISIONES.md. Panel: 28 endpoints, sin OPERARIO. Control por `grep`: las rutas quitadas solo aparecen en textos que explican que se quitaron y en los tests de 404.
- **Sin hacer:** `herramientas/app-govp/` (está sin commitear en la carpeta del equipo, no en esta copia) usa pantallas de lo quitado; hay que adaptarla si se adopta el recorte. La página de estudio publicada sigue describiendo el alcance anterior.
- **Limpieza (2026-11-14):** se borró el código que quedó sin uso tras el recorte (detectado al escribir `docs/guia-del-codigo.md`): `CronogramaService` y su test vacío, `ObraService.buscarTramo`, `FaseRepository.findByTramoId*`, `TramoRepository.findByObraId`, `CertificacionRepository.findByOperarioId`, `ReservaRepository.findByCuadrillaIdAndFechaReserva`, `OrdenTrabajoRepository.findByFaseId`, `UmbralClimaticoService.obtenerUmbralPorTipo`, `EstadoOrden.puedeTransicionarHacia` e imports que quedaron sueltos. Control: obras 105, recursos 93, operaciones 124 y commons 36, todos verdes.
- **7. Verificación desde cero** — hecho el 2026-11-14 (stack temporal `govp-recorte`, puertos 15432/18080/18081, sin tocar el del equipo; borrado al terminar). Flyway aplicó desde cero Recursos v1 a v3 (sin `mantenimientos`) y Operaciones v1 a v6 (sin `fases_en_riesgo`, `cierres_orden` ni `cierre_consumos`); los 6 contenedores `healthy` (`ddl-auto: validate` acepta las entidades). Los 4 `*ApplicationTests` contra ese Postgres: 4 de 4. Newman (con los umbrales de BASE permisivos antes, como indica el guion): 81 requests, 98 aserciones, 0 fallos, incluidas las nuevas (RN-04 con fecha 2028, RN-06 sin composición, Beta Lejana RN-06, orden COMPLETADA al 100 %, RN-01 por el avance interno, 404 de rutas quitadas). RN-12 a mano (script fuera del repo): fase RELEVAMIENTO nueva con umbrales imposibles, tres habilitaciones: órdenes 6 y 7 REPROGRAMADA (la saga crea la siguiente), la 8 SUSPENDIDA sin orden nueva, fase EN_RIESGO en Obras, 3 suspensiones en el historial y reservas 6 a 8 LIBERADA; el PLANIFICADOR reprograma la fase (vuelve a PENDIENTE) y crea la orden 9: la 8 pasa a REPROGRAMADA y las suspensiones quedan no vigentes. Con umbrales normales la orden 9 (a 15 días) quedó PENDIENTE_CONFIRMACION_CLIMATICA porque Open-Meteo no trae todos los datos horarios a esa distancia (RN-10, comportamiento correcto); el CAPATAZ la resolvió con HABILITAR → HABILITADA y reserva CONFIRMADA.
- **Habilitación suelta recuperada (2026-11-15):** a pedido del equipo vuelve `POST /api/operaciones/ordenes/{id}/habilitacion` (PLANIFICADOR o SISTEMA, mismo `habilitarOrden` que el lote; el job diario sigue). La API queda en 32 endpoints (Operaciones 13). Fila nueva en `SeguridadEndpointsTest` (sale de la lista de rutas quitadas), dos requests en la colección (83: CAPATAZ → 403 y orden COMPLETADA → 422 RN-11; [P] 2026-11-15 en stack temporal desde cero: Newman 83 requests, 101 aserciones, 0 fallos), panel, mail (sale la línea y 54 → 32), matrices, guías y una entrada en DECISIONES.

## Fase B: rama `feat/recorte-alcance` en el repo del equipo (2026-11-16)

La cátedra respondió al mail que el recorte "suena razonable" y que se achique con los criterios que se evalúan. Esta fase lleva el recorte al repo del equipo y lo integra a `main`.

- [x] B1. Rama `feat/recorte-alcance` desde `main` (`5e22a2a`). La guía del alcance completo, que no estaba versionada, se guardó fuera del repo, en `../respaldo-alcance-completo/guia-del-codigo-alcance-completo.md`.
- [x] B2. Traer los cambios de la copia (parche de 100 archivos y las 3 migraciones o guías nuevas). Control: hash de git de los 272 archivos idéntico al de la copia.
- [x] B3. Buscar más cosas para sacar: código, configuración, dependencias y documentación que quedaron sin uso tras el recorte. Solo cosas internas: los endpoints y las funciones son los que se avisaron a la cátedra (54 → 32) y no se recortan más sin avisar.
- [x] B4. Commits por paso (commons/gateway/realm, recursos, obras, operaciones, colección y documentación).
- [x] B5. `mvn clean test` de los 5 módulos, con los `*ApplicationTests` contra un Postgres temporal.
- [x] B6. Stack desde cero en un proyecto Docker temporal (sin tocar el del equipo) y la colección con Newman.
- [x] B7. Merge `--no-ff` a `main` y verificación del resultado.

### Registro de la fase B
- **B1 y B2** — hecho. Parche de la copia aplicado con `git apply --index`; las 3 migraciones nuevas y la guía copiadas aparte. Control: hash de git de los 272 archivos versionados o nuevos idéntico entre la rama y la copia.
- **B3** — hecho. Búsqueda con scripts de métodos sin llamadas, tipos sin referencias, claves `app.*` sin leer, constantes de `Permisos` y `Roles`, dependencias y rutas quitadas en la app interna. Se borró: `Cuadrilla.operarios` y `Cuadrilla.maquinas` (sin uso desde que no se arman cuadrillas) y la dependencia `wiremock-spring-boot` del pom de Recursos (ningún test la usaba). Se adaptó `herramientas/app-govp/` (no versionada, no es entrega): sin pantallas de fases en riesgo, asignaciones, plantas, composición y desplazamiento; máquinas, operarios y cuadrillas solo lectura (más el check-in); sin editar obra o tramo ni iniciar fase; la orden sin cierre, reprogramación ni cancelación, sin GET sueltos de fase y reserva, y el avance informa el estado de la orden; `node --check` sin errores. Se revisó y se deja (motivo en DECISIONES): `ContextoFaseDTO.materiales`, la tabla `configuracion_suspensiones` y los setters de entidades. Se registró la respuesta de la cátedra en `docs/aviso-cambios-catedra.md` y en DECISIONES. Visto de paso, sin tocar: el pom de Recursos fija `spring-boot-starter-cache` en 3.3.4 (artefacto de Boot 3 sobre Boot 4.1.2); funciona en los tests y no es parte del recorte.
- **B5** (se corrió antes de commitear, para no dejar commits rotos) — hecho. Postgres 16-alpine temporal en el 5437 creado con `docker/init-db` y los valores de `.env.example`; cada servicio apuntado a su base con `SPRING_DATASOURCE_URL`. `mvn clean install -DskipTests` y después `mvn test` por módulo: gateway 41, commons 36, ms-obras 106, ms-recursos 94 y ms-operaciones 127, todos con 0 fallos (404 tests, incluidos los 4 `contextLoads`: Flyway aplica V1 a V6 y `ddl-auto: validate` acepta las entidades sin las listas de `Cuadrilla`). Contenedor borrado.
- **B4** — hecho. Seis commits en `feat/recorte-alcance`, agregados con rutas puntuales: seguridad (commons, gateway y realm), ms-recursos, ms-obras, ms-operaciones, colección y panel, documentación. Control: ningún archivo fuera de esas carpetas ni archivos de prueba (`.log`, `cuerpo.json`, `.env`); `herramientas/app-govp/` queda sin versionar, como antes.
- **B6** — hecho. Stack completo desde cero en el proyecto Docker `govp-recorte` (compose sin `container_name`, puertos 15432, 18080 y 18081; el del equipo no se tocó): 6 contenedores `healthy`. Con los umbrales de BASE permisivos (ADMIN, como dice el guion), Newman: 83 requests, 101 aserciones, 0 fallos. Flyway quedó en Obras v5, Recursos v3 y Operaciones v6; `fases_en_riesgo`, `cierres_orden`, `cierre_consumos` y `mantenimientos` no existen; el usuario `operario` ya no puede ingresar (401). Stack borrado con `down -v`.
- **B7** — merge `--no-ff` de `feat/recorte-alcance` a `main` (local; el push lo hace el equipo). Quien tenga un volumen viejo de Postgres tiene que recrearlo (`docker compose down -v`): las migraciones V3 de Recursos y V6 de Operaciones borran tablas.

## Fase C: quitar RN-04 y guía de defensa (2026-11-16, rama `feat/quitar-rn04`)

- [x] C1. Quitar RN-04 del motor, la entidad, el repositorio, el DTO y los tests; migración `V4__quitar_rn04_y_restos_del_recorte.sql`.
- [x] C2. Colección (82 requests), panel, app interna y documentación; entrada en DECISIONES y borrador del aviso.
- [x] C3. Barrido de restos de todo lo quitado (recorte y RN-04).
- [x] C4. Tests de los 5 módulos, stack desde cero y colección.
- [x] C5. Guía de defensa (`docs/guia-de-defensa.md`).
- [ ] C6. Commits y merge a `main`.

### Registro de la fase C
- **C1 y C2** — hecho. En el código solo quedan menciones a `certificaciones` en V1 y V2 (aplicadas, no se editan) y en la V4 que borra la tabla. Tests de Recursos sin la base: verdes. En la documentación, las menciones que quedan son las que dicen que RN-04 se quitó y las entradas viejas de DECISIONES y el mail enviado, que son historia.
- **C3** — hecho. Barrido con `git grep` de las rutas, clases, roles y tablas quitados en código, configuración, tests, colección, herramientas y documentación vigente (sin contar DECISIONES, este plan y el mail enviado, que son historia, ni los tests y requests que comprueban el 404 de las rutas quitadas). Restos encontrados y corregidos: (1) datos del rol OPERARIO en Recursos: la V2 siembra operarios con usuario de Keycloak `operario` y `operario2` (ya no existen) y `rol_operativo` tenía 'OPERARIO' por defecto; se corrige en la migración nueva, que pasa a llamarse `V4__quitar_rn04_y_restos_del_recorte.sql`, y en la entidad (PEON); (2) el script `ms-obras/scripts/e2e-ms-obras.ps1` pedía token para `operario` (fallaba al empezar) y llamaba 6 rutas quitadas: se sacaron esas llamadas (los números quedan con huecos, igual que las filas tachadas de `docs/ms-obras-api.md`), el test 16 espera 250 m3 (sin la edición del tramo la superficie queda en 1000 m2) y el 35 usa `capataz`; (3) `docs/trazabilidad-ms-obras.md` seguía con el alcance del 12/11 (rutas quitadas marcadas como hechas): reescrita; (4) `docs/guion-demo.md` describía el cierre con horas de máquina y materiales; (5) `docs/guia-de-estudio.md` nombraba `FaseEnRiesgoService` y una regla del gateway para `PUT /api/obras/**` que ya no existe. Se comprobó con un script que las guías no nombran clases del proyecto que ya no existan (los que aparecen son records anidados, clases de Spring o código citado como borrado).
- **C4** — hecho. Postgres temporal (5437): gateway 41, commons 36, ms-obras 106, ms-recursos 93 (dos de RN-04 menos, uno nuevo de RN-06) y ms-operaciones 127, todos con 0 fallos, incluidos los 4 `contextLoads`. Flyway de Recursos llegó a la v4 y los operarios quedaron con usuario `capataz`, vacío y vacío. Stack desde cero `govp-rn04` (15432, 18080, 18081 y Obras en 18082 solo para el script; borrado): 6 contenedores `healthy`; Newman 82 requests, 98 aserciones, 0 fallos (con umbrales de BASE permisivos); `ms-obras/scripts/e2e-ms-obras.ps1` corregido, en una copia con los puertos del stack y el secreto de `.env.example`: 32 de 32 OK; `GET /api/recursos/operarios` sin certificaciones y la tabla `certificaciones` no existe.
- **C5** — hecho. `docs/guia-de-defensa.md` (739 líneas), enlazada desde el README: el sistema en una página, arquitectura y trusted zone, los mecanismos de Spring con el código del repo (contenedor e inyección, estereotipos, los cinco `@Bean` uno por uno, auto-configuración de commons, configuración externa y perfiles), capa web y validación, errores RFC 7807 (por qué 401 y 403 se escriben a mano), seguridad (JWT, resource server, conversor de roles, cadena de filtros, `@PreAuthorize`, reglas del gateway), persistencia (entidades, `@Version`, Spring Data, `@Transactional` y autoinvocación, Flyway), clientes entre servicios, Open-Meteo, Resilience4j, OSRM con caché y rate limiter, tareas programadas, correlation-id, las 11 reglas, estados, reservas con TTL, saga, fallas, Docker, tests y preguntas de defensa. Control con script: todo método y clase del proyecto que nombra existe en el código (lo que no aparece son nombres de configuración, atributos de anotaciones o clases de Spring).

