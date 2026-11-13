# Colección de Postman

Archivos:

- `obras-viales.postman_collection.json`: la colección (93 requests).
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
- Pendiente de la Rama 2: los permisos finales de Operaciones (hoy `GET /ordenes` lo lee cualquier autenticado) y la orden 3 del seed.
- Corrida verificada el 13/11/2026 sobre el stack levantado desde cero (`docker compose up -d --build`, con la Rama 1 y sin la Rama 2): 93 requests y 101 aserciones en verde, con Newman. Las carpetas de Fallas, con el stack sano, solo recorren el camino feliz; para probar cada falla hay que apagar el contenedor o cambiar la variable que indica cada request.
- Para volver a correrla hace falta una base limpia (`docker compose down -v`): el Flujo principal completa la orden 1 y no se puede repetir.
- Cada request lleva el encabezado `X-Correlation-Id` con un UUID nuevo (variable `correlation_id`), para seguirlo en los logs. El gateway genera el suyo y reenvía ese; verificar en la segunda pasada si conserva el del cliente.
- Correrla por línea de comandos: `npx newman run coleccion/obras-viales.postman_collection.json -e coleccion/local.postman_environment.json`.
