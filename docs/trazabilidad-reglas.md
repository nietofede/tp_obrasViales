# Reglas de negocio: regla, test y request

Tabla única de RN-01 a RN-12. Para el detalle de implementación de cada servicio, ver `trazabilidad-ms-obras.md` y `trazabilidad-ms-operaciones.md`. La carpeta «Reglas» de la colección Postman (`coleccion/`) tiene un request por regla.

Estado: ✔ test y request en la colección · ≈ test, request parcial o pendiente de la segunda pasada · ✘ pendiente.

| Regla | Servicio | Test automático | Request en la colección | Resultado esperado | Estado |
|---|---|---|---|---|---|
| RN-01 Una fase no inicia sin la anterior completa | Obras (y Operaciones al habilitar) | `FaseControllerTest.iniciarQueIncumpleRn01Devuelve409`, `FaseServiceTest`, `FaseServiceAvanceTest`; `OrdenTrabajoServiceHabilitacionTest.rn01RechazaSiLaFasePreviaNoEstaCompletaYNoReserva` | Reglas > RN-01 | 409 | ✔ |
| RN-02 Replanificación en cascada | Obras | `FaseServiceTest`, `FaseServiceAvanceTest`; `ObrasHttpClientTest.siObrasRechazaLaReprogramacionPorRn02SeReenviaConSuCodigo` | `PATCH /api/obras/fases/{id}/programacion` (a agregar) | 200 con las dependientes corridas; 422 `RN-02` si hay ciclo o fecha anterior | ≈ falta request |
| RN-03 Materiales según la superficie | Obras | `MaterialServiceTest`, `FaseServiceAvanceTest`, `FaseControllerLecturaTest` | Reglas > RN-03 | 200 con cantidades proporcionales a 5000 m² | ✔ |
| RN-04 Operario solo con certificación vigente | Recursos | `ReservaServiceReglasTest`: `rn04_sinCertificacionVigente_esRechazada`, `rn04_conCertificacionVigente_esElegible` | Pendiente: requiere altas de operarios y certificaciones (Rama 1) | 422 `RN-04` sin certificación vigente | ≈ segunda pasada |
| RN-05 Sin solapamiento de operario ni máquina | Recursos | `ReservaServiceReglasTest`: `rn05_cuadrillaConReservaVigente_esRechazada`, `rn05_conVariasCuadrillas_pasaALaSiguienteLibre` | Reglas > RN-05 | 201 la primera reserva; 422 `RN-05` la segunda del mismo día | ✔ (cuadrilla; máquinas en la segunda pasada) |
| RN-06 Composición mínima de la cuadrilla | Recursos | `ReservaServiceReglasTest`: `rn06_pocosOperarios_esRechazada`, `rn06_faltanMaquinasDelTipoRequerido_esRechazada`, `rn06_maquinaEnMantenimientoEseDia_noCuenta` | Pendiente: requiere `PUT /tipos-trabajo/{tipo}/composicion-minima` (Rama 1) | 422 `RN-06` | ≈ segunda pasada |
| RN-07 Viaje al tramo dentro del máximo y de la jornada | Recursos | `ReservaServiceReglasTest`: `rn07_viajeQueSuperaElMaximo_esRechazado`, `rn07_viajeIgualAlMaximo_esElegible`, `rn07_idaYVueltaQueNoEntraEnLaJornada_esRechazada`, `rn07_idaYVueltaJustoDentroDeLaJornada_esElegible` | Reglas > RN-07 (tramo lejano) | 422 `RN-07` | ✔ |
| RN-08 Ventana de transporte de la mezcla y hora de salida del camión | Recursos | `ReservaServiceReglasTest`: `rn08_plantaFueraDeLaVentana_esRechazada`, `rn08_plantaEnElLimiteDeLaVentana_calculaLaSalidaDelCamion`, `rn08_horaDeSalidaDelCamion_conLosValoresDeLaDemo` | Pendiente: el caso feliz muestra `horaSalidaCamion` en la reserva 201 de RN-05; el rechazo necesita configuración de ventana (Rama 1) | 201 con `horaSalidaCamion`; 422 `RN-08` fuera de ventana | ≈ segunda pasada |
| RN-09 Umbrales climáticos por tipo de trabajo | Operaciones | `OrdenTrabajoServiceHabilitacionTest.rn09SinUmbralConfiguradoParaElTipoNoSeHabilitaNiSeReserva` y los de evaluación climática | Reglas > RN-09 (ADMIN baja y restaura umbrales) | 200; con umbrales extremos la orden se suspende | ✔ |
| RN-10 Sin dato climático confiable no se habilita sola | Operaciones | `OpenMeteoServiceTest`; `OrdenTrabajoServiceHabilitacionTest.siFallaElClimaQuedaPendienteDeConfirmacionYConservaLaReserva`; `EjecucionServiceTest` | Reglas > RN-10 (rechazo) y Fallas > Habilitación sin Open-Meteo | `PENDIENTE_CONFIRMACION_CLIMATICA`; el CAPATAZ resuelve | ✔ |
| RN-11 Solo transiciones de estado válidas | Operaciones | `OrdenTrabajoServiceHabilitacionTest.rn11RechazaUnaOrdenQueNoEstaProgramadaYNoConsultaAObras`, `EjecucionServiceTest.noSePuedeIniciarUnaOrdenQueNoEstaHabilitada` | Reglas > RN-11 | 422 | ✔ |
| RN-12 N suspensiones, fase en riesgo | Operaciones | `SuspensionServiceTest` (7), `FaseEnRiesgoServiceTest` (7) | Reglas > RN-12 | 200; la fase aparece en `GET /fases-en-riesgo` | ✔ |

## Pendientes

- RN-02: agregar el request de reprogramación de fase a la colección.
- RN-04, RN-06 y RN-08: completar en la segunda pasada, cuando la Rama 1 incorpore altas de operarios, composición mínima y configuración de desplazamiento.
- Los códigos esperados salen de los controllers y de las trazabilidades; falta corroborarlos corriendo la colección sobre el stack.
