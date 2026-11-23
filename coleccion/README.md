# Colección de Postman

La colección para la presentación y para probar todo es la **modular**. La original queda como respaldo.

Archivos:

- `govp-modular.postman_collection.json` (**la principal**): 135 requests en 6 carpetas, con sus propias variables (`baseUrl`, `keycloakUrl`, `sistemaSecret`), así que corre sin entorno.
  1. `01 - Usuarios`: tokens de los 5 roles (SISTEMA por client credentials).
  2. `02 - Flujo Básico`: las 3 órdenes del seed. La orden 1 de punta a punta y, en `06b`, la orden 2 suspendida por clima (con la saga) y la orden 3 rechazada por distancia (RN-07), antes de ejecutar la orden 1.
  3. `03 - Reglas de Negocio`: por cada regla, éxito y rechazo. Crea su propia obra con dos fases en `Preparación`. RN-01 también se ve frenando la habilitación (409 desde Operaciones), RN-09 habilita una orden con umbrales imposibles y queda REPROGRAMADA, y RN-12 sigue con la segunda y la tercera suspensión hasta la fase `EN_RIESGO`.
  4. `04 - Endpoints por Servicio`: los 32 endpoints uno por uno, más los filtros de órdenes (`02b` a `02e`).
  5. `05 - Seguridad`: 401, 403 y rol correcto en los tres servicios, rutas internas solo para SISTEMA y check-in en una cuadrilla ajena.
  6. `06 - Fallas y resiliencia`: Recursos, Obras, Open-Meteo y OSRM caídos y token vencido. Cada request dice qué apagar antes y qué restaurar después; con el stack sano recorren el camino feliz.
- `obras-viales.postman_collection.json`: la colección original (95 requests), con el entorno `local.postman_environment.json`. Todo lo que tenía está también en la modular.
- `local.postman_environment.json`: variables de entorno de ejemplo para la original.

Verificación: el 23/11/2026, con Newman sobre un stack desde cero, la modular dio 135 requests, 184 aserciones y 0 fallos.

Equivalencias entre las dos colecciones (los documentos viejos nombran las carpetas de la original):

| Original (`obras-viales`) | Modular (`govp-modular`) |
|---|---|
| `00-Auth` | `01 - Usuarios` |
| `01-Setup-Datos` | `03 - Reglas de Negocio > Preparación` y `04 - Endpoints por Servicio` |
| `02-Seguridad` | `05 - Seguridad` (por servicio: Obras y token, Recursos, Operaciones) |
| `03-Flujo-Principal` | `02 - Flujo Básico` (órdenes 2 y 3 del seed en `06b`) |
| `04-Reglas-Negocio > RN-xx` | `03 - Reglas de Negocio > RN-xx` |
| `05-Fallas-y-Resiliencia` | `06 - Fallas y resiliencia` |
| Filtros de órdenes | `04 > ms-operaciones > 02b a 02e` |

## Cómo usarla

1. Levantar el stack desde cero (ver el README principal): `docker compose down -v` y `docker compose up -d --build`, el mismo día de la prueba (las fechas de las órdenes del seed se calculan al crear la base).
2. En Postman: Import y elegir `govp-modular.postman_collection.json`. No necesita entorno.
3. Correr `01 - Usuarios` y después las carpetas en orden. Para correrla entera: `02`, `03`, `04`, `05` y `06`.
   - Los tokens duran 5 minutos: si algo da 401 inesperado, repetir `01 - Usuarios`.
   - Cada request tiene en su descripción el resultado esperado y un test que lo comprueba.
   - Desde la terminal: `npx -y newman@6 run coleccion/govp-modular.postman_collection.json`.

## Notas

- El Flujo principal usa el seed: obra 1, fase 2 y las órdenes de `V4__seed_demo_orders.sql`. Necesita una base limpia (`docker compose down -v`).
- Si el clima real queda fuera de los umbrales, la orden 1 se suspende en lugar de habilitarse: en la modular el paso `01` del Flujo Básico ya los sube; en la original, usar `04-Reglas-Negocio > RN-09` para bajar o restaurar los umbrales.
- La carpeta 05-Fallas-y-Resiliencia indica qué contenedor apagar o qué variable cambiar antes de cada request, y qué restaurar después.
- El `X-Correlation-Id` que envía la colección lo ignora el gateway, que genera el suyo (se ve en la respuesta).
- Corrida verificada el 17/11/2026 sobre el stack levantado desde cero, con los umbrales de BASE permisivos antes de empezar: 95 requests y 122 aserciones en verde, con Newman. Cada carpeta de `04-Reglas-Negocio` tiene al menos un request que viola su regla; RN-09 y RN-12 usan la fase RELEVAMIENTO creada en `01-Setup-Datos`, así que necesitan esa carpeta corrida antes. Las carpetas de Fallas, con el stack sano, solo recorren el camino feliz; para probar cada falla hay que apagar el contenedor o cambiar la variable que indica cada request.
- Para volver a correrla hace falta una base limpia (`docker compose down -v`): el Flujo principal completa la orden 1 y no se puede repetir.
- Cada request lleva el encabezado `X-Correlation-Id` con un UUID nuevo (variable `correlation_id`).
- Correrla por línea de comandos: `npx newman run coleccion/obras-viales.postman_collection.json -e coleccion/local.postman_environment.json`.
