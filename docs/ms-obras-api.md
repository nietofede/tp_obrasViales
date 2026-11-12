# ms-obras: guia de la API y requests para la coleccion

Probado de punta a punta contra PostgreSQL y Keycloak reales, directo al puerto 8082.
No se probo a traves del gateway (8081): las rutas son las mismas porque el gateway no recorta el prefijo.

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

## 3. Flujo de requests (en este orden)
Variables a guardar de cada respuesta (campo `id`): `obraId`, `tramoId`, `faseA`, `faseB`.

| # | Request | Token | Cuerpo | Esperado |
| --- | --- | --- | --- | --- |
| 1 | POST /api/obras | sin token | OBRA | 401 |
| 2 | POST /api/obras | capataz | OBRA | 403 |
| 3 | POST /api/obras | planificador | OBRA | 201. Guardar `obraId` |
| 4 | POST /api/obras/{{obraId}}/tramos | planificador | TRAMO | 201. Guardar `tramoId` |
| 5 | POST /api/obras/{{obraId}}/tramos | planificador | TRAMO con latitud 91 | 400 con `errores.latitud` |
| 6 | POST /api/obras/99999/tramos | planificador | TRAMO | 404 |
| 7 | POST /api/obras/tramos/{{tramoId}}/fases | planificador | FASE_A | 201. Guardar `faseA` |
| 8 | POST /api/obras/tramos/{{tramoId}}/fases | planificador | FASE_B (con `faseAnteriorId` = `faseA`) | 201. Guardar `faseB` |
| 9 | POST /api/obras/tramos/{{tramoId}}/fases | planificador | FASE_C (otra siguiente de `faseA`) | 409 |
| 10 | POST /api/obras/fases/{{faseB}}/iniciar | capataz | (vacio) | 422 con `codigoRegla: RN-01` |
| 11 | POST /api/obras/fases/{{faseA}}/iniciar | capataz | (vacio) | 200, estado `EN_CURSO` |
| 12 | POST /api/obras/fases/{{faseA}}/reprogramar | capataz | REPROGRAMAR | 403 |
| 13 | POST /api/obras/fases/{{faseA}}/reprogramar | planificador | REPROGRAMAR | 200 (RN-02, cascada sobre B) |
| 14 | GET /api/obras/tramos/{{tramoId}}/fases | auditor | (vacio) | 200. B pasa a 2026-12-07 / 2026-12-12 |
| 15 | GET /api/obras/fases/{{faseB}}/datos-operacion | operario | (vacio) | 403 |
| 16 | GET /api/obras/fases/{{faseB}}/datos-operacion | sistema | (vacio) | 200 (RN-03: piedra triturada 250.00 m3) |
| 17 | GET /v3/api-docs | sin token | (vacio) | 200 |

Cuerpos (JSON):
- OBRA: `{"nombre":"Demo Av. Libertad","descripcion":"Prueba de punta a punta"}`
- TRAMO: `{"nombre":"Tramo norte","superficieM2":1000,"latitud":-34.6,"longitud":-58.4}`
- FASE_A: `{"tipo":"RELEVAMIENTO","fechaInicioPlanificada":"2026-12-01","fechaFinPlanificada":"2026-12-05"}`
- FASE_B: `{"tipo":"BASE","fechaInicioPlanificada":"2026-12-05","fechaFinPlanificada":"2026-12-10","faseAnteriorId":{{faseA}}}`
- FASE_C: `{"tipo":"SUBBASE","fechaInicioPlanificada":"2026-12-11","fechaFinPlanificada":"2026-12-15","faseAnteriorId":{{faseA}}}`
- REPROGRAMAR: `{"nuevaFechaInicio":"2026-12-03"}`

Para cada endpoint, los tres casos 401, 403 y 2xx estan en `docs/matriz-permisos.md`, seccion 4.

## 4. Contrato de datos-operacion (para ms-operaciones)
```json
{"faseId":4,"tipoTrabajo":"BASE","estadoFase":"PENDIENTE","faseAnteriorCompletada":false,
 "tramoId":2,"tramoNombre":"Tramo norte","superficieM2":1000.00,"latitud":-34.6,"longitud":-58.4,
 "materiales":[{"material":"Piedra triturada","cantidad":250.00,"unidad":"m3"}]}
```
`latitud` y `longitud` van por separado. OSRM espera `longitud,latitud`. Sin fase anterior, `faseAnteriorCompletada` es `true`.

## 5. Errores (RFC 7807)
| Codigo | Cuando | Detalle |
| --- | --- | --- |
| 400 | Validacion del cuerpo | `errores` con un mensaje por campo |
| 401 | Sin token | |
| 403 | Rol insuficiente | |
| 404 | La obra, el tramo o la fase no existe | |
| 409 | Estado incompatible (iniciar una fase que no esta PENDIENTE, reprogramar una COMPLETADA, o una fase anterior que ya tiene siguiente) | |
| 422 | Regla incumplida | `codigoRegla`: RN-01, RN-02, RN-03, FASE-FECHAS, FASE-TRAMO |

## 6. Casos con test automatico que no se corrieron contra el servicio real
Fin anterior al inicio (422 `FASE-FECHAS`), fase anterior de otro tramo (422 `FASE-TRAMO`), fase anterior inexistente (404), reprogramar antes de que termine la anterior (422 `RN-02`), reprogramar una fase COMPLETADA (409) y superficie menor o igual a 0 (400). Los tests estan en `FaseServiceTest` y `TramoControllerTest`.

## 7. Tests
`mvnw test "-Dtest=!MsObrasApplicationTests"` corre 61 tests sin base de datos. `MsObrasApplicationTests` arranca la aplicacion completa y necesita la base levantada y `OBRAS_DB_PASSWORD`.

## 8. Consultas de obras y tramos (se suman a las 17 requests)
Se agregaron tras la revision del PR. Son GET sin cuerpo.

| # | Request | Token | Esperado |
| --- | --- | --- | --- |
| 18 | GET /api/obras | auditor | 200, lista de obras |
| 19 | GET /api/obras/{{obraId}} | capataz | 200 |
| 20 | GET /api/obras/{{obraId}}/tramos | planificador | 200, lista de tramos |

Para los tres: sin token da 401 y con operario da 403. Una obra inexistente da 404.
