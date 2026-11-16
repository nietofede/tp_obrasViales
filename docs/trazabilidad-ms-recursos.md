# Trazabilidad de ms-recursos: requisito, implementación y evidencia

Sirve para ALC-01 y NEG-02 de la rúbrica v05 («matriz de trazabilidad enunciado/compromiso → implementación → evidencia» y «regla → test → request»). Alcance: lo que el equipo comprometió para MS-Recursos en el cuestionario (secciones 04 y 07) y las reglas RN-05 a RN-08 (RN-04 se quitó el 2026-11-16).

Estado: ✔ cumplido y con evidencia · ≈ cumplido con una diferencia anotada · ✘ pendiente.

Todas las rutas llevan el prefijo `/api/recursos` (el gateway no recorta el prefijo). La columna «Request» es la llamada HTTP que demuestra la regla; la colección Bruno/Postman (paso 12) tiene que usar estos mismos nombres.

## 1. Endpoints comprometidos (sección 07) tras el recorte de alcance

De los 20 endpoints comprometidos quedan 7 (recorte del 2026-11-14, `docs/DECISIONES.md`). Máquinas, operarios, plantas, cuadrillas, composición mínima y configuración de desplazamiento vienen precargados por Flyway (`V2__datos_demo.sql`) y no se editan por la API.

| # | Comprometido | Implementado | Roles | Evidencia | Estado |
|---|---|---|---|---|---|
| 1 | GET /maquinas | GET /api/recursos/maquinas | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | CatalogoServiceTest, CatalogoControllerSeguridadTest | ≈ el ADMIN se suma a los tres roles del cuestionario; sin filtro de disponibilidad por fecha |
| 2 | GET /operarios | GET /api/recursos/operarios | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | CatalogoServiceTest, CatalogoControllerSeguridadTest | ≈ sin la disponibilidad del día; sin certificaciones desde que se quitó RN-04 |
| 3 | GET /cuadrillas, con ubicación vigente | GET /api/recursos/cuadrillas | ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR | CuadrillaServiceTest, CatalogoControllerSeguridadTest | ✔ |
| 4 | POST /cuadrillas/{id}/ubicaciones | POST /api/recursos/cuadrillas/{id}/ubicaciones | CAPATAZ | CuadrillaServiceTest, CatalogoControllerSeguridadTest | ≈ solo el capataz de esa cuadrilla (403 para otro) |
| 5 | POST /internos/reservas | POST /api/recursos/internos/reservas | SISTEMA | ReservaServiceReglasTest, ReservaControllerContratoTest, ReservaControllerSeguridadTest | ✔ |
| 6 | PATCH /internos/reservas/{id}/confirmacion | PATCH /api/recursos/internos/reservas/{id}/confirmacion | SISTEMA | ReservaServiceEstadosTest, ReservaControllerEstadosTest | ✔ |
| 7 | liberación de la reserva (saga) | PATCH /api/recursos/internos/reservas/{id}/liberacion | SISTEMA | ReservaServiceEstadosTest, ReservaControllerEstadosTest | ✔ |

Quitados con el recorte: `POST` y `PUT /maquinas`, `POST /operarios`, `POST /operarios/{id}/certificaciones`, `GET /operarios/me/asignaciones`, `POST` y `GET /plantas`, `POST` y `PUT /cuadrillas`, `PUT /tipos-trabajo/{tipo}/composicion-minima`, `GET` y `PUT /configuracion/desplazamiento`, `GET /reservas/{id}`, y los extras `GET /cuadrillas/{id}` y `GET /tipos-trabajo/composicion-minima`. También el chequeo de mantenimiento al reservar (tabla `mantenimientos`, borrada en `V3__quitar_mantenimientos.sql`). Se conserva el job que pasa a EXPIRADA las reservas vencidas.

## 2. Reglas de negocio de Recursos: regla → test → request

| Regla | Qué se comprueba | Test (caso normal, límite y rechazo) | Request |
|---|---|---|---|
| ~~RN-04~~ operario solo con certificación vigente | Quitada el 2026-11-16 (`DECISIONES.md`): tabla borrada con `V4__quitar_rn04_y_restos_del_recorte.sql` | — | — | — |
| RN-05 sin solapamiento de operario o máquina | Una cuadrilla con reserva vigente ese día no se propone; pasa a la siguiente libre; una provisoria vencida no bloquea | ReservaServiceReglasTest: `rn05_cuadrillaConReservaVigente_esRechazada`, `rn05_conVariasCuadrillas_pasaALaSiguienteLibre`; ReservaServiceEstadosTest: `proponer_provisoriaVencida_reevaluaYReutilizaLaFila` | POST /internos/reservas dos veces el mismo día (422 `RN-05` la segunda, con otra orden) |
| RN-06 composición mínima de cuadrilla por tipo | Pocos operarios o faltan máquinas del tipo requerido; tipo de trabajo sin composición | ReservaServiceReglasTest: `rn06_pocosOperarios_esRechazada`, `rn06_faltanMaquinasDelTipoRequerido_esRechazada`, `tipoDeTrabajoNoConfigurado_esRechazado` | POST /internos/reservas con un tipo sin composición (422 `RN-06`); en el rechazo de RN-05 la cuadrilla Beta Lejana figura con RN-06 (0 operarios) |
| RN-07 viaje cuadrilla → tramo dentro del máximo y la jornada | Viaje mayor al máximo, igual al máximo, y ida y vuelta que no entra en la jornada | ReservaServiceReglasTest: `rn07_viajeQueSuperaElMaximo_esRechazado`, `rn07_viajeIgualAlMaximo_esElegible`, `rn07_idaYVueltaQueNoEntraEnLaJornada_esRechazada`, `rn07_idaYVueltaJustoDentroDeLaJornada_esElegible`; OsrmProveedorRutasIntegrationTest (OSRM y fallback ESTIMADA) | POST /internos/reservas con un tramo lejano (422 `RN-07`) |
| RN-08 ventana de transporte de mezcla y hora de salida del camión | Planta fuera de la ventana es rechazada; en el límite calcula la salida del camión; valores de la demo | ReservaServiceReglasTest: `rn08_plantaFueraDeLaVentana_esRechazada`, `rn08_plantaEnElLimiteDeLaVentana_calculaLaSalidaDelCamion`, `rn08_horaDeSalidaDelCamion_conLosValoresDeLaDemo` | POST /internos/reservas con `CARPETA_ASFALTICA` (201 con `horaSalidaCamion`) |

Tests que cubren el rechazo completo: `rechazo_informaLaReglaDeLaCuadrillaQueLlegoMasLejos` y `sinCuadrillasCargadas_esRechazado` (ReservaServiceReglasTest).

## 3. Funcionalidades (sección 04) que tocan a MS-Recursos

| Funcionalidad | Cómo se cumple en Recursos | Evidencia | Estado |
|---|---|---|---|
| Reserva provisoria con vencimiento | Reserva PROVISORIA con TTL de 30 minutos; vencida deja de bloquear y el job la pasa a EXPIRADA | ReservaServiceEstadosTest, ExpiracionReservasTest | ✔ |
| Confirmación y liberación de reservas (habilitación y saga de suspensión) | PATCH confirmacion y liberacion, idempotentes, con guardas de estado | ReservaServiceEstadosTest | ✔ |
| Check-in de ubicación del CAPATAZ | POST /cuadrillas/{id}/ubicaciones | CuadrillaServiceTest | ✔ |
| Integración OSRM con respaldo | `ProveedorRutas`, circuit breaker, estimación en línea recta marcada `ESTIMADA` | OsrmProveedorRutasIntegrationTest | ✔ caché de rutas vaciada cada 6 h (`VencimientoCacheRutas`) y 1 pedido por segundo |

## 4. Cómo se verificó

- Tests automáticos: `mvn -pl ms-recursos -am clean test` (necesita Postgres y `RECURSOS_DB_PASSWORD` en la terminal). La matriz de permisos (`CatalogoControllerSeguridadTest`) prueba cada endpoint sin token (401) y con los cinco roles (2xx o 403).
- Corrida real: servicio contra Postgres y Keycloak, con un script que llamó a todos los endpoints con los tokens de cada rol (401, 403, rol correcto y los casos de rechazo 400, 404, 409 y 422), incluido vencer a mano el TTL de una reserva y ver que el job la pasa a EXPIRADA.

## 5. Pendiente (no bloquea)

- Vencimiento por entrada de la caché de rutas (hoy se vacía entera cada 6 h; una caché como Caffeine lo haría por par origen-destino).
- `GET /operarios` sin la disponibilidad del día.
- TTL de la reserva (30 min) y hora de inicio de jornada (08:00) fijos en el código.
- Carrera entre dos propuestas simultáneas de la misma cuadrilla (sin bloqueo a nivel de base).
- `codigoRegla` informa la regla de la cuadrilla que llegó más lejos, no la de todas.
