# Matriz de permisos

Estado: diseño inicial. Se completa con los endpoints reales en los pasos 8, 9 y 10.
Roles (constantes en `obras-commons`, clase `Roles`): ADMIN, PLANIFICADOR, CAPATAZ, OPERARIO, AUDITOR y la cuenta de servicio SISTEMA.

## 1. Quién valida qué

| Capa | Qué hace |
|---|---|
| API Gateway (8081) | Solo **autentica**: valida el JWT. No mira roles. |
| Cada servicio (8082 a 8084) | Vuelve a validar el JWT y **autoriza por rol** con `@PreAuthorize`. |

El rol viene del claim `realm_access.roles` del JWT de Keycloak. `KeycloakConversorRoles` le antepone `ROLE_`, por eso en el código se usa `hasRole('ADMIN')`.

## 2. Rutas sin token

| Ruta | Dónde | Motivo |
|---|---|---|
| `/actuator/health/**` | Gateway | Healthcheck de Docker |
| `/actuator/**` | Servicios | Monitoreo |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Servicios | Documentación; se apagan en producción con `SPRINGDOC_*_ENABLED=false` |

Todo lo demás exige JWT.

## 3. Permisos por capacidad (propuesta a confirmar)

Leyenda: ✔ permitido, — no permitido, ? a definir con el equipo.

| Capacidad | Servicio | ADMIN | PLANIFICADOR | CAPATAZ | OPERARIO | AUDITOR | SISTEMA | Fuente |
|---|---|---|---|---|---|---|---|---|
| Iniciar la habilitación diaria de una orden | operaciones | — | ✔ | — | — | — | ✔ (job diario) | CLAUDE.md |
| Confirmar una orden habilitada | operaciones | — | — | ✔ | — | — | — | CLAUDE.md |
| Decidir sobre una fase en riesgo | operaciones | — | ✔ | — | — | — | — | CLAUDE.md |
| Configurar umbrales climáticos | operaciones | ✔ | — | — | — | — | — | CLAUDE.md |
| Check-in de ubicación | recursos | — | — | ✔ | — | — | — | CLAUDE.md |
| Proponer y reservar / liberar reservas | recursos | — | — | — | — | — | ✔ | Propuesta: lo llama la saga |
| Consultar (solo lectura) | los tres | ? | ? | ? | ? | ✔ | — | Propuesta |
| Crear y editar obras y tramos; crear fases | obras | — | ✔ | — | — | — | — | Cuestionario, sección 07: PLANIFICADOR |
| Consultar el cronograma de un tramo | obras | — | ✔ | ✔ | — | ✔ | — | Propuesta |
| Consultar el cronograma de una obra | obras | — | ✔ | ✔ | — | ✔ | — | Cuestionario, sección 07 |
| Consultar obras (con estado y avance), un tramo, una fase y los tramos de una obra | obras | — | ✔ | ✔ | — | ✔ | — | Cuestionario, sección 07 |
| Consultar los materiales de una fase (RN-03) | obras | — | ✔ | ✔ | — | — | — | Cuestionario, sección 07 |
| Iniciar una fase (RN-01) | obras | — | — | ✔ | — | — | ✔ | TP: el CAPATAZ registra inicio y fin; Operaciones la llama como SISTEMA o con el token del capataz |
| Reprogramar una fase en cascada (RN-02) | obras | — | ✔ | — | — | — | ✔ | TP: el PLANIFICADOR arma el cronograma; la saga de suspensión la llama como SISTEMA |
| Contexto de ejecución de una fase para Operaciones (incluye materiales, RN-03) | obras | — | — | — | — | — | ✔ | Cuestionario, sección 07: solo SISTEMA |
| Registrar el avance de una fase (completa la fase al 100 %) | obras | — | — | — | — | — | ✔ | Cuestionario, sección 07: solo SISTEMA |
| Marcar una fase en riesgo | obras | — | — | — | — | — | ✔ | Cuestionario, sección 07: solo SISTEMA |
| ABM de máquinas, operarios y cuadrillas | recursos | ? | ? | — | — | — | — | A definir en el paso 9 |
| Funciones del OPERARIO | — | — | — | — | ? | — | — | Los documentos no lo detallan |

## 4. Pruebas obligatorias por endpoint

Tres casos para cada endpoint protegido, con test automático y request en la colección.

| Endpoint | Sin token (401) | Rol insuficiente (403) | Rol correcto (2xx) | Test | Request |
|---|---|---|---|---|---|
| POST /api/obras | 401 | 403 con CAPATAZ | 201 con PLANIFICADOR | ObraControllerTest | docs/ms-obras-api.md |
| GET /api/obras | 401 | 403 con OPERARIO | 200 con AUDITOR | ObraControllerConsultasTest | docs/ms-obras-api.md |
| GET /api/obras/{obraId} | 401 | 403 con OPERARIO | 200 con CAPATAZ | ObraControllerConsultasTest | docs/ms-obras-api.md |
| PUT /api/obras/{obraId} | 401 | 403 con CAPATAZ | 200 con PLANIFICADOR | ObraControllerConsultasTest | docs/ms-obras-api.md |
| POST /api/obras/{obraId}/tramos | 401 | 403 con CAPATAZ | 201 con PLANIFICADOR | ObraControllerTest | docs/ms-obras-api.md |
| GET /api/obras/{obraId}/tramos | 401 | 403 con OPERARIO | 200 con PLANIFICADOR | ObraControllerConsultasTest | docs/ms-obras-api.md |
| GET /api/obras/{obraId}/cronograma | 401 | 403 con OPERARIO | 200 con AUDITOR | ObraControllerConsultasTest | docs/ms-obras-api.md |
| GET /api/obras/tramos/{tramoId} | 401 | 403 con OPERARIO | 200 con AUDITOR | TramoControllerEdicionTest | docs/ms-obras-api.md |
| PUT /api/obras/tramos/{tramoId} | 401 | 403 con CAPATAZ | 200 con PLANIFICADOR | TramoControllerEdicionTest | docs/ms-obras-api.md |
| POST /api/obras/tramos/{tramoId}/fases | 401 | 403 con CAPATAZ | 201 con PLANIFICADOR | TramoControllerTest | docs/ms-obras-api.md |
| GET /api/obras/tramos/{tramoId}/fases | 401 | 403 con OPERARIO | 200 con AUDITOR | TramoControllerTest | docs/ms-obras-api.md |
| GET /api/obras/fases/{id} | 401 | 403 con OPERARIO | 200 con AUDITOR | FaseControllerLecturaTest | docs/ms-obras-api.md |
| GET /api/obras/fases/{id}/materiales | 401 | 403 con AUDITOR | 200 con CAPATAZ | FaseControllerLecturaTest | docs/ms-obras-api.md |
| POST /api/obras/fases/{id}/iniciar | 401 | 403 con OPERARIO | 200 con CAPATAZ | FaseControllerTest | docs/ms-obras-api.md |
| PATCH /api/obras/fases/{id}/programacion | 401 | 403 con CAPATAZ | 200 con PLANIFICADOR | FaseControllerTest y FaseControllerLecturaTest | docs/ms-obras-api.md |
| GET /api/obras/internos/fases/{id}/contexto-ejecucion | 401 | 403 con OPERARIO | 200 con SISTEMA | OperacionControllerTest | docs/ms-obras-api.md |
| POST /api/obras/internos/fases/{id}/avances | 401 | 403 con PLANIFICADOR | 201 con SISTEMA | FaseInternaControllerTest | docs/ms-obras-api.md |
| PATCH /api/obras/internos/fases/{id}/riesgo | 401 | 403 con CAPATAZ | 200 con SISTEMA | FaseInternaControllerTest | docs/ms-obras-api.md |

### ms-operaciones

Los tres casos de cada endpoint están en `SeguridadEndpointsTest` (parametrizado: una fila por endpoint y tres chequeos por fila). Los endpoints que solo exigen estar autenticado no tienen caso 403.

| Endpoint | Sin token (401) | Rol insuficiente (403) | Rol correcto (2xx) | Test |
|---|---|---|---|---|
| POST /api/operaciones/ordenes | 401 | 403 con CAPATAZ | 201 con PLANIFICADOR | SeguridadEndpointsTest |
| GET /api/operaciones/ordenes | 401 | — (cualquier autenticado) | 200 con AUDITOR | SeguridadEndpointsTest |
| GET /api/operaciones/ordenes/{id} | 401 | — (cualquier autenticado) | 200 con AUDITOR | SeguridadEndpointsTest |
| PATCH /api/operaciones/ordenes/{id}/estado | 401 | 403 con AUDITOR | 200 con CAPATAZ | SeguridadEndpointsTest |
| POST /api/operaciones/ordenes/{id}/habilitacion | 401 | 403 con CAPATAZ | 200 con PLANIFICADOR | SeguridadEndpointsTest |
| GET /api/operaciones/ordenes/{id}/evaluaciones-climaticas | 401 | — (cualquier autenticado) | 200 con AUDITOR | SeguridadEndpointsTest |
| PATCH /api/operaciones/ordenes/{id}/cancelacion | 401 | 403 con CAPATAZ | 200 con PLANIFICADOR | SeguridadEndpointsTest |
| PATCH /api/operaciones/ordenes/{id}/reprogramacion | 401 | 403 con CAPATAZ | 200 con PLANIFICADOR | SeguridadEndpointsTest |
| PATCH /api/operaciones/ordenes/{id}/resolucion-climatica | 401 | 403 con PLANIFICADOR | 200 con CAPATAZ | SeguridadEndpointsTest |
| PATCH /api/operaciones/ordenes/{id}/inicio | 401 | 403 con PLANIFICADOR | 200 con CAPATAZ | SeguridadEndpointsTest |
| PATCH /api/operaciones/ordenes/{id}/rechazo | 401 | 403 con PLANIFICADOR | 200 con CAPATAZ | SeguridadEndpointsTest |
| POST /api/operaciones/ordenes/{id}/avances | 401 | 403 con PLANIFICADOR | 201 con CAPATAZ | SeguridadEndpointsTest |
| PATCH /api/operaciones/ordenes/{id}/cierre | 401 | 403 con PLANIFICADOR | 200 con CAPATAZ | SeguridadEndpointsTest |
| POST /api/operaciones/habilitaciones-diarias | 401 | 403 con CAPATAZ | 200 con PLANIFICADOR (o SISTEMA) | SeguridadEndpointsTest |
| GET /api/operaciones/suspensiones | 401 | 403 con CAPATAZ | 200 con AUDITOR | SeguridadEndpointsTest |
| GET /api/operaciones/fases-en-riesgo | 401 | 403 con CAPATAZ | 200 con AUDITOR | SeguridadEndpointsTest |
| POST /api/operaciones/fases-en-riesgo/{faseId}/decisiones | 401 | 403 con AUDITOR | 200 con PLANIFICADOR | SeguridadEndpointsTest |
| PUT /api/operaciones/configuracion/limite-suspensiones | 401 | 403 con PLANIFICADOR | 200 con ADMIN | SeguridadEndpointsTest |
| GET /api/operaciones/umbrales-climaticos | 401 | 403 con OPERARIO | 200 con AUDITOR | SeguridadEndpointsTest |
| PUT /api/operaciones/umbrales-climaticos/{tipoTrabajo} | 401 | 403 con PLANIFICADOR | 200 con ADMIN | SeguridadEndpointsTest |
