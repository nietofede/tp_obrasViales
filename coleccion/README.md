# Colección de Postman

Archivos:

- `obras-viales.postman_collection.json`: la colección (53 requests).
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
- Recursos (máquinas, operarios, cuadrillas) y los permisos finales se agregan en la segunda pasada, cuando se mergeen las Ramas 1 y 2.
- Primera pasada: no se corrió todavía contra el stack. Los códigos esperados salen de los controllers y de la trazabilidad; hay que verificarlos al correrla.
