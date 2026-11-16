# Trazabilidad de ms-obras: requisito, implementación y evidencia

Sirve para ALC-01 de la rúbrica v05 («matriz de trazabilidad enunciado/compromiso → implementación → evidencia»). Alcance: lo que el equipo comprometió para MS-Obras en el cuestionario (secciones 04, 07 y 08). Es un borrador de Mateo; Hugo y Federico deberían revisarlo.

Estado: ✔ cumplido y con evidencia · ≈ cumplido con una diferencia anotada · ✘ pendiente.

## 1. Endpoints

Con el recorte aprobado por la cátedra (2026-11-16) quedan 11 de los 15 endpoints comprometidos más un extra. Se quitaron `PUT /obras/{id}`, `GET /tramos/{id}`, `PUT /tramos/{id}` y `GET /fases/{id}` (la información aparece al consultar la obra), y los extras iniciar una fase (el primer avance la inicia) y cronograma de un tramo (está en el de la obra). Ver `docs/DECISIONES.md`, "Recorte de alcance".

| # | Comprometido | Implementado | Roles | Evidencia | Estado |
|---|---|---|---|---|---|
| 1 | POST /obras | POST /api/obras | PLANIFICADOR | ObraControllerTest, ObraServiceTest | ✔ |
| 2 | GET /obras, con estado y avance | GET /api/obras | PLANIFICADOR, CAPATAZ, AUDITOR | ObraControllerConsultasTest, ObraServiceConsultasTest | ✔ |
| 3 | GET /obras/{id}, con tramos y fases | GET /api/obras/{obraId} | PLANIFICADOR, CAPATAZ, AUDITOR | ObraControllerConsultasTest, ObraServiceConsultasTest | ✔ |
| 4 | POST /obras/{obraId}/tramos | POST /api/obras/{obraId}/tramos | PLANIFICADOR | ObraControllerTest, ObraServiceTest | ✔ |
| 5 | POST /tramos/{tramoId}/fases | POST /api/obras/tramos/{tramoId}/fases | PLANIFICADOR | TramoControllerTest, FaseServiceTest | ✔ |
| 6 | GET /fases/{id}/materiales | GET /api/obras/fases/{id}/materiales | PLANIFICADOR, CAPATAZ | FaseControllerLecturaTest, MaterialServiceTest | ✔ |
| 7 | GET /obras/{obraId}/cronograma | GET /api/obras/{obraId}/cronograma | PLANIFICADOR, CAPATAZ, AUDITOR | ObraControllerConsultasTest, ObraServiceConsultasTest | ✔ |
| 8 | PATCH /fases/{id}/programacion | PATCH /api/obras/fases/{id}/programacion | PLANIFICADOR, SISTEMA | FaseControllerTest, FaseServiceTest | ✔ |
| 9 | GET /internos/fases/{id}/contexto-ejecucion | GET /api/obras/internos/fases/{id}/contexto-ejecucion | SISTEMA | OperacionControllerTest, OperacionServiceTest | ✔ |
| 10 | POST /internos/fases/{id}/avances | POST /api/obras/internos/fases/{id}/avances | SISTEMA | FaseInternaControllerTest, FaseServiceAvanceTest | ✔ |
| 11 | PATCH /internos/fases/{id}/riesgo | PATCH /api/obras/internos/fases/{id}/riesgo | SISTEMA | FaseInternaControllerTest, FaseServiceAvanceTest | ✔ |
| extra | — | GET /api/obras/{obraId}/tramos | PLANIFICADOR, CAPATAZ, AUDITOR | ObraControllerConsultasTest | ✔ (lo usa Operaciones para filtrar órdenes por obra) |

Regla de rutas: prefijo `/api/obras` más la ruta del cuestionario (el gateway no recorta el prefijo).

## 2. Funcionalidades (sección 04) que tocan a MS-Obras

| ID | Funcionalidad | Implementación | Estado |
|---|---|---|---|
| F01 | Gestionar obras, tramos y fases | Endpoints 1 a 5 | ≈ alta y consulta; la edición se quitó con el recorte |
| F02 | Armar cronograma y dependencias | Endpoints 5, 7 y 8; `fase_anterior_id` | ✔ |
| F03 | Calcular materiales requeridos | Endpoint 6; tabla `consumos_material` | ✔ |
| F14 | Fase en riesgo | Endpoint 11 (Operaciones la marca a la tercera suspensión) y endpoint 8 (el PLANIFICADOR la reprograma y vuelve a PENDIENTE) | ✔ (RN-12 simplificada) |
| F17 | Autenticar y autorizar | `@PreAuthorize` por endpoint y reglas por método en el gateway; 401 y 403 probados (`docs/matriz-permisos.md`) | ✔ |

## 3. Reglas de negocio (sección 04)

| Regla | Qué hace en MS-Obras | Rechazo | Evidencia | Estado |
|---|---|---|---|---|
| RN-01 | Una fase no empieza si la anterior no está COMPLETADA (al registrar su primer avance); `contexto-ejecucion` informa `faseAnteriorCompletada` | 409 | FaseServiceTest, FaseServiceAvanceTest, FaseControllerTest | ✔ |
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

## 5. Integración con los otros servicios

- MS-Operaciones consume `contexto-ejecucion` (paso 1 del proceso central), `avances` (el CAPATAZ registra avance; al 100 % la fase y la orden quedan COMPLETADA), `riesgo` (RN-12) y `programacion` (saga de suspensión, RN-02). Clientes: `ObrasHttpClient` en ms-operaciones.
- La colección Postman (`coleccion/`) cubre los endpoints de Obras en las carpetas de seguridad, flujo principal y reglas.

## 6. Verificación

- Tests de ms-obras (106 con `MsObrasApplicationTests`, que aplica las migraciones V1 a V5 contra PostgreSQL real): verdes el 2026-11-16.
- Prueba de punta a punta del servicio: `ms-obras/scripts/e2e-ms-obras.ps1` (ver `docs/ms-obras-api.md`).
