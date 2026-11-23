# Colección de Postman

Archivos:

- `obras-viales.postman_collection.json`: la colección original integral (95 requests), con un caso de error por regla.
- `govp-modular.postman_collection.json`: nueva colección modular organizada en 4 carpetas (Usuarios, Flujo Básico, Reglas de Negocio con éxito/rechazo, y Endpoints por Servicio): 87 requests. El Flujo Básico recorre las 3 órdenes del seed: la orden 1 de punta a punta y, en `06b`, la orden 2 suspendida por clima (con la saga) y la orden 3 rechazada por distancia (RN-07), antes de ejecutar la orden 1. Trae sus propias variables (`baseUrl`, `keycloakUrl`, `sistemaSecret`), así que corre sin entorno. Verificada el 22/11/2026 con Newman sobre un stack desde cero: 82 requests, 114 aserciones, 0 fallos (antes de sumar `06b`, que agrega 5 requests y 8 aserciones). Se corre entera y en orden: «03 - Reglas» crea su propia obra con dos fases, porque las del seed quedan COMPLETADAS con el Flujo Básico.
- `local.postman_environment.json`: variables de entorno de ejemplo (`baseUrl`, `keycloakUrl`, `sistemaSecret`).

## Cómo usarla

1. Levantar el stack desde cero (ver el README principal): `docker compose up -d --build`.
2. En Postman: Import, y elegir los dos archivos. Seleccionar el entorno «Obras Viales - local».
3. Correr las carpetas en orden: 00-Auth, 01-Setup-Datos, 02-Seguridad, 03-Flujo-Principal, 04-Reglas-Negocio, 05-Fallas-y-Resiliencia.
   - Los tokens duran 5 minutos: si algo da 401 inesperado, repetir Auth.
   - Cada request tiene en su descripción el resultado esperado y un test que lo comprueba.

## Notas

- El Flujo principal usa el seed: obra 1, fase 2 y las órdenes de `V4__seed_demo_orders.sql`. Necesita una base limpia (`docker compose down -v`).
- Si el clima real queda fuera de los umbrales, la orden 1 se suspende en lugar de habilitarse: usar los requests de `04-Reglas-Negocio > RN-09` para bajar o restaurar los umbrales.
- La carpeta 05-Fallas-y-Resiliencia indica qué contenedor apagar o qué variable cambiar antes de cada request, y qué restaurar después.
- El `X-Correlation-Id` que envía la colección lo ignora el gateway, que genera el suyo (se ve en la respuesta).
- Corrida verificada el 17/11/2026 sobre el stack levantado desde cero, con los umbrales de BASE permisivos antes de empezar: 95 requests y 122 aserciones en verde, con Newman. Cada carpeta de `04-Reglas-Negocio` tiene al menos un request que viola su regla; RN-09 y RN-12 usan la fase RELEVAMIENTO creada en `01-Setup-Datos`, así que necesitan esa carpeta corrida antes. Las carpetas de Fallas, con el stack sano, solo recorren el camino feliz; para probar cada falla hay que apagar el contenedor o cambiar la variable que indica cada request.
- Para volver a correrla hace falta una base limpia (`docker compose down -v`): el Flujo principal completa la orden 1 y no se puede repetir.
- Cada request lleva el encabezado `X-Correlation-Id` con un UUID nuevo (variable `correlation_id`).
- Correrla por línea de comandos: `npx newman run coleccion/obras-viales.postman_collection.json -e coleccion/local.postman_environment.json`.
