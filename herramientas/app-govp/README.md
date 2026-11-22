# App GOVP (herramienta interna)

**No es parte de la entrega.** Es una app web para que el equipo use el sistema como lo usaría cada rol: se ingresa con un usuario de Keycloak y el menú muestra lo que ese rol puede hacer. Todo pasa por el gateway real, así que lo que el rol no puede hacer lo rechaza el backend (401, 403 o 422) y la app muestra el mensaje, la regla y el correlation-id.

## Cómo se levanta

1. Levantá el stack desde la raíz del repo y esperá los seis contenedores `healthy`:

   ```powershell
   docker compose up -d --build
   docker compose ps
   ```

2. En otra terminal:

   ```powershell
   cd herramientas/app-govp
   java ServidorApp.java
   ```

3. Abrí <http://localhost:9100> e ingresá con un usuario de demo: `admin`, `planificador`, `capataz` o `auditor`, con contraseña `<usuario>123` (por ejemplo `planificador123`). En la pantalla de ingreso, "Usuarios de demo" los completa solos.

Para cerrarla, `Ctrl+C` en esa terminal.

Si el stack usa otros puertos: `java ServidorApp.java 9100 http://localhost:<puerto de Keycloak> http://localhost:<puerto del gateway>`.

## Qué puede hacer cada rol

| Rol | Pantallas y acciones |
|---|---|
| PLANIFICADOR | Obras (crear, agregar tramos, crear y reprogramar fases, cronograma, materiales), órdenes (programar y habilitar una o todas las de un día), suspensiones, lectura de cuadrillas, máquinas, operarios y umbrales |
| CAPATAZ | Órdenes (iniciar, rechazar, registrar avance hasta el 100 %, resolver el clima incierto), check-in de su cuadrilla, lectura de obras, máquinas, operarios y umbrales |
| ADMIN | Umbrales climáticos y lectura de cuadrillas, máquinas y operarios |
| AUDITOR | Lectura de obras, órdenes con sus evaluaciones climáticas, suspensiones y recursos |

Con el recorte de alcance aprobado por la cátedra (2026-11-16) los recursos y la configuración vienen precargados por Flyway: la app solo los muestra.

## Recorrido sugerido (proceso central)

Con una base limpia (`docker compose down -v` antes de levantar):

1. **ADMIN** → Umbrales climáticos → Editar Base. El clima es real: si hoy llueve entre las 08:00 y las 13:00, la orden de hoy se suspende. Para mostrar el camino feliz, subí los umbrales (humedad 1, lluvia 100, temperatura −50); para forzar una suspensión, bajalos.
2. **PLANIFICADOR** → Órdenes → Habilitar un día (hoy). Se ve el resultado de cada orden: habilitada, suspendida y reprogramada, o el motivo si quedó programada.
3. **PLANIFICADOR** → abrí una orden: estado, reserva, salida del camión y evaluación climática.
4. **CAPATAZ** → la misma orden → Iniciar y Registrar avance (100). La fase y la orden quedan completadas.
5. **PLANIFICADOR** → Órdenes → la orden 3 (tramo de Río Cuarto, pasado mañana) → Habilitar ahora: Recursos la rechaza por distancia (RN-07).
6. **AUDITOR** → Suspensiones, solo lectura. Una fase en riesgo (RN-12) se ve en Obras con estado "En riesgo"; el PLANIFICADOR la reprograma y crea la orden nueva.

## Cómo está hecha

- `ServidorApp.java`: programa Java de un solo archivo, sin dependencias (`java Archivo.java` lo compila y lo ejecuta). Sirve `index.html`, `app.js` y `estilos.css`, y reenvía `/kc/...` a Keycloak y `/gw/...` al gateway. Así el navegador habla con un solo origen y no hace falta configurar CORS en el backend. Escucha solo en `127.0.0.1`.
- `app.js`: JavaScript sin frameworks ni build. Ingresa con el flujo de contraseña de Keycloak (cliente público `obras-public-client`), renueva el token con el `refresh_token` antes de que venza y guarda la sesión en `sessionStorage` (se borra al cerrar la pestaña). El menú y los botones salen de los roles del token (`realm_access.roles`) y del estado de cada orden según la máquina de estados (`EstadoOrden`).
- El ingreso con usuario y contraseña de Keycloak es aceptable solo para uso local con usuarios de demo.
