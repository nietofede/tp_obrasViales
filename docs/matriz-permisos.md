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
| Consultar tramo y fase; reprogramar en cascada | obras | — | — | — | — | — | ✔ | Propuesta: lo llama la saga |
| Consultar (solo lectura) | los tres | ? | ? | ? | ? | ✔ | — | Propuesta |
| ABM de obras, tramos y fases | obras | ? | ? | — | — | — | — | A definir en el paso 8 |
| ABM de máquinas, operarios y cuadrillas | recursos | ? | ? | — | — | — | — | A definir en el paso 9 |
| Funciones del OPERARIO | — | — | — | — | ? | — | — | Los documentos no lo detallan |

## 4. Pruebas obligatorias por endpoint

Tres casos para cada endpoint protegido, con test automático y request en la colección.

| Endpoint | Sin token (401) | Rol insuficiente (403) | Rol correcto (2xx) | Test | Request |
|---|---|---|---|---|---|
| (se completa en los pasos 8 a 10) | | | | | |