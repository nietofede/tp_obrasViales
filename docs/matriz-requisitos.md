# Matriz requisito → implementación → evidencia

Cubre lo que el equipo comprometió en el cuestionario: las funcionalidades F-01 a F-17 (sección 04), los cinco escenarios del compromiso (sección 11), la librería común (sección 09) y el despliegue (sección 10). Las reglas RN-01 a RN-12 están en [`trazabilidad-reglas.md`](trazabilidad-reglas.md); el detalle por servicio, en `trazabilidad-ms-obras.md` y `trazabilidad-ms-operaciones.md`.

Estado: ✔ implementado y con evidencia · ≈ implementado con una diferencia o con evidencia parcial · ✘ pendiente (se indica la rama que lo cubre).

Las rutas son las del gateway (prefijo `/api/<servicio>`). Las carpetas citadas son las de la colección Postman en `coleccion/`.

## 1. Funcionalidades (sección 04)

| ID | Funcionalidad | Implementación | Evidencia | Estado |
|---|---|---|---|---|
| F-01 | Gestionar obras, tramos y fases | `ObraController`, `TramoController` (alta de obras, tramos y fases; consulta de la obra completa) | `ObraControllerTest`, `ObraServiceTest`, `TramoControllerTest`; colección: 01-Setup-Datos | ≈ recorte: sin edición de obras y tramos ni consultas sueltas de tramo y fase |
| F-02 | Armar cronograma y dependencias | `GET /obras/{id}/cronograma`, `PATCH /fases/{id}/programacion` (cascada, RN-02) | `FaseServiceTest`, `ObraControllerConsultasTest`; colección: 01-Setup-Datos y RN-02 | ✔ |
| F-03 | Calcular materiales | `GET /fases/{id}/materiales` (RN-03) | `MaterialServiceTest`, `FaseControllerLecturaTest`; colección: 04-Reglas-Negocio > RN-03 | ✔ |
| F-04 | Gestionar catálogo de recursos | `CatalogoController`, `CuadrillaController`: consulta de máquinas, operarios y cuadrillas (datos precargados por Flyway) | `CatalogoServiceTest`, `CuadrillaServiceTest`, `CatalogoControllerSeguridadTest`; colección: 01-Setup-Datos > Recursos | ≈ recorte: sin altas ni ediciones |
| F-05 | Registrar ubicación de la cuadrilla (check-in) | `POST /api/recursos/cuadrillas/{id}/ubicaciones` (CAPATAZ de esa cuadrilla) | `CuadrillaServiceTest`; colección: check-in propio (200) y de una cuadrilla ajena (403) | ✔ |
| F-06 | Proponer y reservar recursos | `POST /api/recursos/internos/reservas` (SISTEMA); RN-04 a RN-08 | `ReservaServiceReglasTest`, `ReservaControllerContratoTest`, `OsrmProveedorRutasIntegrationTest`, `RecursosHttpClientTest`; colección: RN-05, RN-07 | ✔ |
| F-07 | Confirmar o liberar reservas | `PATCH /internos/reservas/{id}/confirmacion` y `/liberacion` | `ReservaServiceEstadosTest`, `ReservaControllerEstadosTest`; colección: RN-05 (liberación) | ✔ |
| F-08 | Programar órdenes diarias | `POST /api/operaciones/ordenes` (solo PLANIFICADOR) | `OrdenTrabajoServiceCrearOrdenTest`, `SeguridadEndpointsTest`; el seed `V4__seed_demo_orders.sql` crea tres; colección: 02-Seguridad > Operaciones | ✔ |
| F-09 | Configurar umbrales climáticos | `PUT/GET /umbrales-climaticos/{tipoTrabajo}` (ADMIN) | `OrdenTrabajoServiceHabilitacionTest`, `SeguridadEndpointsTest`; colección: RN-09 | ✔ |
| F-10 | Evaluar la habilitación diaria | `POST /habilitaciones-diarias`, `POST /ordenes/{id}/habilitacion`; job diario | `HabilitacionDiariaServiceTest`, `JobHabilitacionDiariaTest`, `OrdenTrabajoServiceHabilitacionTest`, `OpenMeteoServiceTest`; colección: 03-Flujo-Principal | ✔ |
| F-11 | Confirmar orden y resolver clima incierto | `PATCH /ordenes/{id}/inicio`, `/rechazo`, `/resolucion-climatica` (CAPATAZ) | `EjecucionServiceTest`; colección: 03-Flujo-Principal, RN-10 | ✔ |
| F-12 | Suspender y reprogramar | Saga en `SuspensionService`: libera reserva, reprograma en Obras, crea orden nueva | `SuspensionServiceTest` (9), `ObrasHttpClientTest`; colección: Escenarios 1 y 3, RN-12 | ✔ (sin reprogramación ni cancelación manual de órdenes) |
| F-13 | Registrar la ejecución | `PATCH /ordenes/{id}/inicio`, `POST /avances` (al 100 % la orden queda COMPLETADA) | `EjecucionServiceTest`, `FaseServiceAvanceTest`; colección: 03-Flujo-Principal | ≈ recorte: sin consumo real de materiales ni horas de máquina |
| F-14 | Gestionar fase en riesgo | A la tercera suspensión la fase queda `EN_RIESGO` en Obras; el PLANIFICADOR reprograma la fase y crea la orden nueva (`cerrarSuspensionesDeLaFase`) | `SuspensionServiceTest`, `OrdenTrabajoServiceCrearOrdenTest`; colección: RN-12 | ≈ recorte: sin listado de fases en riesgo ni decisión registrada, límite fijo en 3 |
| F-15 | Consultar asignaciones del día (OPERARIO) | — | — | ✘ quitada con el recorte de alcance, junto con el rol OPERARIO |
| F-16 | Consultar historial y evaluaciones (AUDITOR) | `GET /ordenes` (con filtros por estado, fecha y obra), `GET /ordenes/{id}/evaluaciones-climaticas`, `GET /suspensiones` | `OrdenTrabajoServiceListadoTest`, `SeguridadEndpointsTest`; colección: 02-Seguridad > Operaciones y 03-Flujo-Principal | ✔ lo leen PLANIFICADOR, CAPATAZ y AUDITOR |
| F-17 | Autenticar y autorizar | Keycloak, gateway con reglas por ruta, método y rol, `@PreAuthorize` en cada servicio; 401 y 403 en formato estándar (commons) | `SeguridadGatewayTests`, `ReglasPorRolGatewayTests`, `SeguridadEndpointsTest`, `ReservaControllerSeguridadTest`, `CatalogoControllerSeguridadTest`, `ProblemDetailSeguridadTest`; colección: 02-Seguridad | ✔ |

## 2. Escenarios del compromiso (sección 11)

| # | Escenario | Cómo se prueba | Evidencia | Estado |
|---|---|---|---|---|
| 1 | Suspensión por clima y replanificación (RN-02, RN-09) | El ADMIN baja los umbrales de BASE y se corre la habilitación de mañana: la orden 2 pasa a REPROGRAMADA, Recursos libera la reserva, Obras reprograma la fase y se crea una orden nueva | `SuspensionServiceTest`; colección: 03-Flujo-Principal > Escenarios 1 y 3 | ✔ verificado sobre el stack |
| 2 | Open-Meteo no disponible (RN-10) | Apuntar `APP_OPEN_METEO_URL` a un host inexistente y habilitar: la orden queda PENDIENTE_CONFIRMACION_CLIMATICA tras los reintentos | `OpenMeteoServiceTest`, `OrdenTrabajoServiceHabilitacionTest`; colección: 05-Fallas-y-Resiliencia | ✔ (sin caché del último pronóstico, quitada con el recorte) |
| 3 | Rechazo de recursos por certificación o distancia (RN-04, RN-07, RN-08) | La orden 3 del seed (tramo lejano de Río Cuarto) se rechaza por RN-07 (215 min contra un máximo de 90) y queda PROGRAMADA con el motivo; RN-04 y RN-08 por los endpoints de Recursos | `ReservaServiceReglasTest`; colección: 03-Flujo-Principal > Escenarios 1 y 3, RN-04, RN-07 y RN-08 | ✔ |
| 4 | Control de acceso por rol (rol insuficiente → 403) | El cuestionario usaba el OPERARIO; tras el recorte, un CAPATAZ intenta crear una obra | `SeguridadEndpointsTest`, `ReglasPorRolGatewayTests`; colección: 02-Seguridad > CAPATAZ crea obra | ✔ |
| 5 | Vencimiento de reservas provisorias | Job de Recursos (`ExpiracionReservasJob`, cada 60 s) pasa a EXPIRADA las reservas vencidas y libera la cuadrilla | `ExpiracionReservasTest`; verificado a mano: con el vencimiento adelantado en la base, la reserva pasa de PROVISORIA a EXPIRADA en menos de un minuto (se mira en la base: `GET /reservas/{id}` se quitó con el recorte) | ✔ el TTL (30 min) está fijo en el código |

## 3. Librería común, seguridad y despliegue (secciones 09 y 10)

| Requisito | Implementación | Evidencia | Estado |
|---|---|---|---|
| `obras-commons` con conversor de roles y resource server | `obras-commons` | usado por los tres servicios y el gateway | ✔ |
| Manejador global de errores en formato estándar | `GlobalExceptionHandler` | tests de commons (Rama 1): 404, 405, JSON ilegible y 401/403 en formato estándar | ✔ |
| Filtro de correlation-id | `CorrelationIdFilter` en commons y en el gateway; el patrón de log de los cuatro servicios incluye el id | `CorrelationIdEnLogsTests` (gateway) | ≈ el gateway ignora el `X-Correlation-Id` del cliente y genera el suyo; el id aparece en las líneas que cada servicio emite (por ejemplo, el rechazo de una orden en Operaciones), no en todos los logs de un mismo pedido |
| Cliente HTTP con token de servicio | `SistemaRequestInterceptor`, `SistemaTokenService` (con timeout y reintento) y el `sistemaRestClient` | `SistemaTokenServiceTest`, `ObrasHttpClientTest`, `RecursosHttpClientTest` | ✔ |
| Value Object `Coordenada` | `obras-commons/.../dto/Coordenada.java`, usada por `OsrmProveedorRutas` (Recursos, URL de OSRM en orden longitud,latitud) y `OpenMeteoService` (Operaciones, valida el tramo) | `CoordenadaTest`; las requests además validan los rangos con Bean Validation | ✔ |
| Despliegue con Docker Compose | `docker-compose.yml`, un `Dockerfile` por módulo, healthchecks, `.env.example` | README, «Levantarlo desde cero» | ✔ |
| Datos iniciales con Flyway | `V*.sql` de cada servicio; el seed de demo siembra tres órdenes con fechas relativas a hoy en hora de Argentina | `docs/arquitectura.md`, sección 2; colección: 03-Flujo-Principal | ✔ |
| Colección ejecutable | `coleccion/` (Postman) | 83 requests tras el recorte de alcance (antes 111); la corrida con Newman sobre el stack desde cero se repite al cerrar el recorte | ✔ falta la corrida final entre los tres |

## 4. Diferencias entre el cuestionario y lo implementado

- **Versiones:** el cuestionario dice Spring Boot 3.2; se usa Spring Boot 4.1.2 con Java 25.
- **Identificadores:** el cuestionario decía uuid; la implementación usa `BIGSERIAL`.
- **Puerto del gateway:** el cuestionario dice 8080 para `baseUrl`; el gateway publica 8081 y Keycloak ocupa el 8080.
- **Códigos de error:** el cuestionario indica 409 para RN-01 y para RN-11 (y en la sección 11 «400/422» para RN-11). La implementación da 409 en RN-01 y 422 en RN-11; a confirmar con la cátedra o alinear.
- **Nombres de carpetas de la colección:** se siguen los del cuestionario (`00-Auth` a `05-Fallas-y-Resiliencia`).
- **Variable `correlation_id` en la colección:** el cuestionario prevé un UUID generado por request con el encabezado `X-Correlation-Id`; la colección lo envía en cada request (variable `correlation_id`).
- **RN-07 sin recursos:** el cuestionario dice que la orden queda `SIN_RECURSOS`; la implementación deja la orden en su estado anterior y devuelve el motivo.
- **RN-05:** el conflicto de horario responde 422 con `codigoRegla` RN-05; el cuestionario dice 409.
- **OSRM `/table`:** se usa solo `/route` (una llamada por cuadrilla y otra para la planta), con caché y 1 pedido por segundo.
- **Ruta estimada:** con OSRM caído la reserva usa la estimación marcada `ESTIMADA`; no se pide confirmación del CAPATAZ cuando el margen de la mezcla es ajustado.
- **WireMock:** las APIs externas se simulan con `MockRestServiceServer` (tests sin red, sin servidor aparte).
- **Recorte de alcance (2026-11-14):** de 54 a 32 endpoints (31 del cuestionario más `GET /obras/{id}/tramos`), sin rol OPERARIO, sin cierre con consumo ni horas de máquina, sin fases en riesgo con decisión, sin límite configurable, sin chequeo de mantenimiento y sin caché del pronóstico. Detalle y motivo en `docs/DECISIONES.md`; se pidió autorización a la cátedra (`docs/aviso-cambios-catedra.md`).
- **Nombre de la interfaz del clima:** `InterfazClimaService` cumple el papel de `ProveedorClima`.
- **Seed:** la fase 2 sembrada es BASE; el cuestionario la describe como Carpeta Asfáltica.
- **Resueltas el 2026-11-13:** el clima se evalúa sobre la ventana de ejecución de la fecha de la orden (peor valor de 08:00 a 13:00, configurable); OSRM limitado a 1 pedido por segundo y caché de rutas con vencimiento de 6 h; `Coordenada` de commons en uso. Detalle en `docs/DECISIONES.md`.

## 5. Pendiente

- La verificación final desde cero entre los tres integrantes, con la colección completa.
- Las dos diferencias con el cuestionario (RN-11 y RN-07) quedan a decisión del equipo.
