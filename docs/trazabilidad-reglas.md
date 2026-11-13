# Reglas de negocio: regla, test y request

Tabla única de RN-01 a RN-12. Para el detalle de implementación de cada servicio, ver `trazabilidad-ms-obras.md` y `trazabilidad-ms-operaciones.md`. La carpeta «Reglas» de la colección Postman (`coleccion/`) tiene un request por regla.

Estado: ✔ test y request en la colección · ≈ test, request parcial o pendiente · ✘ pendiente.

| Regla | Servicio | Test automático | Request en la colección | Resultado esperado | Estado |
|---|---|---|---|---|---|
| RN-01 Una fase no inicia sin la anterior completa | Obras (y Operaciones al habilitar) | `FaseControllerTest.iniciarQueIncumpleRn01Devuelve409`, `FaseServiceTest`, `FaseServiceAvanceTest`; `OrdenTrabajoServiceHabilitacionTest.rn01RechazaSiLaFasePreviaNoEstaCompletaYNoReserva` | 04-Reglas-Negocio > RN-01 | 409 | ✔ |
| RN-02 Replanificación en cascada | Obras | `FaseServiceTest`, `FaseServiceAvanceTest`; `ObrasHttpClientTest.siObrasRechazaLaReprogramacionPorRn02SeReenviaConSuCodigo` | 04-Reglas-Negocio > RN-02 (cascada y rechazo) | 200 con las dependientes corridas; 422 `RN-02` si la fecha es anterior al fin de la previa | ✔ |
| RN-03 Materiales según la superficie | Obras | `MaterialServiceTest`, `FaseServiceAvanceTest`, `FaseControllerLecturaTest` | 04-Reglas-Negocio > RN-03 | 200 con cantidades proporcionales a 5000 m² | ✔ |
| RN-04 Operario solo con certificación vigente | Recursos | `ReservaServiceReglasTest`: `rn04_sinCertificacionVigente_esRechazada`, `rn04_conCertificacionVigente_esElegible`; `CuadrillaServiceTest` | 04-Reglas-Negocio > RN-04 (cuadrilla con un operario sin certificación) | 422 con `codigoRegla: RN-04` | ✔ |
| RN-05 Sin solapamiento de operario ni máquina | Recursos | `ReservaServiceReglasTest`: `rn05_cuadrillaConReservaVigente_esRechazada`, `rn05_conVariasCuadrillas_pasaALaSiguienteLibre` | 04-Reglas-Negocio > RN-05 | 201 la primera reserva; 422 `RN-05` la segunda del mismo día | ✔ (cuadrilla; máquinas en la segunda pasada) |
| RN-06 Composición mínima de la cuadrilla | Recursos | `ReservaServiceReglasTest`: `rn06_pocosOperarios_esRechazada`, `rn06_faltanMaquinasDelTipoRequerido_esRechazada`, `rn06_maquinaEnMantenimientoEseDia_noCuenta` | 04-Reglas-Negocio > RN-06 (el ADMIN sube la composición de BASE a 4 operarios) | 422 con `codigoRegla: RN-06` («tiene 3 operarios activos y se necesitan 4») | ✔ |
| RN-07 Viaje al tramo dentro del máximo y de la jornada | Recursos | `ReservaServiceReglasTest`: `rn07_viajeQueSuperaElMaximo_esRechazado`, `rn07_viajeIgualAlMaximo_esElegible`, `rn07_idaYVueltaQueNoEntraEnLaJornada_esRechazada`, `rn07_idaYVueltaJustoDentroDeLaJornada_esElegible` | 04-Reglas-Negocio > RN-07 (tramo lejano) | 422 `RN-07` | ✔ |
| RN-08 Ventana de transporte de la mezcla y hora de salida del camión | Recursos | `ReservaServiceReglasTest`: `rn08_plantaFueraDeLaVentana_esRechazada`, `rn08_plantaEnElLimiteDeLaVentana_calculaLaSalidaDelCamion`, `rn08_horaDeSalidaDelCamion_conLosValoresDeLaDemo` | 04-Reglas-Negocio > RN-08 (reserva con mezcla y reserva con la ventana en 1 minuto) | 201 con `horaSalidaCamion`; 422 con `codigoRegla: RN-08` | ✔ |
| RN-09 Umbrales climáticos por tipo de trabajo | Operaciones | `OrdenTrabajoServiceHabilitacionTest.rn09SinUmbralConfiguradoParaElTipoNoSeHabilitaNiSeReserva` y los de evaluación climática | 04-Reglas-Negocio > RN-09 (ADMIN baja y restaura umbrales) | 200; con umbrales extremos la orden se suspende | ✔ |
| RN-10 Sin dato climático confiable no se habilita sola | Operaciones | `OpenMeteoServiceTest`; `OrdenTrabajoServiceHabilitacionTest.siFallaElClimaQuedaPendienteDeConfirmacionYConservaLaReserva`; `EjecucionServiceTest` | 04-Reglas-Negocio > RN-10 (rechazo) y 05-Fallas-y-Resiliencia > Habilitación sin Open-Meteo | `PENDIENTE_CONFIRMACION_CLIMATICA`; el CAPATAZ resuelve | ✔ |
| RN-11 Solo transiciones de estado válidas | Operaciones | `OrdenTrabajoServiceHabilitacionTest.rn11RechazaUnaOrdenQueNoEstaProgramadaYNoConsultaAObras`, `EjecucionServiceTest.noSePuedeIniciarUnaOrdenQueNoEstaHabilitada` | 04-Reglas-Negocio > RN-11 | 422 | ✔ |
| RN-12 N suspensiones, fase en riesgo | Operaciones | `SuspensionServiceTest` (7), `FaseEnRiesgoServiceTest` (7) | 04-Reglas-Negocio > RN-12 | 200; la fase aparece en `GET /fases-en-riesgo` | ✔ |

## Pendientes

- Las reglas de Recursos se comprobaron sobre el stack con los endpoints de la Rama 1 (colección en verde).
- Los códigos esperados salen de los controllers y de las trazabilidades; falta corroborarlos corriendo la colección sobre el stack.
