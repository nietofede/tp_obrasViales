# Trazabilidad de ms-operaciones: regla de negocio, implementación y evidencia

Mismo formato que `trazabilidad-ms-obras.md`. Borrador de Mateo; revisar con el equipo antes de entregar.

Estado: ✔ cumplido y con evidencia · ≈ cumplido con una diferencia anotada · ✘ pendiente.

## 1. Reglas de negocio de Operaciones

| Regla | Qué hace | Implementación | Evidencia (tests) | Request para probarla a mano | Estado |
|---|---|---|---|---|---|
| RN-09 | Umbrales climáticos por tipo de trabajo; sin umbral o tipo desconocido no se habilita ni se reserva | `OrdenTrabajoServiceImpl.habilitarOrden`, `UmbralClimaticoController` | `OrdenTrabajoServiceHabilitacionTest`: rechazaUnTipoDeTrabajoQueNoConoceYNoReserva, rn09SinUmbralConfiguradoParaElTipoNoSeHabilitaNiSeReserva | `PUT /api/operaciones/umbrales-climaticos/{tipoTrabajo}` (ADMIN) y `POST /api/operaciones/ordenes/{id}/habilitacion` | ✔ |
| RN-10 | Clima por Open-Meteo (peor valor de la ventana de ejecución de la fecha de la orden, 08:00 a 13:00 por defecto); si no hay dato confiable la orden queda PENDIENTE_CONFIRMACION_CLIMATICA y decide el CAPATAZ | `OpenMeteoService`, `habilitarOrden`, `EjecucionService.resolverClima` | `OpenMeteoServiceTest` (10), `OrdenTrabajoServiceHabilitacionTest`: conClimaMaloDisparaLaSagaDeSuspensionYNoConfirmaLaReserva, siFallaElClimaQuedaPendienteDeConfirmacionYConservaLaReserva; `EjecucionServiceTest`: habilitarUnaOrdenPendienteDeClimaConfirmaLaReserva, suspenderUnaOrdenPendienteDeClimaCorreLaSaga, soloSeResuelveElClimaDeUnaOrdenPendiente, siLaReservaVencioLaOrdenSigueSinHabilitarse, habilitarSinReservaVigenteDa409 | `POST /api/operaciones/ordenes/{id}/habilitacion`, `PATCH /api/operaciones/ordenes/{id}/resolucion-climatica` (CAPATAZ) | ✔ |
| RN-11 | Máquina de estados de la orden; transición inválida da 422 | `EstadoOrden.puedeTransicionarHacia`, `habilitarOrden`, `EjecucionService`, `cancelarOrden` | `OrdenTrabajoServiceHabilitacionTest`: rn11RechazaUnaOrdenQueNoEstaProgramadaYNoConsultaAObras; `EjecucionServiceTest`: noSePuedeIniciarUnaOrdenQueNoEstaHabilitada, soloSePuedeRechazarUnaOrdenHabilitada, noSeRegistraAvanceSiLaOrdenNoEstaEnEjecucion, soloSeCierraUnaOrdenEnEjecucion; `OrdenTrabajoServiceCancelacionTest`: noSePuedeCancelarUnaOrdenEnEjecucion, soloSeReprogramaUnaOrdenSinRecursos | `PATCH /api/operaciones/ordenes/{id}/inicio` sobre una orden PROGRAMADA | ✔ |
| RN-12 | Al llegar al límite de suspensiones (configurable, default 3) la fase pasa a EN_RIESGO y decide el PLANIFICADOR | `SuspensionService`, `FaseEnRiesgoService`, `ConfiguracionController` | `SuspensionServiceTest` (7): debajoDelLimiteLiberaReprogramaLaFaseYCreaLaOrdenNueva, alLlegarAlLimiteMarcaLaFaseEnRiesgoYNoCreaOrdenNueva, elLimiteEsConfigurable, etc.; `FaseEnRiesgoServiceTest` (7) | `PUT /api/operaciones/configuracion/limite-suspensiones` (ADMIN), `PATCH /api/operaciones/ordenes/{id}/rechazo` repetido, `GET /api/operaciones/fases-en-riesgo`, `POST /api/operaciones/fases-en-riesgo/{faseId}/decisiones` | ✔ |
| RN-01 (consumida) | No se habilita si la fase previa no está completa; la consulta a Obras | `habilitarOrden` + `ObrasClient` | `OrdenTrabajoServiceHabilitacionTest`: rn01RechazaSiLaFasePreviaNoEstaCompletaYNoReserva, siObrasFallaLaOrdenNoCambiaDeEstado; `ObrasHttpClientTest` | `POST /api/operaciones/ordenes/{id}/habilitacion` | ✔ |

## 2. Seguridad

Los tres casos (401, 403, 2xx) de cada endpoint están en `SeguridadEndpointsTest`; la tabla está en `matriz-permisos.md`, sección ms-operaciones.

## 3. Diferencias y pendientes anotados

- ≈ La decisión CANCELAR de una fase en riesgo deja la fase EN_RIESGO en Obras: Obras no tiene endpoint para cancelarla.
- ✘ Prueba de punta a punta con Obras + Recursos + gateway, pendiente de que se mergee la rama de Recursos (rutas `confirmacion` / `liberacion`).
- ✘ `MsOperacionesApplicationTests` (contextLoads) solo pasa con la base levantada y `OPERACIONES_DB_PASSWORD` seteada.
