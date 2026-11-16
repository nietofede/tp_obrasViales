# Trazabilidad de ms-operaciones: regla de negocio, implementación y evidencia

Mismo formato que `trazabilidad-ms-obras.md`. Borrador de Mateo; revisar con el equipo antes de entregar.

Estado: ✔ cumplido y con evidencia · ≈ cumplido con una diferencia anotada · ✘ pendiente.

## 1. Reglas de negocio de Operaciones

| Regla | Qué hace | Implementación | Evidencia (tests) | Request para probarla a mano | Estado |
|---|---|---|---|---|---|
| RN-09 | Umbrales climáticos por tipo de trabajo; sin umbral o tipo desconocido no se habilita ni se reserva | `OrdenTrabajoServiceImpl.habilitarOrden`, `UmbralClimaticoController` | `OrdenTrabajoServiceHabilitacionTest`: rechazaUnTipoDeTrabajoQueNoConoceYNoReserva, rn09SinUmbralConfiguradoParaElTipoNoSeHabilitaNiSeReserva | `PUT /api/operaciones/umbrales-climaticos/{tipoTrabajo}` (ADMIN) y `POST /api/operaciones/habilitaciones-diarias` | ✔ |
| RN-10 | Clima por Open-Meteo (peor valor de la ventana de ejecución de la fecha de la orden, 08:00 a 13:00 por defecto); si no hay dato confiable la orden queda PENDIENTE_CONFIRMACION_CLIMATICA y decide el CAPATAZ | `OpenMeteoService`, `habilitarOrden`, `EjecucionService.resolverClima` | `OpenMeteoServiceTest` (10), `OrdenTrabajoServiceHabilitacionTest`: conClimaMaloDisparaLaSagaDeSuspensionYNoConfirmaLaReserva, siFallaElClimaQuedaPendienteDeConfirmacionYConservaLaReserva; `EjecucionServiceTest`: habilitarUnaOrdenPendienteDeClimaConfirmaLaReserva, suspenderUnaOrdenPendienteDeClimaCorreLaSaga, soloSeResuelveElClimaDeUnaOrdenPendiente, siLaReservaVencioLaOrdenSigueSinHabilitarse, habilitarSinReservaVigenteDa409 | `POST /api/operaciones/habilitaciones-diarias`, `PATCH /api/operaciones/ordenes/{id}/resolucion-climatica` (CAPATAZ) | ✔ |
| RN-11 | Máquina de estados de la orden; transición inválida da 422 | `EjecucionService.exigirEstado`, chequeo del estado inicial en `habilitarOrden` | `OrdenTrabajoServiceHabilitacionTest`: rn11RechazaUnaOrdenQueNoEstaProgramadaYNoConsultaAObras; `EjecucionServiceTest`: noSePuedeIniciarUnaOrdenQueNoEstaHabilitada, soloSePuedeRechazarUnaOrdenHabilitada, noSeRegistraAvanceSiLaOrdenNoEstaEnEjecucion, unaOrdenCompletadaNoAceptaMasAvances | `PATCH /api/operaciones/ordenes/{id}/inicio` sobre una orden PROGRAMADA | ✔ |
| RN-12 | Al llegar al límite fijo de 3 suspensiones vigentes la fase pasa a EN_RIESGO en Obras, la orden queda SUSPENDIDA y no se reprograma sola; el PLANIFICADOR reprograma la fase y crea la orden nueva, y el conteo vuelve a cero | `SuspensionService` (`suspender`, `cerrarSuspensionesDeLaFase`), `OrdenTrabajoServiceImpl.crearOrden` | `SuspensionServiceTest` (9): debajoDelLimiteLiberaReprogramaLaFaseYCreaLaOrdenNueva, alLlegarAlLimiteMarcaLaFaseEnRiesgoYNoCreaOrdenNueva, alCrearLaOrdenNuevaDeLaFaseLasSuspendidasQuedanReprogramadasYElConteoVuelveACero, etc.; `OrdenTrabajoServiceCrearOrdenTest` | `PATCH /api/operaciones/ordenes/{id}/rechazo` repetido, `GET /api/operaciones/suspensiones`, `PATCH /api/obras/fases/{id}/programacion` y `POST /api/operaciones/ordenes` | ✔ |
| RN-01 (consumida) | No se habilita si la fase previa no está completa; la consulta a Obras | `habilitarOrden` + `ObrasClient` | `OrdenTrabajoServiceHabilitacionTest`: rn01RechazaSiLaFasePreviaNoEstaCompletaYNoReserva, siObrasFallaLaOrdenNoCambiaDeEstado; `ObrasHttpClientTest` | `POST /api/operaciones/habilitaciones-diarias` | ✔ |

## 2. Seguridad

Los tres casos (401, 403, 2xx) de cada endpoint están en `SeguridadEndpointsTest`; la tabla está en `matriz-permisos.md`, sección ms-operaciones.

## 3. Diferencias y pendientes anotados

- ≈ Recorte de alcance (2026-11-14): sin pantalla de fases en riesgo ni decisión registrada aparte; la decisión del PLANIFICADOR es reprogramar la fase en Obras (vuelve a PENDIENTE) y crear la orden nueva. El cierre ya no guarda consumo real ni horas de máquina: la orden se completa con el avance al 100 %.
- ✘ Prueba de punta a punta con Obras + Recursos + gateway, pendiente de que se mergee la rama de Recursos (rutas `confirmacion` / `liberacion`).
- ✘ `MsOperacionesApplicationTests` (contextLoads) solo pasa con la base levantada y `OPERACIONES_DB_PASSWORD` seteada.
