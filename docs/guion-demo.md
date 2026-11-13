# Guion de demo y defensa

Para ensayar con reloj. Los tiempos son una estimación de partida: ajustarlos después del primer ensayo. Los requests están en la colección de Postman (`coleccion/`); las carpetas se nombran como allí (`00-Auth` a `05-Fallas-y-Resiliencia`).

> Estado: este guion se escribió con las Ramas 1 y 2 ya mergeadas en esta rama, y los pasos se comprobaron uno por uno sobre el stack. No se ensayó con reloj ni con el equipo.

## 1. Antes de la demo (checklist)

- Docker Desktop abierto, `.env` copiado de `.env.example`, nada ocupando los puertos 8080 y 8081.
- Postman con la colección y el entorno «Obras Viales - local» importados.
- Hora de la máquina correcta: las órdenes del seed se calculan con la fecha de hoy en Argentina.
- Una ventana con `docker compose logs -f ms-operaciones` lista para mostrar los logs.
- Plan B si falla internet (Open-Meteo y OSRM son externos): saltar al escenario 2, que justamente muestra qué pasa sin Open-Meteo, y mostrar la reserva con `fuenteRuta: ESTIMADA`.

## 2. Orden de la demo

| # | Qué se muestra | Tiempo | Quién |
|---|---|---|---|
| 1 | Arranque desde cero | 4 min | Integrante A |
| 2 | Arquitectura en una pantalla (C2 y C1) | 3 min | Integrante A |
| 3 | Seguridad: 401 y 403 (escenario 4) | 3 min | Integrante C |
| 4 | Camino feliz: habilitación, ejecución y cierre | 6 min | Integrante C |
| 5 | Escenario 1: suspensión por clima y replanificación | 4 min | Integrante C |
| 6 | Escenario 2: Open-Meteo caído | 3 min | Integrante C |
| 7 | Escenario 3: rechazo de recursos por distancia | 3 min | Integrante B |
| 8 | Escenario 5: vencimiento de reservas por TTL | 3 min | Integrante B |
| 9 | Trazabilidad: `correlationId` en los logs | 2 min | Integrante A |
| 10 | Cierre: matrices y lo que queda fuera de alcance | 2 min | Todos |

Total estimado: unos 33 minutos. Si la cátedra da menos, recortar 2, 9 y 10.

## 3. Paso a paso

### 3.1 Arranque desde cero (4 min)

```powershell
docker compose down -v
docker compose up -d --build
docker compose ps
```

Esperado: seis contenedores en `(healthy)` (Postgres, Keycloak, gateway y los tres servicios). Decir mientras se construye: solo el gateway (8081) y Keycloak (8080) se publican; los servicios viven dentro de la red de Docker.

Comprobar el aislamiento:

```powershell
curl.exe -s -m 3 http://localhost:8082/actuator/health
```

Esperado: no responde (Obras no está publicado).

### 3.2 Tokens y datos base (2 min)

En Postman, correr `00-Auth` entero. Esperado: seis requests con 200 y las variables `tokenAdmin`, `tokenPlanificador`, `tokenCapataz`, `tokenOperario`, `tokenAuditor` y `tokenSistema` completas. Los tokens duran 5 minutos: si algo da 401 de golpe, repetir `00-Auth`.

Mostrar los datos sembrados: `GET /api/obras` con el PLANIFICADOR devuelve «Ruta Provincial 34».

### 3.3 Seguridad (escenario 4, 3 min)

Correr `02-Seguridad`. Los tres casos que explican todo:

| Request | Esperado |
|---|---|
| Sin token → `GET /api/obras` | 401 |
| OPERARIO crea obra | 403 |
| PLANIFICADOR crea obra / lee obras | 200 o 201 |

Decir: el gateway valida firma y vigencia, y cada servicio vuelve a validar el token y controla el rol con `@PreAuthorize`. Los endpoints `/internos/` exigen el rol SISTEMA.

### 3.4 Camino feliz (6 min)

1. `03-Flujo-Principal > Habilitación diaria (hoy)` con el token SISTEMA. Esperado: la orden 1 (fase 2, tramo 1) queda `HABILITADA`.
2. `Ver orden 1` y `Evaluaciones climáticas de la orden 1`: se ven los valores de humedad, lluvia y temperatura, y los umbrales cumplidos.
3. CAPATAZ: `inicio` → `EN_EJECUCION`; `avances` 60 y 100; `cierre` con horas de máquina y materiales → `COMPLETADA`.
4. `GET /api/obras/1/cronograma`: la fase 2 figura `COMPLETADA`.

Si el clima real de ese día no cumple los umbrales, la orden se suspende en vez de habilitarse. En ese caso, correr antes `04-Reglas-Negocio > RN-09 > ADMIN restaura umbrales` o subir los valores: es parte de la demo y se explica con el escenario 1.

### 3.5 Escenario 1: suspensión por clima y replanificación (4 min)

Condición: una orden PROGRAMADA y umbrales por encima de lo que el clima real cumple. Está en `03-Flujo-Principal > Escenarios 1 y 3`; todo corre antes de iniciar la orden 1.

1. ADMIN: `ADMIN baja los umbrales de BASE` (humedad 0, lluvia 0, temperatura mínima 100).
2. SISTEMA: `Habilitación de mañana`. Esperado: la orden 2 queda `REPROGRAMADA`: Recursos libera la reserva, Obras corre la fase 2 y se crea una orden nueva (la 4) con el contador de suspensiones en 0.
3. `GET /api/operaciones/suspensiones`: una suspensión de motivo `CLIMA`, con el detalle de lo medido contra los umbrales. `GET /api/obras/1/cronograma` muestra la fase 2 con la fecha corrida.
4. ADMIN: `ADMIN restaura los umbrales de BASE` (humedad 0.4, lluvia 20, temperatura 5).

Para mostrar RN-12: con `ADMIN fija límite de suspensiones = 1` (carpeta RN-12), la primera suspensión deja la fase en riesgo y aparece en `GET /fases-en-riesgo`. Restaurar el límite a 3 después.

### 3.6 Escenario 2: Open-Meteo no disponible (3 min)

Condición: la API de clima no responde y no hay caché reciente.

1. En `docker-compose.yml`, bajo `ms-operaciones` > `environment`, agregar `APP_OPEN_METEO_URL: http://clima-inexistente.invalid/v1/forecast` y correr `docker compose up -d ms-operaciones`.
2. Habilitar una orden `PROGRAMADA`.
3. Esperado (verificado): tarda unos 7 segundos por los tres reintentos y responde 200 con la orden en `PENDIENTE_CONFIRMACION_CLIMATICA`, con su `reservaId` conservada. El CAPATAZ decide con `PATCH /ordenes/{id}/resolucion-climatica` (`HABILITAR` o `SUSPENDER`).
4. Restaurar: quitar la variable y repetir `docker compose up -d ms-operaciones`.

La Rama 2 agregó una caché en memoria del último pronóstico (15 minutos por defecto, `app.open-meteo.cache-ttl`): si Open-Meteo falla y hay un pronóstico reciente de esas coordenadas, la orden se habilita igual. Al reiniciar `ms-operaciones` con la URL inválida la caché queda vacía, que es justo lo que muestra este escenario (verificado en un stack limpio: unos 5 segundos y `PENDIENTE_CONFIRMACION_CLIMATICA`).

### 3.7 Escenario 3: rechazo de recursos por distancia (3 min)

Condición: un tramo cuya distancia supera la jornada o el viaje máximo.

- La orden 3 del seed (fase 3, tramo 2 en Río Cuarto): `Habilitación de pasado mañana` en `03-Flujo-Principal > Escenarios 1 y 3`. Esperado: 200 con la orden 3 todavía `PROGRAMADA` y el motivo de Recursos: la cuadrilla Alfa está a unos 215 minutos y el máximo es 90 (RN-07). Verificado.
- Además, directo contra Recursos: `04-Reglas-Negocio > RN-07 > Tramo lejano (Buenos Aires)` (422).

Para mostrar OSRM caído: agregar `APP_OSRM_URL: http://osrm-inexistente.invalid` bajo `ms-recursos` > `environment`, `docker compose up -d ms-recursos` y repetir una reserva; devuelve 201 con `fuenteRuta: ESTIMADA`.

Para RN-04 (cuadrilla con un operario sin certificación vigente: 422) y RN-08 (mezcla fuera de la ventana de transporte: 422), usar `04-Reglas-Negocio > RN-04` y `RN-08`; cada una explica el resultado esperado.

### 3.8 Escenario 5: vencimiento de reservas (3 min)

Condición: reserva provisoria emitida y el orquestador cae antes de confirmar.

1. `POST /api/recursos/internos/reservas` con SISTEMA (request `RN-05 > Reserva 1`).
2. Detener Operaciones: `docker compose stop ms-operaciones`.
3. El TTL es de 30 minutos y está fijo en el código (`ReservaService`): no hay variable para acortarlo. Para la demo, o se espera, o se adelanta `fecha_vencimiento_ttl` de la reserva en la base de Recursos con `psql`.
4. Esperado: el job de Recursos pasa la reserva a `EXPIRADA` y la cuadrilla queda libre.
5. `docker compose start ms-operaciones`.

El job de Recursos (`ExpiracionReservasJob`) corre cada 60 segundos y pasa a EXPIRADA las reservas vencidas. Verificado: tras adelantar el vencimiento en la base, la reserva pasa de PROVISORIA a EXPIRADA en menos de un minuto. La orden de los pasos es: crear la reserva, adelantar `fecha_vencimiento_ttl` con el `psql` del paso 3, esperar hasta un minuto y consultar `GET /api/recursos/reservas/{id}`.

### 3.9 Trazabilidad con correlationId (2 min)

Hacer un pedido por el gateway y mostrar su id en los logs:

```powershell
curl.exe -s -D - -o NUL -X POST http://localhost:8081/api/operaciones/habilitaciones-diarias -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{"fecha":"<fecha de la orden 3>"}'
docker compose logs ms-operaciones | Select-String "<id del encabezado X-Correlation-Id>"
```

El gateway genera el `X-Correlation-Id` (viene en la respuesta; ignora el que mande el cliente) y lo reenvía; el patrón de log de los cuatro servicios lo muestra entre corchetes. Verificado: el id aparece en las líneas que emite cada servicio, por ejemplo el rechazo de la orden 3 en `ms-operaciones`. Obras y Recursos no escriben líneas por cada pedido, así que en un pedido normal el id no se ve en todos los logs a la vez; para eso hace falta un pedido que genere un log en cada uno.

### 3.10 Cierre (2 min)

Mostrar `docs/matriz-requisitos.md` y `docs/trazabilidad-reglas.md`, y nombrar lo que queda fuera de alcance: mantenimiento preventivo, reasignación de cuadrillas, notificaciones, GPS y reportes complejos.

## 4. Quién explica qué

Cada integrante tiene que poder explicar **un servicio en profundidad y el flujo completo en general**. Asignación sugerida (ajustar):

| Integrante | Servicio | Qué tiene que saber contar |
|---|---|---|
| A | `ms-obras` y el gateway | Modelo obra, tramo, fase; RN-01, RN-02, RN-03; rutas del gateway; correlationId |
| B | `ms-recursos` | RN-04 a RN-08; OSRM, caché, circuit breaker y fallback `ESTIMADA`; reservas con TTL |
| C | `ms-operaciones` | Máquina de estados (RN-11); habilitación diaria; saga de suspensión; RN-09, RN-10, RN-12; clima |

Y los tres: el flujo de punta a punta (C1, C2 y la secuencia de `docs/arquitectura.md`), la seguridad (Keycloak, gateway y `@PreAuthorize`) y cómo se levanta todo.

## 5. Preguntas probables de defensa

**¿Por qué tres servicios y no uno?**
Cada servicio tiene su base y sus reglas, y cambia por razones distintas: la planificación de obras, los recursos y su disponibilidad, y la ejecución diaria sujeta al clima. Las bases no comparten claves foráneas: se refieren a ids ajenos y los validan por HTTP. El costo es la coordinación, y por eso existe la saga.

**¿Qué hay en `obras-commons` y qué no?**
Lo transversal y técnico: conversor de roles de Keycloak, constantes de roles, formato estándar de error, filtro de correlation-id, cliente HTTP con token de servicio y el value object `Coordenada`. No hay entidades ni DTOs de contrato: compartirlos acoplaría los servicios y obligaría a redesplegar todo ante un cambio de contrato.

**¿Qué es la zona de confianza (trusted zone)?**
La red privada de Docker. Solo el gateway y Keycloak se publican. Aun así, cada servicio vuelve a validar el JWT y controla el rol, así que no se depende de que el gateway sea la única barrera.

**¿Qué pasa si cae un servicio?**
- Recursos caído o rechazando: la orden queda como estaba y el error se informa; la reserva es idempotente por orden, así que reintentar es seguro.
- Obras caído: la orden no cambia de estado.
- Open-Meteo caído: `PENDIENTE_CONFIRMACION_CLIMATICA`, con la reserva conservada, y decide el CAPATAZ.
- OSRM caído: se estima en línea recta con un factor de desvío y la reserva queda con `fuenteRuta: ESTIMADA`.
- Operaciones cae a mitad de una saga: las reservas provisorias vencen por TTL y un job de Recursos las pasa a EXPIRADA.

**¿Cómo se evita que una saga a medias deje recursos bloqueados?**
La reserva es provisoria con vencimiento (30 minutos): si no se confirma, expira. Si Recursos no responde al liberar, la suspensión sigue y la reserva vence sola.

**¿Por qué BIGSERIAL y no uuid?**
El cuestionario planteaba uuid; en la implementación se eligió un entero autoincrementado por simplicidad y porque los ids no cruzan bases como claves foráneas. Está anotado en `docs/arquitectura.md`.

**¿Por qué RN-11 devuelve 422 y no 409?**
Una transición inválida es una regla de negocio incumplida (`ReglaNegocioException` con código `RN-11`), que el handler global traduce a 422. El 409 se usa para conflictos de estado de datos, como RN-01. Es una diferencia con el cuestionario, listada en `docs/matriz-requisitos.md`.

**¿Cómo se prueba que la seguridad funciona?**
Con los tres casos de la colección: sin token (401), rol insuficiente (403) y rol correcto (200 o 201), y con los tests `SeguridadGatewayTests`, `SeguridadEndpointsTest` y `ReservaControllerSeguridadTest`.

**¿Qué límites tiene OSRM público y cómo los manejan?**
Alrededor de un pedido por segundo y sin garantías. El cliente limita el ritmo, cachea, usa un User-Agent propio y tiene un circuito de corte con fallback `ESTIMADA`. Atribución a OpenStreetMap (ODbL) en el README.

**¿Qué queda fuera y por qué?**
Mantenimiento preventivo, reasignación automática de cuadrillas, notificaciones, GPS en tiempo real y reportes complejos: se acotó el alcance al proceso de habilitación diaria, que es el núcleo del compromiso.

## 6. Ensayo con reloj

- [ ] Primer ensayo completo desde cero (con `docker compose down -v`) y cronómetro: anotar el tiempo real de cada bloque.
- [ ] Segundo ensayo cambiando de presentador entre bloques.
- [ ] Ensayo sin internet (plan B de la sección 1).
- [ ] Revisar que los tokens no venzan en medio de un bloque (duran 5 minutos).
