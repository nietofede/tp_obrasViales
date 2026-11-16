# Reglas de negocio: regla, test y request

Tabla única de RN-01 a RN-12. Para el detalle de implementación de cada servicio, ver `trazabilidad-ms-obras.md` y `trazabilidad-ms-operaciones.md`. La carpeta «Reglas» de la colección Postman (`coleccion/`) tiene un request por regla.

Estado: ✔ test y request en la colección · ≈ test, request parcial o pendiente · ✘ pendiente.

| Regla | Servicio | Test automático | Request en la colección | Resultado esperado | Estado |
|---|---|---|---|---|---|
| RN-01 Una fase no inicia sin la anterior completa | Obras (y Operaciones al habilitar) | `FaseServiceAvanceTest.elPrimerAvanceEsRechazadoSiLaAnteriorNoEstaCompletada`, `FaseServiceTest`; `OrdenTrabajoServiceHabilitacionTest.rn01RechazaSiLaFasePreviaNoEstaCompletaYNoReserva` | 04-Reglas-Negocio > RN-01 (avance en la fase BASE con RELEVAMIENTO pendiente) | 409 | ✔ |
| RN-02 Replanificación en cascada | Obras | `FaseServiceTest`, `FaseServiceAvanceTest`; `ObrasHttpClientTest.siObrasRechazaLaReprogramacionPorRn02SeReenviaConSuCodigo` | 04-Reglas-Negocio > RN-02 (cascada y rechazo) | 200 con las dependientes corridas; 422 `RN-02` si la fecha es anterior al fin de la previa | ✔ |
| RN-03 Materiales según la superficie | Obras | `MaterialServiceTest`, `FaseServiceAvanceTest`, `FaseControllerLecturaTest` | 04-Reglas-Negocio > RN-03 | 200 con cantidades proporcionales a 5000 m² | ✔ |
| ~~RN-04~~ Operario solo con certificación vigente | Recursos | Quitada el 2026-11-16 (ver `DECISIONES.md`); los demás números no cambian | — | — |
| RN-05 Sin solapamiento de operario ni máquina | Recursos | `ReservaServiceReglasTest`: `rn05_cuadrillaConReservaVigente_esRechazada`, `rn05_conVariasCuadrillas_pasaALaSiguienteLibre` | 04-Reglas-Negocio > RN-05 | 201 la primera reserva; 422 `RN-05` la segunda del mismo día | ✔ (cuadrilla; máquinas en la segunda pasada) |
| RN-06 Composición mínima de la cuadrilla | Recursos | `ReservaServiceReglasTest`: `rn06_pocosOperarios_esRechazada`, `rn06_faltanMaquinasDelTipoRequerido_esRechazada`, `tipoDeTrabajoNoConfigurado_esRechazado` | 04-Reglas-Negocio > RN-06 (tipo sin composición) y RN-05 (la cuadrilla Beta Lejana se rechaza por RN-06) | 422 con `codigoRegla: RN-06` | ✔ |
| RN-07 Viaje al tramo dentro del máximo y de la jornada | Recursos | `ReservaServiceReglasTest`: `rn07_viajeQueSuperaElMaximo_esRechazado`, `rn07_viajeIgualAlMaximo_esElegible`, `rn07_idaYVueltaQueNoEntraEnLaJornada_esRechazada`, `rn07_idaYVueltaJustoDentroDeLaJornada_esElegible` | 04-Reglas-Negocio > RN-07 (tramo lejano) y 03-Flujo-Principal > Escenarios 1 y 3 (orden 3 del seed por la habilitación diaria) | 422 `RN-07` | ✔ |
| RN-08 Ventana de transporte de la mezcla y hora de salida del camión | Recursos | `ReservaServiceReglasTest`: `rn08_plantaFueraDeLaVentana_esRechazada`, `rn08_plantaEnElLimiteDeLaVentana_calculaLaSalidaDelCamion`, `rn08_horaDeSalidaDelCamion_conLosValoresDeLaDemo` | 04-Reglas-Negocio > RN-08 (reserva con mezcla) | 201 con `horaSalidaCamion`; el rechazo (422 `RN-08`) queda en los tests porque la ventana ya no se edita | ✔ |
| RN-09 Umbrales climáticos por tipo de trabajo | Operaciones | `OrdenTrabajoServiceHabilitacionTest.rn09SinUmbralConfiguradoParaElTipoNoSeHabilitaNiSeReserva` y los de evaluación climática | 04-Reglas-Negocio > RN-09 (ADMIN baja y restaura umbrales) | 200; con umbrales extremos la orden se suspende | ✔ |
| RN-10 Sin dato climático confiable no se habilita sola | Operaciones | `OpenMeteoServiceTest`; `OrdenTrabajoServiceHabilitacionTest.siFallaElClimaQuedaPendienteDeConfirmacionYConservaLaReserva`; `EjecucionServiceTest` | 04-Reglas-Negocio > RN-10 (rechazo) y 05-Fallas-y-Resiliencia > Habilitación sin Open-Meteo | `PENDIENTE_CONFIRMACION_CLIMATICA`; el CAPATAZ resuelve | ✔ |
| RN-11 Solo transiciones de estado válidas | Operaciones | `OrdenTrabajoServiceHabilitacionTest.rn11RechazaUnaOrdenQueNoEstaProgramadaYNoConsultaAObras`, `EjecucionServiceTest.noSePuedeIniciarUnaOrdenQueNoEstaHabilitada`, `unaOrdenCompletadaNoAceptaMasAvances` | 04-Reglas-Negocio > RN-11 | 422 | ✔ |
| RN-12 N suspensiones, fase en riesgo | Operaciones | `SuspensionServiceTest` (9): `alLlegarAlLimiteMarcaLaFaseEnRiesgoYNoCreaOrdenNueva`, `alCrearLaOrdenNuevaDeLaFaseLasSuspendidasQuedanReprogramadasYElConteoVuelveACero`; `OrdenTrabajoServiceCrearOrdenTest` | 04-Reglas-Negocio > RN-12 (historial de suspensiones) | límite fijo de 3: la fase queda `EN_RIESGO` en Obras y la orden `SUSPENDIDA`; el PLANIFICADOR crea la orden nueva | ✔ |

## Pendientes

- Las reglas de Recursos se comprobaron sobre el stack con los endpoints de la Rama 1 (colección en verde).
- Los códigos esperados salen de los controllers y de las trazabilidades; falta corroborarlos corriendo la colección sobre el stack.
