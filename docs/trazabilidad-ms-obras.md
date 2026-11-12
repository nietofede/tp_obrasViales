# Trazabilidad de ms-obras: requisito, implementación y evidencia

Sirve para ALC-01 de la rúbrica v05 («matriz de trazabilidad enunciado/compromiso → implementación → evidencia»). Alcance: lo que el equipo comprometió para MS-Obras en el cuestionario (secciones 04, 07 y 08). Es un borrador de Mateo; Hugo y Federico deberían revisarlo.

Estado: ✔ cumplido y con evidencia · ≈ cumplido con una diferencia anotada · ✘ pendiente.

## 1. Endpoints comprometidos (sección 07)

| # | Comprometido | Implementado | Roles | Evidencia | Estado |
|---|---|---|---|---|---|
| 1 | POST /obras | POST /api/obras | PLANIFICADOR | ObraControllerTest, ObraServiceTest | ✔ |
| 2 | GET /obras, con estado y avance | GET /api/obras | PLANIFICADOR, CAPATAZ, AUDITOR | ObraControllerConsultasTest, ObraServiceConsultasTest | ✔ |
| 3 | GET /obras/{id}, con tramos y fases | GET /api/obras/{obraId} | PLANIFICADOR, CAPATAZ, AUDITOR | ObraControllerConsultasTest, ObraServiceConsultasTest | ✔ |
| 4 | PUT /obras/{id} | PUT /api/obras/{obraId} | PLANIFICADOR | ObraControllerConsultasTest, ObraServiceConsultasTest | ✔ |
| 5 | POST /obras/{obraId}/tramos | POST /api/obras/{obraId}/tramos | PLANIFICADOR | ObraControllerTest, ObraServiceTest | ✔ |
| 6 | GET /tramos/{id} | GET /api/obras/tramos/{tramoId} | PLANIFICADOR, CAPATAZ, AUDITOR | TramoControllerEdicionTest, ObraServiceConsultasTest | ✔ |
| 7 | PUT /tramos/{id}, recalcula materiales | PUT /api/obras/tramos/{tramoId} | PLANIFICADOR | TramoControllerEdicionTest, ObraServiceConsultasTest | ≈ los materiales no se guardan: se recalculan solos al consultar |
| 8 | POST /tramos/{tramoId}/fases | POST /api/obras/tramos/{tramoId}/fases | PLANIFICADOR | TramoControllerTest, FaseServiceTest | ✔ |
| 9 | GET /fases/{id} | GET /api/obras/fases/{id} | PLANIFICADOR, CAPATAZ, AUDITOR | FaseControllerLecturaTest, FaseServiceAvanceTest | ✔ |
| 10 | GET /fases/{id}/materiales | GET /api/obras/fases/{id}/materiales | PLANIFICADOR, CAPATAZ | FaseControllerLecturaTest, FaseServiceAvanceTest, MaterialServiceTest | ✔ |
| 11 | GET /obras/{obraId}/cronograma | GET /api/obras/{obraId}/cronograma | PLANIFICADOR, CAPATAZ, AUDITOR | ObraControllerConsultasTest, ObraServiceConsultasTest | ✔ |
| 12 | PATCH /fases/{id}/programacion | PATCH /api/obras/fases/{id}/programacion | PLANIFICADOR, SISTEMA | FaseControllerTest, FaseControllerLecturaTest, FaseServiceTest | ✔ |
| 13 | GET /internos/fases/{id}/contexto-ejecucion | GET /api/obras/internos/fases/{id}/contexto-ejecucion | SISTEMA | OperacionControllerTest, OperacionServiceTest | ✔ |
| 14 | POST /internos/fases/{id}/avances | POST /api/obras/internos/fases/{id}/avances | SISTEMA | FaseInternaControllerTest, FaseServiceAvanceTest | ✔ |
| 15 | PATCH /internos/fases/{id}/riesgo | PATCH /api/obras/internos/fases/{id}/riesgo | SISTEMA | FaseInternaControllerTest, FaseServiceAvanceTest | ✔ |

Regla de rutas: prefijo `/api/obras` más la ruta del cuestionario (el gateway no recorta el prefijo).
Extras que no están en el compromiso: iniciar una fase, listar los tramos de una obra y el cronograma de un tramo.

## 2. Funcionalidades (sección 04) que tocan a MS-Obras

| ID | Funcionalidad | Implementación | Estado |
|---|---|---|---|
| F01 | Gestionar obras, tramos y fases (alta y edición) | Endpoints 1 a 9 | ≈ no hay edición de fases; el cuestionario no tiene un endpoint para eso |
| F02 | Armar cronograma y dependencias | Endpoints 8, 11 y 12; `fase_anterior_id` | ✔ |
| F03 | Calcular materiales requeridos | Endpoint 10; tabla `consumos_material` | ✔ |
| F14 | Gestionar fase en riesgo | Endpoint 15 y resolución al reprogramar; la decisión del PLANIFICADOR y el conteo de suspensiones son de MS-Operaciones | ≈ parte de Obras lista |
| F17 | Autenticar y autorizar | `@PreAuthorize` por endpoint; 401 y 403 probados (ver `docs/matriz-permisos.md`, sección 4) | ✔ |

## 3. Reglas de negocio (sección 04)

| Regla | Qué hace en MS-Obras | Rechazo | Evidencia | Estado |
|---|---|---|---|---|
| RN-01 | Una fase no empieza si la anterior no está COMPLETADA (al iniciarla o al registrar su primer avance); `contexto-ejecucion` informa `faseAnteriorCompletada` | 409 | FaseServiceTest, FaseServiceAvanceTest, FaseControllerTest | ✔ |
| RN-02 | Reprogramar una fase corre en cascada las dependientes; rechaza ciclos y fechas anteriores al fin de la fase previa; una fase en riesgo vuelve a PENDIENTE | 422 `RN-02` | FaseServiceTest, FaseServiceAvanceTest | ✔ |
| RN-03 | Materiales = superficie del tramo × consumo por tipo de fase (BigDecimal, 2 decimales) | Superficie inválida: 400 | MaterialServiceTest, FaseServiceAvanceTest, FaseControllerLecturaTest | ≈ ver diferencias |
| RN-11 | Es una regla de la orden (MS-Operaciones). En Obras, las fases rechazan transiciones inválidas con 409 | 409 | FaseServiceTest, FaseServiceAvanceTest | ✔ |

## 4. Diferencias abiertas con el cuestionario

Hay que decidirlas en equipo, porque el cuestionario dice que los cambios de alcance se acuerdan con la cátedra.

| Tema | Cuestionario | Implementación | Qué proponemos |
|---|---|---|---|
| Ids | `uuid` en el DER | `BIGSERIAL` | Avisar a la cátedra o migrar a uuid |
| Spring Boot | 3.2 | 4.1.2 | Avisar a la cátedra (roadmap, tarea 4) |
| Material de la fase | `material_requerido` guardado en la fase | Se calcula al consultar | Dejarlo, ya figura en DECISIONES |
| Estado de la obra | columna `estado` y `fecha_inicio` | `estado` derivado de las fases; sin `fecha_inicio` | Dejarlo |
| RN-03, tipo sin rendimiento | Se rechaza | Devuelve lista vacía (RELEVAMIENTO no consume material) | Decidir si se rechaza con 422 |
| Seed de la obra | Fase 1 COMPLETADA y Fase 2 Carpeta Asfáltica | Fase RELEVAMIENTO COMPLETADA y fase BASE PENDIENTE | Ajustar el seed (tarea 11) |

## 5. Lo que depende de otros servicios para NEG-01 e INT-01

- MS-Operaciones todavía no llama a MS-Obras: no consume `contexto-ejecucion`, `avances`, `riesgo` ni `programacion`. Estado de su rama `feat/ms-operaciones` al 12/11.
- La colección de requests (API-01, tarea 12) no existe todavía. La guía `docs/ms-obras-api.md` lista las 38 requests de MS-Obras con su resultado esperado.
- El `docker compose` completo con los tres servicios (INF-01, tarea 13) es de otros.

## 6. Verificación

- 142 tests sin base de datos más `MsObrasApplicationTests` contra PostgreSQL real (aplica las migraciones V1 a V4).
- Corrida real de las 38 requests contra Keycloak y PostgreSQL (script `ms-obras/scripts/e2e-ms-obras.ps1`): 38 de 38 OK el 12/11/2026, directo al puerto 8082, sin pasar por el gateway.
