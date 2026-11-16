# Matriz de permisos

Estado: actualizada al recorte de alcance (32 endpoints; ver `docs/DECISIONES.md`, «Recorte de alcance»).
Roles (constantes en `obras-commons`, clase `Roles`): ADMIN, PLANIFICADOR, CAPATAZ, AUDITOR y la cuenta de servicio SISTEMA. El rol OPERARIO se quitó con el recorte: su única función era consultar sus asignaciones.

## 1. Quién valida qué

| Capa                        | Qué hace                                                            |
| --------------------------- | ------------------------------------------------------------------- |
| API Gateway (8081)          | Valida el JWT y aplica una regla gruesa por ruta, método y rol.     |
| Cada servicio (8082 a 8084) | Vuelve a validar el JWT y **autoriza por rol** con `@PreAuthorize`. |

El rol viene del claim `realm_access.roles` del JWT de Keycloak. `KeycloakConversorRoles` le antepone `ROLE_`, por eso en el código se usa `hasRole('ADMIN')`.

## 2. Rutas sin token

| Ruta                                                    | Dónde     | Motivo                                                                 |
| ------------------------------------------------------- | --------- | ---------------------------------------------------------------------- |
| `/actuator/health/**`                                   | Gateway   | Healthcheck de Docker                                                  |
| `/actuator/**`                                          | Servicios | Monitoreo                                                              |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Servicios | Documentación; se apagan en producción con `SPRINGDOC_*_ENABLED=false` |

Todo lo demás exige JWT.

## 3. Permisos por capacidad

Leyenda: ✔ permitido, — no permitido.

El gateway aplica una regla gruesa por ruta y método (la unión de los roles que algún endpoint de ese servicio acepta) y cada servicio afina con `@PreAuthorize`. Un rol sin ningún permiso en esa ruta recibe 403 en el gateway, aunque mande un cuerpo inválido; un rol que sí tiene permiso en otro endpoint de la misma ruta llega al servicio, que valida el cuerpo antes de evaluar el rol y puede responder 400. Las rutas `/api/*/internos/**` solo las abre el gateway al rol SISTEMA.

| Capacidad                                                                      | Servicio    | ADMIN | PLANIFICADOR | CAPATAZ | AUDITOR | SISTEMA        |
| ------------------------------------------------------------------------------ | ----------- | ----- | ------------ | ------- | ------- | -------------- |
| Habilitación de una orden o de las órdenes de una fecha                        | operaciones | —     | ✔            | —       | —       | ✔ (job diario) |
| Crear órdenes de trabajo (y así decidir sobre una fase en riesgo, RN-12)       | operaciones | —     | ✔            | —       | —       | —              |
| Consultar órdenes y sus evaluaciones climáticas                                | operaciones | —     | ✔            | ✔       | ✔       | —              |
| Resolver el clima incierto, iniciar, rechazar y registrar avances de una orden | operaciones | —     | —            | ✔       | —       | —              |
| Consultar el historial de suspensiones                                         | operaciones | —     | ✔            | —       | ✔       | —              |
| Consultar umbrales climáticos                                                  | operaciones | ✔     | ✔            | ✔       | ✔       | —              |
| Configurar umbrales climáticos                                                 | operaciones | ✔     | —            | —       | —       | —              |
| Consultar máquinas, operarios y cuadrillas                                     | recursos    | ✔     | ✔            | ✔       | ✔       | —              |
| Check-in de ubicación (solo el capataz de esa cuadrilla)                       | recursos    | —     | —            | ✔       | —       | —              |
| Proponer y reservar, confirmar y liberar reservas                              | recursos    | —     | —            | —       | —       | ✔              |
| Crear obras, tramos y fases                                                    | obras       | —     | ✔            | —       | —       | —              |
| Consultar obras, la obra completa, sus tramos y su cronograma                  | obras       | —     | ✔            | ✔       | ✔       | —              |
| Consultar los materiales de una fase (RN-03)                                   | obras       | —     | ✔            | ✔       | —       | —              |
| Reprogramar una fase en cascada (RN-02)                                        | obras       | —     | ✔            | —       | —       | ✔ (saga)       |
| Contexto de ejecución, avance y riesgo de una fase (para Operaciones)          | obras       | —     | —            | —       | —       | ✔              |

Quitado con el recorte: alta y edición de máquinas, operarios, certificaciones, plantas y cuadrillas; edición de la composición mínima y de la configuración de desplazamiento; asignaciones del operario; edición de obras y tramos; consultas sueltas de tramo, fase y reserva; cancelación y reprogramación manual de órdenes; cierre con consumo; fases en riesgo con su decisión; límite de suspensiones configurable.

## 4. Pruebas obligatorias por endpoint

Tres casos para cada endpoint protegido, con test automático. Las rutas quitadas de Operaciones dan 404 (`SeguridadEndpointsTest.lasRutasQuitadasYaNoExisten`).

### ms-obras (12)

| Endpoint                                              | Sin token (401) | Rol insuficiente (403) | Rol correcto (2xx)   | Test                                           |
| ----------------------------------------------------- | --------------- | ---------------------- | -------------------- | ---------------------------------------------- |
| POST /api/obras                                       | 401             | 403 con CAPATAZ        | 201 con PLANIFICADOR | ObraControllerTest                             |
| GET /api/obras                                        | 401             | 403 con ADMIN          | 200 con AUDITOR      | ObraControllerConsultasTest                    |
| GET /api/obras/{obraId}                               | 401             | 403 con ADMIN          | 200 con CAPATAZ      | ObraControllerConsultasTest                    |
| POST /api/obras/{obraId}/tramos                       | 401             | 403 con CAPATAZ        | 201 con PLANIFICADOR | ObraControllerTest                             |
| GET /api/obras/{obraId}/tramos                        | 401             | 403 con ADMIN          | 200 con PLANIFICADOR | ObraControllerConsultasTest                    |
| GET /api/obras/{obraId}/cronograma                    | 401             | 403 con ADMIN          | 200 con AUDITOR      | ObraControllerConsultasTest                    |
| POST /api/obras/tramos/{tramoId}/fases                | 401             | 403 con CAPATAZ        | 201 con PLANIFICADOR | TramoControllerTest                            |
| GET /api/obras/fases/{id}/materiales                  | 401             | 403 con AUDITOR        | 200 con CAPATAZ      | FaseControllerLecturaTest                      |
| PATCH /api/obras/fases/{id}/programacion              | 401             | 403 con CAPATAZ        | 200 con PLANIFICADOR | FaseControllerTest y FaseControllerLecturaTest |
| GET /api/obras/internos/fases/{id}/contexto-ejecucion | 401             | 403 con PLANIFICADOR   | 200 con SISTEMA      | OperacionControllerTest                        |
| POST /api/obras/internos/fases/{id}/avances           | 401             | 403 con PLANIFICADOR   | 201 con SISTEMA      | FaseInternaControllerTest                      |
| PATCH /api/obras/internos/fases/{id}/riesgo           | 401             | 403 con CAPATAZ        | 200 con SISTEMA      | FaseInternaControllerTest                      |

### ms-recursos (7)

| Endpoint                                                | Sin token (401) | Rol insuficiente (403)                 | Rol correcto (2xx) | Test                            |
| ------------------------------------------------------- | --------------- | -------------------------------------- | ------------------ | ------------------------------- |
| GET /api/recursos/maquinas                              | 401             | 403 con SISTEMA                        | 200 con AUDITOR    | CatalogoControllerSeguridadTest |
| GET /api/recursos/operarios                             | 401             | 403 con SISTEMA                        | 200 con AUDITOR    | CatalogoControllerSeguridadTest |
| GET /api/recursos/cuadrillas                            | 401             | 403 con SISTEMA                        | 200 con AUDITOR    | CatalogoControllerSeguridadTest |
| POST /api/recursos/cuadrillas/{id}/ubicaciones          | 401             | 403 con PLANIFICADOR                   | 200 con CAPATAZ    | CatalogoControllerSeguridadTest |
| POST /api/recursos/internos/reservas                    | 401             | 403 con cualquier rol que no es SISTEMA | 201 con SISTEMA    | ReservaControllerSeguridadTest  |
| PATCH /api/recursos/internos/reservas/{id}/confirmacion | 401             | 403 con cualquier rol que no es SISTEMA | 200 con SISTEMA    | ReservaControllerSeguridadTest  |
| PATCH /api/recursos/internos/reservas/{id}/liberacion   | 401             | 403 con cualquier rol que no es SISTEMA | 200 con SISTEMA    | ReservaControllerSeguridadTest  |

### ms-operaciones (13)

Los tres casos de cada endpoint están en `SeguridadEndpointsTest` (parametrizado: una fila por endpoint y tres chequeos por fila).

| Endpoint                                                  | Sin token (401) | Rol insuficiente (403) | Rol correcto (2xx)               | Test                   |
| --------------------------------------------------------- | --------------- | ---------------------- | -------------------------------- | ---------------------- |
| POST /api/operaciones/ordenes                             | 401             | 403 con ADMIN          | 201 con PLANIFICADOR             | SeguridadEndpointsTest |
| GET /api/operaciones/ordenes                              | 401             | 403 con ADMIN          | 200 con AUDITOR                  | SeguridadEndpointsTest |
| GET /api/operaciones/ordenes/{id}                         | 401             | 403 con ADMIN          | 200 con AUDITOR                  | SeguridadEndpointsTest |
| POST /api/operaciones/ordenes/{id}/habilitacion           | 401             | 403 con CAPATAZ        | 200 con PLANIFICADOR (o SISTEMA) | SeguridadEndpointsTest |
| GET /api/operaciones/ordenes/{id}/evaluaciones-climaticas | 401             | 403 con ADMIN          | 200 con CAPATAZ                  | SeguridadEndpointsTest |
| PATCH /api/operaciones/ordenes/{id}/resolucion-climatica  | 401             | 403 con PLANIFICADOR   | 200 con CAPATAZ                  | SeguridadEndpointsTest |
| PATCH /api/operaciones/ordenes/{id}/inicio                | 401             | 403 con PLANIFICADOR   | 200 con CAPATAZ                  | SeguridadEndpointsTest |
| PATCH /api/operaciones/ordenes/{id}/rechazo               | 401             | 403 con PLANIFICADOR   | 200 con CAPATAZ                  | SeguridadEndpointsTest |
| POST /api/operaciones/ordenes/{id}/avances                | 401             | 403 con PLANIFICADOR   | 201 con CAPATAZ                  | SeguridadEndpointsTest |
| POST /api/operaciones/habilitaciones-diarias              | 401             | 403 con CAPATAZ        | 200 con PLANIFICADOR (o SISTEMA) | SeguridadEndpointsTest |
| GET /api/operaciones/suspensiones                         | 401             | 403 con CAPATAZ        | 200 con AUDITOR                  | SeguridadEndpointsTest |
| GET /api/operaciones/umbrales-climaticos                  | 401             | 403 con SISTEMA        | 200 con AUDITOR                  | SeguridadEndpointsTest |
| PUT /api/operaciones/umbrales-climaticos/{tipoTrabajo}    | 401             | 403 con PLANIFICADOR   | 200 con ADMIN                    | SeguridadEndpointsTest |
