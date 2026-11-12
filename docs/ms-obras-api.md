# ms-obras: guia de la API y requests para la coleccion

Las rutas siguen el cuestionario de compromiso (seccion 07) con el prefijo `/api/obras`, porque el gateway (8081) no recorta el prefijo.
Todos los endpoints tienen tests automaticos, y las 38 requests de la seccion 4 se corrieron contra Keycloak y PostgreSQL reales: 38 de 38 OK el 12/11/2026 (ver seccion 7).

## 1. Como levantarlo
1. Docker Desktop abierto. Desde la raiz: `docker compose down -v` y `docker compose up -d`.
2. Una sola vez, instalar el pom padre y commons en `.m2`:
   `mvnw -f pom.xml -N install` y `mvnw -f obras-commons/pom.xml install -DskipTests` (`mvnw.cmd` en Windows).
3. Definir la variable `OBRAS_DB_PASSWORD` (el valor esta en `.env`) y correr `mvnw spring-boot:run` dentro de `ms-obras`.
4. URL base: `http://localhost:8082` (directo) o `http://localhost:8081` (gateway).

## 2. Tokens (Keycloak)
Todas las requests van con el header `Authorization: Bearer <access_token>`, salvo las que dicen "sin token".

| Para | URL | Campos (form-urlencoded) |
| --- | --- | --- |
| Personas | `POST http://localhost:8080/realms/obras-viales/protocol/openid-connect/token` | `grant_type=password`, `client_id=obras-public-client`, `username=<rol en minuscula>`, `password=<rol>123` |
| SISTEMA | misma URL | `grant_type=client_credentials`, `client_id=servicio-sistema`, `client_secret=` valor de `SERVICIO_SISTEMA_SECRET` del `.env` |

Usuarios de demo: `admin`, `planificador`, `capataz`, `operario`, `auditor`. El token esta en el campo `access_token` de la respuesta.

## 3. Endpoints de ms-obras (15 comprometidos y 3 extras)

| Endpoint | Roles | Cuestionario |
| --- | --- | --- |
| POST /api/obras | PLANIFICADOR | POST /obras |
| GET /api/obras | PLANIFICADOR, CAPATAZ, AUDITOR | GET /obras |
| GET /api/obras/{obraId} | PLANIFICADOR, CAPATAZ, AUDITOR | GET /obras/{id} |
| PUT /api/obras/{obraId} | PLANIFICADOR | PUT /obras/{id} |
| POST /api/obras/{obraId}/tramos | PLANIFICADOR | POST /obras/{obraId}/tramos |
| GET /api/obras/tramos/{tramoId} | PLANIFICADOR, CAPATAZ, AUDITOR | GET /tramos/{id} |
| PUT /api/obras/tramos/{tramoId} | PLANIFICADOR | PUT /tramos/{id} |
| POST /api/obras/tramos/{tramoId}/fases | PLANIFICADOR | POST /tramos/{tramoId}/fases |
| GET /api/obras/fases/{id} | PLANIFICADOR, CAPATAZ, AUDITOR | GET /fases/{id} |
| GET /api/obras/fases/{id}/materiales | PLANIFICADOR, CAPATAZ | GET /fases/{id}/materiales |
| GET /api/obras/{obraId}/cronograma | PLANIFICADOR, CAPATAZ, AUDITOR | GET /obras/{obraId}/cronograma |
| PATCH /api/obras/fases/{id}/programacion | PLANIFICADOR, SISTEMA | PATCH /fases/{id}/programacion |
| GET /api/obras/internos/fases/{id}/contexto-ejecucion | SISTEMA | GET /internos/fases/{id}/contexto-ejecucion |
| POST /api/obras/internos/fases/{id}/avances | SISTEMA | POST /internos/fases/{id}/avances |
| PATCH /api/obras/internos/fases/{id}/riesgo | SISTEMA | PATCH /internos/fases/{id}/riesgo |
| POST /api/obras/fases/{id}/iniciar | CAPATAZ, SISTEMA | extra |
| GET /api/obras/{obraId}/tramos | PLANIFICADOR, CAPATAZ, AUDITOR | extra |
| GET /api/obras/tramos/{tramoId}/fases | PLANIFICADOR, CAPATAZ, AUDITOR | extra |

## 4. Flujo de requests (en este orden)
Variables a guardar de cada respuesta (campo `id`): `obraId`, `tramoId`, `faseA`, `faseB`.

| # | Request | Token | Cuerpo | Esperado |
| --- | --- | --- | --- | --- |
| 1 | POST /api/obras | sin token | OBRA | 401 |
| 2 | POST /api/obras | capataz | OBRA | 403 |
| 3 | POST /api/obras | planificador | OBRA | 201. Guardar `obraId` |
| 4 | PUT /api/obras/{{obraId}} | capataz | OBRA_EDITADA | 403 |
| 5 | PUT /api/obras/{{obraId}} | planificador | OBRA_EDITADA | 200, con el nombre editado |
| 6 | POST /api/obras/{{obraId}}/tramos | planificador | TRAMO | 201. Guardar `tramoId` |
| 7 | POST /api/obras/{{obraId}}/tramos | planificador | TRAMO con latitud 91 | 400 con `errores.latitud` |
| 8 | POST /api/obras/99999/tramos | planificador | TRAMO | 404 |
| 9 | PUT /api/obras/tramos/{{tramoId}} | planificador | TRAMO_EDITADO | 200, superficie 2000 |
| 10 | GET /api/obras/tramos/{{tramoId}} | auditor | (vacio) | 200, superficie 2000 |
| 11 | POST /api/obras/tramos/{{tramoId}}/fases | planificador | FASE_A | 201. Guardar `faseA` |
| 12 | POST /api/obras/tramos/{{tramoId}}/fases | planificador | FASE_B (con `faseAnteriorId` = `faseA`) | 201. Guardar `faseB` |
| 13 | POST /api/obras/tramos/{{tramoId}}/fases | planificador | FASE_C (otra siguiente de `faseA`) | 409 |
| 14 | GET /api/obras/fases/{{faseB}} | auditor | (vacio) | 200, estado `PENDIENTE`, avance 0 |
| 15 | GET /api/obras/fases/{{faseB}}/materiales | auditor | (vacio) | 403 |
| 16 | GET /api/obras/fases/{{faseB}}/materiales | capataz | (vacio) | 200 (RN-03: piedra triturada 500.00 m3, con la superficie editada de 2000) |
| 17 | POST /api/obras/fases/{{faseB}}/iniciar | capataz | (vacio) | 409 (RN-01: la fase anterior no esta completada) |
| 18 | POST /api/obras/internos/fases/{{faseB}}/avances | sistema | AVANCE 30 | 409 (RN-01) |
| 19 | POST /api/obras/internos/fases/{{faseA}}/avances | planificador | AVANCE 50 | 403 |
| 20 | POST /api/obras/internos/fases/{{faseA}}/avances | sistema | AVANCE 0 | 400 con `errores.porcentaje` |
| 21 | POST /api/obras/internos/fases/{{faseA}}/avances | sistema | AVANCE 50 | 201, estado `EN_CURSO`, avance 50 |
| 22 | POST /api/obras/internos/fases/{{faseA}}/avances | sistema | AVANCE 20 | 422 con `codigoRegla: AVANCE` (no puede retroceder) |
| 23 | POST /api/obras/internos/fases/{{faseA}}/avances | sistema | AVANCE 100 | 201, estado `COMPLETADA` |
| 24 | POST /api/obras/internos/fases/{{faseA}}/avances | sistema | AVANCE 100 | 409 (ya esta completada) |
| 25 | POST /api/obras/internos/fases/{{faseB}}/avances | sistema | AVANCE 40 | 201, estado `EN_CURSO` (la anterior ya esta completa) |
| 26 | PATCH /api/obras/internos/fases/{{faseB}}/riesgo | capataz | (vacio) | 403 |
| 27 | PATCH /api/obras/internos/fases/{{faseB}}/riesgo | sistema | (vacio) | 200, estado `EN_RIESGO` |
| 28 | POST /api/obras/internos/fases/{{faseB}}/avances | sistema | AVANCE 50 | 409 (esta en riesgo) |
| 29 | PATCH /api/obras/fases/{{faseB}}/programacion | capataz | REPROGRAMAR | 403 |
| 30 | PATCH /api/obras/fases/{{faseB}}/programacion | planificador | REPROGRAMAR | 200 (RN-02), vuelve a `PENDIENTE`, fechas 2026-12-12 y 2026-12-17 |
| 31 | GET /api/obras/{{obraId}}/cronograma | auditor | (vacio) | 200: A `COMPLETADA`, B `PENDIENTE` |
| 32 | GET /api/obras/{{obraId}} | capataz | (vacio) | 200 con tramos y fases, estado `EN_EJECUCION`, avance 70 |
| 33 | GET /api/obras | auditor | (vacio) | 200, lista con estado y avance |
| 34 | GET /api/obras/{{obraId}}/tramos | planificador | (vacio) | 200, lista de tramos |
| 35 | GET /api/obras/internos/fases/{{faseB}}/contexto-ejecucion | operario | (vacio) | 403 |
| 36 | GET /api/obras/internos/fases/{{faseB}}/contexto-ejecucion | planificador | (vacio) | 403 (solo SISTEMA) |
| 37 | GET /api/obras/internos/fases/{{faseB}}/contexto-ejecucion | sistema | (vacio) | 200, `faseAnteriorCompletada: true` |
| 38 | GET /v3/api-docs | sin token | (vacio) | 200 |

Cuerpos (JSON):
- OBRA: `{"nombre":"Demo Av. Libertad","descripcion":"Prueba de punta a punta"}`
- OBRA_EDITADA: `{"nombre":"Demo Av. Libertad (editada)","descripcion":"Descripcion corregida"}`
- TRAMO: `{"nombre":"Tramo norte","superficieM2":1000,"latitud":-34.6,"longitud":-58.4}`
- TRAMO_EDITADO: `{"nombre":"Tramo norte","superficieM2":2000,"latitud":-34.6,"longitud":-58.4}`
- FASE_A: `{"tipo":"RELEVAMIENTO","fechaInicioPlanificada":"2026-12-01","fechaFinPlanificada":"2026-12-05"}`
- FASE_B: `{"tipo":"BASE","fechaInicioPlanificada":"2026-12-05","fechaFinPlanificada":"2026-12-10","faseAnteriorId":{{faseA}}}`
- FASE_C: `{"tipo":"SUBBASE","fechaInicioPlanificada":"2026-12-11","fechaFinPlanificada":"2026-12-15","faseAnteriorId":{{faseA}}}`
- AVANCE n: `{"porcentaje":n}`
- REPROGRAMAR: `{"nuevaFechaInicio":"2026-12-12"}`

Para cada endpoint, los tres casos 401, 403 y 2xx estan en `docs/matriz-permisos.md`, seccion 4.

## 5. Contratos de respuesta

contexto-ejecucion (para ms-operaciones):
```json
{"faseId":4,"tipoTrabajo":"BASE","estadoFase":"PENDIENTE","faseAnteriorCompletada":true,
 "tramoId":2,"tramoNombre":"Tramo norte","superficieM2":2000.00,"latitud":-34.6,"longitud":-58.4,
 "materiales":[{"material":"Piedra triturada","cantidad":500.00,"unidad":"m3"}]}
```
`latitud` y `longitud` van por separado. OSRM espera `longitud,latitud`. Sin fase anterior, `faseAnteriorCompletada` es `true`.

Fase (la devuelven avances, riesgo, programacion, iniciar y los GET de fase):
```json
{"id":4,"tramoId":2,"tipo":"BASE","estado":"EN_CURSO","fechaInicioPlanificada":"2026-12-05",
 "fechaFinPlanificada":"2026-12-10","faseAnteriorId":3,"avance":40}
```

Obra en la lista: `{"id","nombre","descripcion","estado","avance"}`. En el detalle suma `tramos`, una lista de `{"tramo":{...},"fases":[...]}`.
`estado` de la obra: `PLANIFICADA`, `EN_EJECUCION` o `COMPLETADA`. `avance` es el promedio del avance de sus fases.

## 6. Errores (RFC 7807)
| Codigo | Cuando | Detalle |
| --- | --- | --- |
| 400 | Validacion del cuerpo | `errores` con un mensaje por campo |
| 401 | Sin token | |
| 403 | Rol insuficiente | |
| 404 | La obra, el tramo o la fase no existe | |
| 409 | Estado incompatible: RN-01 (la fase anterior no esta completada), iniciar una fase que no esta PENDIENTE, avanzar o reprogramar una COMPLETADA, avanzar una EN_RIESGO, o una fase anterior que ya tiene siguiente | |
| 422 | Regla incumplida | `codigoRegla`: RN-02, RN-03, AVANCE, FASE-FECHAS, FASE-TRAMO |

## 7. Verificacion
- `mvnw test "-Dtest=!MsObrasApplicationTests"` corre 142 tests sin base de datos. `MsObrasApplicationTests` arranca la aplicacion completa contra PostgreSQL (aplica la migracion V4) y necesita la base levantada y `OBRAS_DB_PASSWORD`.
- Corrida real: `ms-obras/scripts/e2e-ms-obras.ps1` ejecuta las 38 requests de la seccion 4 contra Keycloak y PostgreSQL reales, directo al puerto 8082 (sin pasar por el gateway). Resultado del 12/11/2026: 38 de 38 OK. Con `docker compose up -d` y ms-obras corriendo, desde la raiz: `Get-Content -Raw .\ms-obras\scripts\e2e-ms-obras.ps1 | Invoke-Expression`.
- Casos con test automatico y sin corrida real: fin anterior al inicio (422 `FASE-FECHAS`), fase anterior de otro tramo (422 `FASE-TRAMO`), fase anterior inexistente (404), reprogramar antes de que termine la anterior (422 `RN-02`), reprogramar una fase COMPLETADA (409) y superficie menor o igual a 0 (400).
