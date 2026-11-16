# Visión general de GOVP

GOVP (Gestor de Obras Viales y Pavimentación) es el backend del TPI 2026 de Backend de Aplicaciones (UTN FRC, 3K3). Este documento explica **qué hace el programa y cómo está organizado**, sin bajar al código. Para el detalle de cada archivo y cada método está [`referencia-del-codigo.md`](referencia-del-codigo.md); para preparar la demo y la defensa (diagramas, reglas en detalle, guion, preguntas), [`guia-del-proyecto.md`](guia-del-proyecto.md).

---

## 1. Qué problema resuelve

Una repartición vial planifica obras de pavimentación. Cada obra se divide en **tramos** (un pedazo de calle o ruta con su superficie y sus coordenadas) y cada tramo en **fases** que van en orden: relevamiento, base, sub-base, carpeta asfáltica o losa de hormigón. Una fase no puede empezar si la anterior no terminó.

Lo difícil es el día a día. Para cada **orden de trabajo** (hacer una fase en una fecha) hay que decidir si se puede ejecutar, y eso depende de cuatro cosas:

1. **Que la obra esté lista**: la fase anterior tiene que estar completa.
2. **Que haya con qué**: una cuadrilla libre ese día, con la gente y las máquinas que pide ese tipo de trabajo, que llegue al tramo a tiempo; y, si el trabajo lleva mezcla asfáltica, una planta lo bastante cerca para que la mezcla no se enfríe en el camino.
3. **Que el clima acompañe**: sin lluvia, con el suelo no demasiado húmedo y con temperatura suficiente, según el tipo de trabajo.
4. **Qué hacer si no se puede**: suspender, liberar los recursos, correr las fechas de la obra y reprogramar.

GOVP automatiza esa decisión y deja registro de todo.

## 2. Quién lo usa

| Rol | Qué hace |
|---|---|
| PLANIFICADOR | Da de alta obras, tramos y fases; arma el cronograma; crea órdenes y dispara la habilitación del día; reprograma fases (también cuando una queda en riesgo) |
| CAPATAZ | Inicia o rechaza la orden habilitada, registra el avance, resuelve el clima cuando no hay datos confiables y registra la ubicación de su cuadrilla (check-in) |
| ADMIN | Configura los umbrales climáticos de cada tipo de trabajo |
| AUDITOR | Consulta todo, sin modificar nada |
| SISTEMA | Cuenta de servicio, sin persona detrás: la usan el job diario y los servicios para hablar entre sí |

Los usuarios y los roles viven en **Keycloak**, el servidor de identidad. Cada pedido lleva un token firmado (JWT) que dice quién es y qué roles tiene.

## 3. Cómo está armado

```
Cliente (Postman, navegador)
        │  HTTP con token JWT
        ▼
   API Gateway :8081 ──────────────► Keycloak :8080 (usuarios, roles, tokens)
        │  red interna de Docker
        ├──► ms-obras        :8082   obras, tramos, fases, materiales
        ├──► ms-recursos     :8083   cuadrillas, operarios, máquinas, reservas ──► OSRM (rutas)
        └──► ms-operaciones  :8084   órdenes, clima, suspensiones          ──► Open-Meteo (clima)
                     │
              PostgreSQL :5432 (una base por servicio)
```

- **API Gateway**: la única puerta de entrada. Valida el token, aplica reglas gruesas por ruta y verbo (por ejemplo, solo el PLANIFICADOR puede crear obras), genera un identificador por pedido (correlation-id) y reenvía al servicio que corresponde.
- **Tres microservicios**, cada uno con su base de datos propia. Ninguno lee las tablas de otro: se piden la información por HTTP.
- **obras-commons**: una librería que usan los tres servicios con lo técnico que comparten (seguridad, formato de errores, cliente HTTP con token, correlation-id). No tiene reglas de negocio.
- **Keycloak** y **PostgreSQL** completan la instalación. Todo se levanta con `docker compose up -d --build`.

Solo el gateway y Keycloak se publican fuera de Docker. Los servicios viven en una red interna (la "zona de confianza"): desde afuera no se puede llegar a ellos sin pasar por el gateway. Aun así, cada servicio vuelve a validar el token y controla el rol: es **defensa en profundidad**.

## 4. Los tres microservicios

### 4.1 ms-obras: qué hay que construir y cuándo

- **Guarda**: obras, tramos (superficie y coordenadas), fases (tipo, fechas planificadas, dependencia de la anterior, estado y porcentaje de avance) y el consumo de materiales por m² de cada tipo de fase.
- **Hace**: altas de obras, tramos y fases; consulta de la obra completa y su cronograma; cálculo de materiales; reprogramación de una fase con corrimiento en cascada de las que dependen de ella; registro del avance real.
- **Reglas**: RN-01 (una fase no empieza sin la anterior completa), RN-02 (reprogramación en cascada) y RN-03 (materiales según la superficie).
- **Le hablan**: el usuario por el gateway, y Operaciones con la cuenta SISTEMA (para pedir el contexto de una fase, registrar avances, reprogramar y marcar riesgo).
- 12 endpoints.

### 4.2 ms-recursos: con qué se construye

- **Guarda**: cuadrillas (con la última ubicación informada por el capataz), operarios, máquinas, plantas de mezcla, la composición mínima de cada tipo de trabajo, los límites de viaje y las **reservas**.
- **Hace**: el **motor de elegibilidad** ("proponer y reservar"). Dada una orden, recorre las cuadrillas y elige la primera que sirve, o rechaza explicando por qué no sirvió ninguna. Si el trabajo lleva mezcla, elige también la planta y calcula a qué hora tiene que salir el camión. Deja una reserva **provisoria** que vence en 30 minutos si nadie la confirma.
- **Reglas**: RN-05 (una cuadrilla no puede estar en dos lugares el mismo día), RN-06 (composición mínima según el tipo de trabajo), RN-07 (el viaje al tramo no supera el máximo y entra en la jornada) y RN-08 (la mezcla llega a tiempo y se calcula la salida del camión).
- **Habla con**: **OSRM**, un servicio público que calcula el tiempo de viaje por calle. Si OSRM no responde, estima en línea recta y lo marca como estimado.
- Los recursos vienen precargados (no hay altas ni ediciones por la API). 7 endpoints.

### 4.3 ms-operaciones: qué se hace hoy

- **Guarda**: órdenes de trabajo con su estado, las evaluaciones climáticas, el historial de suspensiones y los umbrales climáticos.
- **Hace**: orquesta el proceso central (sección 5): pide datos a Obras, recursos a Recursos y el pronóstico a Open-Meteo, y decide. Lleva la máquina de estados de la orden y la **saga de suspensión** (cuando no se puede trabajar, deshace y reprograma en los otros servicios). Tiene un **job** que corre todos los días a las 6:00.
- **Reglas**: RN-09 (umbrales climáticos por tipo de trabajo), RN-10 (sin dato climático confiable no se habilita sola), RN-11 (solo transiciones de estado válidas) y RN-12 (tres suspensiones ponen la fase en riesgo).
- **Habla con**: **Open-Meteo**, una API pública de pronóstico. Si no responde, la orden queda pendiente de que decida el capataz.
- 13 endpoints.

## 5. El proceso central: la habilitación diaria de una orden

Lo dispara el PLANIFICADOR (para una orden o para todas las de un día) o el job diario.

1. **Operaciones pregunta a Obras** por la fase: qué tipo de trabajo es, dónde queda el tramo y si la fase anterior terminó. Si no terminó, no sigue (RN-01).
2. **Operaciones pide a Recursos** que proponga y reserve: Recursos elige cuadrilla y planta (RN-05 a RN-08) y deja una reserva provisoria con la hora de llegada de la cuadrilla y la hora de salida del camión. Si no hay recursos, devuelve el motivo y la orden sigue programada.
3. **Operaciones consulta Open-Meteo** para la fecha de la orden y mira el peor valor de la jornada (de 08:00 a 13:00): humedad del suelo, probabilidad de lluvia y temperatura.
4. **Decide**:
   - Si cumple los umbrales del tipo de trabajo: confirma la reserva y la orden queda **HABILITADA**. Después el capataz la inicia y registra el avance; al 100 % la fase y la orden quedan **COMPLETADAS**.
   - Si no cumple: la orden queda **SUSPENDIDA** y empieza la saga: Recursos libera la reserva, Obras corre la fase y las que dependen de ella, y Operaciones crea una orden nueva para la fecha nueva.
   - Si Open-Meteo no dio un dato confiable: la orden queda **pendiente de confirmación climática**, con la reserva guardada, y decide el capataz.
5. **A la tercera suspensión** de una misma fase, el sistema deja de reprogramarla solo: la fase queda **en riesgo** y el planificador decide (reprograma la fase y crea la orden nueva).

## 6. Cuando algo falla

El sistema está pensado para que una falla no deje datos a medias:

- **Open-Meteo caído**: tres reintentos; si sigue, la orden queda pendiente y decide el capataz. Nunca se inventa un dato del clima.
- **OSRM caído o saturado**: se estima el viaje en línea recta y la reserva queda marcada como estimada.
- **Obras o Recursos caídos**: la orden queda como estaba y el job la vuelve a intentar.
- **Operaciones se cae a mitad de camino**: las reservas provisorias vencen solas a los 30 minutos y el job retoma al día siguiente (o cuando se lo vuelve a disparar) sin repetir lo que ya se hizo.

## 7. Datos y tecnología

- **Java 25 y Spring Boot 4.1.2**, Spring Cloud Gateway (variante MVC), Spring Security con JWT, Spring Data JPA con Hibernate, Resilience4j (reintentos, circuit breaker, rate limiter) y springdoc (Swagger).
- **PostgreSQL 16**, una base por servicio con su propio usuario. **Flyway** versiona el esquema y carga los datos de demo.
- **Keycloak 24** para usuarios, roles y tokens.
- **Docker Compose** levanta los seis contenedores con controles de salud.
- Datos de demo: la obra "Ruta Provincial 34" con dos tramos (uno en Córdoba y otro en Río Cuarto, lejos de la cuadrilla), las cuadrillas Alfa y Beta Lejana, una planta de mezcla, los umbrales de cada tipo de trabajo y tres órdenes (para hoy, mañana y pasado mañana) que muestran una habilitación, una suspensión por clima y un rechazo por distancia.

## 8. Qué quedó afuera

Con la autorización de la cátedra (2026-11-16) se recortó el alcance para concentrarse en el proceso central: no hay altas ni ediciones de recursos y configuración (vienen precargados), ni edición de obras y tramos, ni consultas sueltas que repetían información, ni cancelación o reprogramación manual de órdenes, ni cierre con consumo real de materiales, ni caché del pronóstico, ni rol de operario. Después se quitó también RN-04 (certificación de los operarios). Tampoco forman parte del alcance el mantenimiento preventivo, la reasignación automática de cuadrillas, las notificaciones, el GPS en tiempo real ni los reportes. El detalle está en [`guia-del-proyecto.md`](guia-del-proyecto.md) (comunicación con la cátedra) y en `DECISIONES.md`.
