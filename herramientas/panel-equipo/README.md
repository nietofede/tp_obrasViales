# Panel del equipo (herramienta interna)

**No es parte de la entrega ni se muestra en la defensa.** Es una página para que el equipo pruebe el backend sin armar requests a mano: pide los tokens de los seis roles, lista todos los endpoints con sus roles permitidos y un cuerpo de ejemplo, recorre el proceso central paso a paso y compara los permisos de un endpoint contra la matriz (sin token, rol insuficiente y rol correcto).

## Cómo se usa

1. Levantá el stack (`docker compose up -d --build` desde la raíz) y esperá los seis contenedores `healthy`.
2. Desde esta carpeta:

   ```powershell
   cd herramientas/panel-equipo
   java PanelEquipo.java
   ```

3. Abrí <http://localhost:9090>.
4. Tocá **Pedir todos** (los tokens duran 5 minutos; el panel los renueva solo cuando hace falta).

Si el stack usa otros puertos: `java PanelEquipo.java 9090 http://localhost:18080 http://localhost:18081` (puerto del panel, Keycloak y gateway).

## Qué tiene

- **Explorador de la API**: todos los endpoints del cuestionario (más los extras), agrupados por servicio, con método, ruta, roles permitidos y un cuerpo de ejemplo. Elegís uno, el rol con el que querés mandarlo, lo editás si hace falta y ves el código, el tiempo, el `X-Correlation-Id` y la respuesta.
- **Proceso central**: los pasos de la demo con los datos sembrados (habilitar hoy, forzar una suspensión por clima, rechazo por distancia, ejecución del CAPATAZ). Necesita una base limpia (`docker compose down -v`).
- **Probar permisos**: manda el endpoint elegido sin token y con los seis roles y marca si cada respuesta es la esperada.

Los endpoints `/internos/` son solo para la cuenta SISTEMA: el gateway rechaza cualquier otro rol con 403. Los servicios los llaman directo entre sí, y desde el panel se pueden probar con el rol SISTEMA.

## Cómo funciona por dentro

`PanelEquipo.java` es un programa Java de un solo archivo, sin dependencias (`java Archivo.java` lo compila y lo corre). Sirve `index.html` y reenvía los pedidos del navegador:

- `/kc/...` → Keycloak
- `/gw/...` → gateway

Así el navegador habla con un solo origen y no hace falta configurar CORS en el backend (que es lo que habría que hacer para llamar a Keycloak y al gateway directo desde una página). Escucha solo en `127.0.0.1`.

El secreto de la cuenta SISTEMA viene cargado con el valor de demo de `.env.example`; si lo cambiaron en su `.env`, cámbienlo en el panel.
