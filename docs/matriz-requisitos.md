# Matriz requisito → implementación → evidencia

Cubre lo que el equipo comprometió en el cuestionario: las funcionalidades F-01 a F-17 (sección 04), los cinco escenarios del compromiso (sección 11), la librería común (sección 09) y el despliegue (sección 10). Las reglas RN-01 a RN-12 están en [`trazabilidad-reglas.md`](trazabilidad-reglas.md); el detalle por servicio, en `trazabilidad-ms-obras.md` y `trazabilidad-ms-operaciones.md`.

Estado: ✔ implementado y con evidencia · ≈ implementado con una diferencia o con evidencia parcial · ✘ pendiente (se indica la rama que lo cubre).

Las rutas son las del gateway (prefijo `/api/<servicio>`). Las carpetas citadas son las de la colección Postman en `coleccion/`.

## 1. Funcionalidades (sección 04)

| ID | Funcionalidad | Implementación | Evidencia | Estado |
|---|---|---|---|---|
| F-01 | Gestionar obras, tramos y fases | `ObraController`, `TramoController` (alta y edición de obras, tramos y fases) | `ObraControllerTest`, `ObraServiceTest`, `TramoControllerTest`, `TramoControllerEdicionTest`; colección: 01-Setup-Datos | ✔ |
| F-02 | Armar cronograma y dependencias | `GET /obras/{id}/cronograma`, `PATCH /fases/{id}/programacion` (cascada, RN-02) | `CronogramaServiceTest`, `FaseServiceTest`, `ObraControllerConsultasTest`; colección: 01-Setup-Datos | ✔ |
| F-03 | Calcular materiales | `GET /fases/{id}/materiales` (RN-03) | `MaterialServiceTest`, `FaseControllerLecturaTest`; colección: 04-Reglas-Negocio > RN-03 | ✔ |
| F-04 | Gestionar catálogo de recursos | Hoy solo existen los datos de `V2__datos_demo.sql`; faltan los endpoints de altas | — | ✘ Rama 1 |
| F-05 | Registrar ubicación de la cuadrilla (check-in) | Falta `POST /cuadrillas/{id}/ubicaciones` | — | ✘ Rama 1 |
| F-06 | Proponer y reservar recursos | `POST /api/recursos/internos/reservas` (SISTEMA); RN-04 a RN-08 | `ReservaServiceReglasTest`, `ReservaControllerContratoTest`, `OsrmProveedorRutasIntegrationTest`, `RecursosHttpClientTest`; colección: RN-05, RN-07 | ✔ |
| F-07 | Confirmar o liberar reservas | `PATCH /internos/reservas/{id}/confirmacion` y `/liberacion` | `ReservaServiceEstadosTest`, `ReservaControllerEstadosTest`; colección: RN-05 (liberación) | ✔ |
| F-08 | Programar órdenes diarias | `POST /api/operaciones/ordenes` | `OrdenTrabajoServiceCrearOrdenTest`; el seed `V4__seed_demo_orders.sql` crea tres | ≈ hoy también lo puede el ADMIN (Rama 2 lo restringe) |
| F-09 | Configurar umbrales climáticos | `PUT/GET /umbrales-climaticos/{tipoTrabajo}` (ADMIN) | `OrdenTrabajoServiceHabilitacionTest`, `SeguridadEndpointsTest`; colección: RN-09 | ✔ |
| F-10 | Evaluar la habilitación diaria | `POST /habilitaciones-diarias`, `POST /ordenes/{id}/habilitacion`; job diario | `HabilitacionDiariaServiceTest`, `JobHabilitacionDiariaTest`, `OrdenTrabajoServiceHabilitacionTest`, `OpenMeteoServiceTest`; colección: 03-Flujo-Principal | ✔ |
| F-11 | Confirmar orden y resolver clima incierto | `PATCH /ordenes/{id}/inicio`, `/rechazo`, `/resolucion-climatica` (CAPATAZ) | `EjecucionServiceTest`; colección: 03-Flujo-Principal, RN-10 | ✔ |
| F-12 | Suspender y reprogramar | Saga en `SuspensionService`: libera reserva, reprograma en Obras, crea orden nueva | `SuspensionServiceTest` (7), `ObrasHttpClientTest`, `OrdenTrabajoServiceCancelacionTest`; colección: RN-12 | ✔ |
| F-13 | Registrar la ejecución | `PATCH /ordenes/{id}/inicio`, `POST /avances`, `PATCH /cierre` | `EjecucionServiceTest`, `FaseServiceAvanceTest`; colección: 03-Flujo-Principal | ✔ |
| F-14 | Gestionar fase en riesgo | `GET /fases-en-riesgo`, `POST /fases-en-riesgo/{faseId}/decisiones` | `FaseEnRiesgoServiceTest` (7); colección: RN-12 | ✔ |
| F-15 | Consultar asignaciones del día (OPERARIO) | Falta `GET /operarios/me/asignaciones` | — | ✘ Rama 1 |
| F-16 | Consultar historial y evaluaciones (AUDITOR) | `GET /ordenes`, `GET /ordenes/{id}/evaluaciones-climaticas`, `GET /suspensiones` | `OrdenTrabajoServiceHabilitacionTest` | ≈ hoy lo lee cualquier autenticado, incluso el OPERARIO (Rama 2 lo restringe) |
| F-17 | Autenticar y autorizar | Keycloak, gateway con validación de JWT, `@PreAuthorize` en cada servicio | `SeguridadGatewayTests`, `SeguridadEndpointsTest`, `ReservaControllerSeguridadTest`; colección: 02-Seguridad | ≈ 401/403 sin formato estándar hasta la Rama 1 |

## 2. Escenarios del compromiso (sección 11)

| # | Escenario | Cómo se prueba | Evidencia | Estado |
|---|---|---|---|---|
| 1 | Suspensión por clima y replanificación (RN-02, RN-09) | El ADMIN baja los umbrales y se corre la habilitación; la orden pasa a SUSPENDIDA, Recursos libera y Obras reprograma | `SuspensionServiceTest`; colección: RN-09 y 03-Flujo-Principal | ≈ falta documentar el truco en el README (Rama 2) |
| 2 | Open-Meteo no disponible (RN-10) | Apagar el acceso al clima y habilitar: la orden queda PENDIENTE_CONFIRMACION_CLIMATICA | `OpenMeteoServiceTest`, `OrdenTrabajoServiceHabilitacionTest`; colección: 05-Fallas-y-Resiliencia | ≈ falta caché con TTL del pronóstico (Rama 2) |
| 3 | Rechazo de recursos por certificación o distancia (RN-04, RN-07, RN-08) | Reserva sobre un tramo lejano: Recursos rechaza con el motivo y la orden queda en su estado | `ReservaServiceReglasTest`; colección: RN-07 | ≈ la orden 3 del seed necesita la migración V5 de Obras (Rama 2); RN-04 y RN-08 en la segunda pasada |
| 4 | Control de acceso por rol (OPERARIO → 403) | Token de OPERARIO intenta crear una obra | `SeguridadEndpointsTest`; colección: 02-Seguridad > OPERARIO crea obra | ✔ |
| 5 | Vencimiento de reservas provisorias | Detener Operaciones tras reservar; la reserva debe expirar por TTL | El TTL (30 min) y `expirarReservasVencidas` existen en el repositorio, pero ningún job lo invoca | ✘ Rama 1 |

## 3. Librería común, seguridad y despliegue (secciones 09 y 10)

| Requisito | Implementación | Evidencia | Estado |
|---|---|---|---|
| `obras-commons` con conversor de roles y resource server | `obras-commons` | usado por los tres servicios y el gateway | ✔ |
| Manejador global de errores en formato estándar | `GlobalExceptionHandler` | tests de cada servicio; 404, 405, 400 y 401/403 en formato estándar pendientes | ≈ Rama 1 |
| Filtro de correlation-id | `CorrelationIdFilter` en commons y en el gateway | falta verificarlo en los logs de los cuatro servicios | ≈ Rama 2 |
| Cliente HTTP con token de servicio | `SistemaRequestInterceptor` y el `sistemaRestClient` | `ObrasHttpClientTest`, `RecursosHttpClientTest`; sin timeout todavía | ≈ Rama 2 |
| Value Object `Coordenada` | `obras-commons/.../dto/Coordenada.java` | verificar si los servicios la usan; las requests validan los rangos con Bean Validation | ≈ |
| Despliegue con Docker Compose | `docker-compose.yml`, un `Dockerfile` por módulo, healthchecks, `.env.example` | README, «Levantarlo desde cero» | ✔ |
| Datos iniciales con Flyway | `V*.sql` de cada servicio | `docs/arquitectura.md`, sección 2 | ≈ seed de las 3 órdenes en curso (Rama 2) |
| Colección ejecutable | `coleccion/` (Postman) | `coleccion/README.md` | ≈ segunda pasada y corrida completa pendientes |

## 4. Diferencias entre el cuestionario y lo implementado

- **Versiones:** el cuestionario dice Spring Boot 3.2; se usa Spring Boot 4.1.2 con Java 25.
- **Identificadores:** el cuestionario decía uuid; la implementación usa `BIGSERIAL`.
- **Puerto del gateway:** el cuestionario dice 8080 para `baseUrl`; el gateway publica 8081 y Keycloak ocupa el 8080.
- **Códigos de error:** el cuestionario indica 409 para RN-01 y para RN-11 (y en la sección 11 «400/422» para RN-11). La implementación da 409 en RN-01 y 422 en RN-11; a confirmar con la cátedra o alinear.
- **Nombres de carpetas de la colección:** se siguen los del cuestionario (`00-Auth` a `05-Fallas-y-Resiliencia`).
- **Variable `correlation_id` en la colección:** el cuestionario prevé un UUID generado por request con el encabezado `X-Correlation-Id`; la colección todavía no lo envía (lo agrega la segunda pasada).
- **RN-07 sin recursos:** el cuestionario dice que la orden queda `SIN_RECURSOS`; la implementación deja la orden en su estado anterior y devuelve el motivo.

## 5. Se completa en la segunda pasada

Los estados ✘ y ≈ que dependen de las Ramas 1 y 2 se vuelven a evaluar cuando se mergeen, con la colección corrida sobre el stack desde cero.
