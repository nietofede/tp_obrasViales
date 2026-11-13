# Trazabilidad de ms-recursos: requisito, implementación y evidencia

Sirve para ALC-01 y NEG-02 de la rúbrica v05 («matriz de trazabilidad enunciado/compromiso → implementación → evidencia» y «regla → test → request»). Alcance: lo que el equipo comprometió para MS-Recursos en el cuestionario (secciones 04 y 07) y las reglas RN-04 a RN-08.

Estado: ✔ cumplido y con evidencia · ≈ cumplido con una diferencia anotada · ✘ pendiente.

Todas las rutas llevan el prefijo `/api/recursos` (el gateway no recorta el prefijo). La columna «Request» es la llamada HTTP que demuestra la regla; la colección Bruno/Postman (paso 12) tiene que usar estos mismos nombres.

## 1. Endpoints comprometidos (sección 07)

| # | Comprometido | Implementado | Roles | Evidencia | Estado |
|---|---|---|---|---|---|
| 1 | POST /maquinas | POST /api/recursos/maquinas | ADMIN | CatalogoServiceTest, CatalogoControllerValidacionTest, CatalogoControllerSeguridadTest | ✔ |
| 2 | GET /maquinas, disponibilidad en una fecha | GET /api/recursos/maquinas?fecha= | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | CatalogoServiceTest, CatalogoControllerSeguridadTest | ≈ el ADMIN se suma a los tres roles del cuestionario; «disponibilidad» es el campo `enMantenimiento` de esa fecha |
| 3 | PUT /maquinas/{id} | PUT /api/recursos/maquinas/{id} | ADMIN | CatalogoServiceTest, CatalogoControllerSeguridadTest | ≈ el código de la máquina no se modifica |
| 4 | POST /operarios | POST /api/recursos/operarios | ADMIN | CatalogoServiceTest, CatalogoControllerValidacionTest | ✔ |
| 5 | GET /operarios, con certificaciones y disponibilidad | GET /api/recursos/operarios | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | CatalogoServiceTest, CatalogoControllerSeguridadTest | ≈ trae las certificaciones pero no la disponibilidad del día |
| 6 | POST /operarios/{id}/certificaciones | POST /api/recursos/operarios/{id}/certificaciones | ADMIN | CatalogoServiceTest, CatalogoControllerValidacionTest | ✔ |
| 7 | GET /operarios/me/asignaciones | GET /api/recursos/operarios/me/asignaciones?fecha= | OPERARIO | CatalogoServiceTest, CatalogoControllerSeguridadTest | ✔ |
| 8 | POST /plantas | POST /api/recursos/plantas | ADMIN | CatalogoServiceTest, CatalogoControllerValidacionTest | ✔ |
| 9 | GET /plantas | GET /api/recursos/plantas | ADMIN, PLANIFICADOR | CatalogoControllerSeguridadTest | ✔ |
| 10 | POST /cuadrillas, valida certificaciones y composición | POST /api/recursos/cuadrillas | PLANIFICADOR | CuadrillaServiceTest, CatalogoControllerValidacionTest | ≈ valida RN-04 y que haya al menos un operario; la composición mínima por tipo se evalúa al reservar (RN-06) |
| 11 | GET /cuadrillas, con ubicación vigente | GET /api/recursos/cuadrillas y /{id} | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | CatalogoControllerSeguridadTest | ✔ |
| 12 | PUT /cuadrillas/{id} | PUT /api/recursos/cuadrillas/{id} | PLANIFICADOR | CuadrillaServiceTest | ✔ |
| 13 | POST /cuadrillas/{id}/ubicaciones | POST /api/recursos/cuadrillas/{id}/ubicaciones | CAPATAZ | CuadrillaServiceTest, CatalogoControllerSeguridadTest | ≈ solo el capataz de esa cuadrilla (403 para otro) |
| 14 | PUT /tipos-trabajo/{tipo}/composicion-minima | PUT /api/recursos/tipos-trabajo/{tipo}/composicion-minima | ADMIN | ConfiguracionServiceTest, CatalogoControllerSeguridadTest | ✔ |
| 15 | PUT /configuracion/desplazamiento | PUT /api/recursos/configuracion/desplazamiento | ADMIN | ConfiguracionServiceTest, CatalogoControllerValidacionTest | ✔ |
| 16 | GET /configuracion/desplazamiento | GET /api/recursos/configuracion/desplazamiento | ADMIN, PLANIFICADOR | ConfiguracionServiceTest, CatalogoControllerSeguridadTest | ✔ |
| 17 | POST /internos/reservas | POST /api/recursos/internos/reservas | SISTEMA | ReservaServiceReglasTest, ReservaControllerContratoTest, ReservaControllerSeguridadTest | ✔ |
| 18 | GET /reservas/{id} | GET /api/recursos/reservas/{id} | PLANIFICADOR, CAPATAZ, AUDITOR, SISTEMA | ExpiracionReservasTest, CatalogoControllerSeguridadTest | ✔ |
| 19 | PATCH /internos/reservas/{id}/confirmacion | PATCH /api/recursos/internos/reservas/{id}/confirmacion | SISTEMA | ReservaServiceEstadosTest, ReservaControllerEstadosTest | ✔ |
| 20 | liberación de la reserva (saga) | PATCH /api/recursos/internos/reservas/{id}/liberacion | SISTEMA | ReservaServiceEstadosTest, ReservaControllerEstadosTest | ✔ |

Extras que no están en el compromiso: `GET /tipos-trabajo/composicion-minima` (lista las composiciones), `GET /cuadrillas/{id}` y el job que pasa a EXPIRADA las reservas vencidas.

## 2. Reglas de negocio de Recursos: regla → test → request

| Regla | Qué se comprueba | Test (caso normal, límite y rechazo) | Request |
|---|---|---|---|
| RN-04 operario solo con certificación vigente | Cuadrilla sin certificación vigente para la máquina requerida es rechazada al reservar y al armarla | ReservaServiceReglasTest: `rn04_sinCertificacionVigente_esRechazada`, `rn04_conCertificacionVigente_esElegible`; CuadrillaServiceTest: `rn04_*` | POST /internos/reservas (422 `RN-04`); POST /cuadrillas (422 `RN-04`) |
| RN-05 sin solapamiento de operario o máquina | Una cuadrilla con reserva vigente ese día no se propone; pasa a la siguiente libre; una provisoria vencida no bloquea | ReservaServiceReglasTest: `rn05_cuadrillaConReservaVigente_esRechazada`, `rn05_conVariasCuadrillas_pasaALaSiguienteLibre`; ReservaServiceEstadosTest: `proponer_provisoriaVencida_reevaluaYReutilizaLaFila` | POST /internos/reservas dos veces el mismo día (422 `RN-05` la segunda, con otra orden) |
| RN-06 composición mínima de cuadrilla por tipo | Pocos operarios o faltan máquinas del tipo requerido; una máquina en mantenimiento ese día no cuenta | ReservaServiceReglasTest: `rn06_pocosOperarios_esRechazada`, `rn06_faltanMaquinasDelTipoRequerido_esRechazada`, `rn06_maquinaEnMantenimientoEseDia_noCuenta`, `tipoDeTrabajoNoConfigurado_esRechazado`; ConfiguracionServiceTest | POST /internos/reservas (422 `RN-06`); PUT /tipos-trabajo/{tipo}/composicion-minima |
| RN-07 viaje cuadrilla → tramo dentro del máximo y la jornada | Viaje mayor al máximo, igual al máximo, y ida y vuelta que no entra en la jornada | ReservaServiceReglasTest: `rn07_viajeQueSuperaElMaximo_esRechazado`, `rn07_viajeIgualAlMaximo_esElegible`, `rn07_idaYVueltaQueNoEntraEnLaJornada_esRechazada`, `rn07_idaYVueltaJustoDentroDeLaJornada_esElegible`; OsrmProveedorRutasIntegrationTest (OSRM y fallback ESTIMADA) | POST /internos/reservas con un tramo lejano (422 `RN-07`); PUT /configuracion/desplazamiento |
| RN-08 ventana de transporte de mezcla y hora de salida del camión | Planta fuera de la ventana es rechazada; en el límite calcula la salida del camión; valores de la demo | ReservaServiceReglasTest: `rn08_plantaFueraDeLaVentana_esRechazada`, `rn08_plantaEnElLimiteDeLaVentana_calculaLaSalidaDelCamion`, `rn08_horaDeSalidaDelCamion_conLosValoresDeLaDemo` | POST /internos/reservas con `CARPETA_ASFALTICA` (201 con `horaSalidaCamion`) |

Tests que cubren el rechazo completo: `rechazo_informaLaReglaDeLaCuadrillaQueLlegoMasLejos` y `sinCuadrillasCargadas_esRechazado` (ReservaServiceReglasTest).

## 3. Funcionalidades (sección 04) que tocan a MS-Recursos

| Funcionalidad | Cómo se cumple en Recursos | Evidencia | Estado |
|---|---|---|---|
| Reserva provisoria con vencimiento | Reserva PROVISORIA con TTL de 30 minutos; vencida deja de bloquear y el job la pasa a EXPIRADA | ReservaServiceEstadosTest, ExpiracionReservasTest | ✔ |
| Confirmación y liberación de reservas (habilitación y saga de suspensión) | PATCH confirmacion y liberacion, idempotentes, con guardas de estado | ReservaServiceEstadosTest | ✔ |
| Check-in de ubicación del CAPATAZ | POST /cuadrillas/{id}/ubicaciones | CuadrillaServiceTest | ✔ |
| Asignaciones del operario | GET /operarios/me/asignaciones | CatalogoServiceTest | ✔ |
| Integración OSRM con respaldo | `ProveedorRutas`, circuit breaker, estimación en línea recta marcada `ESTIMADA` | OsrmProveedorRutasIntegrationTest | ≈ la caché no vence por TTL |

## 4. Cómo se verificó

- Tests automáticos: `mvn -pl ms-recursos -am clean test` (necesita Postgres y `RECURSOS_DB_PASSWORD` en la terminal). La matriz de permisos de los endpoints nuevos (`CatalogoControllerSeguridadTest`) prueba cada endpoint sin token (401) y con los seis roles (2xx o 403).
- Corrida real: servicio contra Postgres y Keycloak, con un script que llamó a todos los endpoints con los tokens de cada rol (401, 403, rol correcto y los casos de rechazo 400, 404, 409 y 422), incluido vencer a mano el TTL de una reserva y ver que el job la pasa a EXPIRADA.

## 5. Pendiente (no bloquea)

- Caché de rutas con TTL (hoy `@Cacheable("rutas")` sin vencimiento; requiere Caffeine).
- `GET /operarios` sin la disponibilidad del día.
- TTL de la reserva (30 min) y hora de inicio de jornada (08:00) fijos en el código.
- Carrera entre dos propuestas simultáneas de la misma cuadrilla (sin bloqueo a nivel de base).
- `codigoRegla` informa la regla de la cuadrilla que llegó más lejos, no la de todas.
