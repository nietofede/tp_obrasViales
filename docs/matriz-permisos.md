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
| Crear obras, tramos y fases | obras | — | ✔ | — | — | — | — | TP: el PLANIFICADOR crea obras, tramos y fases |
| Consultar el cronograma de un tramo | obras | — | ✔ | ✔ | — | ✔ | — | Propuesta |
| Consultar obras y los tramos de una obra | obras | — | ✔ | ✔ | — | ✔ | — | Propuesta: pedido en la revisión del PR |
| Iniciar una fase (RN-01) | obras | — | — | ✔ | — | — | ✔ | TP: el CAPATAZ registra inicio y fin; Operaciones la llama como SISTEMA o con el token del capataz |
| Reprogramar una fase en cascada (RN-02) | obras | — | ✔ | — | — | — | ✔ | TP: el PLANIFICADOR arma el cronograma; la saga de suspensión la llama como SISTEMA |
| Datos de una fase para Operaciones (incluye materiales, RN-03) | obras | — | ✔ | ✔ | — | — | ✔ | Propuesta: los consume Operaciones |
| ABM de máquinas, operarios y cuadrillas | recursos | ? | ? | — | — | — | — | A definir en el paso 9 |
| Funciones del OPERARIO | — | — | — | — | ? | — | — | Los documentos no lo detallan |

## 4. Pruebas obligatorias por endpoint

Tres casos para cada endpoint protegido, con test automático y request en la colección.

| Endpoint | Sin token (401) | Rol insuficiente (403) | Rol correcto (2xx) | Test | Request |
|---|---|---|---|---|---|
| POST /api/obras | 401 | 403 con CAPATAZ | 201 con PLANIFICADOR | ObraControllerTest | colección (tarea 12) |
| POST /api/obras/{obraId}/tramos | 401 | 403 con CAPATAZ | 201 con PLANIFICADOR | ObraControllerTest | colección (tarea 12) |
| POST /api/obras/tramos/{tramoId}/fases | 401 | 403 con CAPATAZ | 201 con PLANIFICADOR | TramoControllerTest | colección (tarea 12) |
| GET /api/obras/tramos/{tramoId}/fases | 401 | 403 con OPERARIO | 200 con AUDITOR | TramoControllerTest | colección (tarea 12) |
| POST /api/obras/fases/{id}/iniciar | 401 | 403 con OPERARIO | 200 con CAPATAZ | FaseControllerTest | colección (tarea 12) |
| POST /api/obras/fases/{id}/reprogramar | 401 | 403 con CAPATAZ | 200 con PLANIFICADOR | FaseControllerTest | colección (tarea 12) |
| GET /api/obras/fases/{id}/datos-operacion | 401 | 403 con OPERARIO | 200 con SISTEMA | OperacionControllerTest | colección (tarea 12) |
| GET /api/obras | 401 | 403 con OPERARIO | 200 con AUDITOR | ObraControllerConsultasTest | colección (tarea 12) |
| GET /api/obras/{obraId} | 401 | 403 con OPERARIO | 200 con CAPATAZ | ObraControllerConsultasTest | colección (tarea 12) |
| GET /api/obras/{obraId}/tramos | 401 | 403 con OPERARIO | 200 con PLANIFICADOR | ObraControllerConsultasTest | colección (tarea 12) |
