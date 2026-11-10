# Bitácora de decisiones — GOVP

Cada entrada: decisión, alternativas descartadas y consecuencias.

## Una base de datos por microservicio

**Problema inicial:** Se definia de diferente manera la base de datos, tanto en 01-init-schemas.ql, como en application.yml.

**Decisión:** `ms_obras`, `ms_recursos` y `ms_operaciones`, cada una con su usuario dueño (`usr_obras`, `usr_recursos`, `usr_operaciones`), en una única instancia. Las crea `docker/init-db/01-init-schemas.sql` y los `application.yml` apuntan a ellas. Usamos REVOKE CONNECT...FROM PUBLIC para que cada usuario pueda conectarse solo a su base de datos

**Alternativa descartada:** una base compartida con un esquema por servicio.

**Consecuencias:** ningún servicio puede leer tablas de otro, solo vía API. Flyway trabaja en el esquema `public` de cada base. Los scripts de `init-db` solo corren con el volumen vacío (`docker compose down -v` para re-crear).

## Usuarios keycloak

**Decision:** Se crearon usuarios de demo en el realm (`admin`, `planificador`, `capataz`, `operario`, `auditor`), uno por rol. Todos con contraseña `<rol>123`. Solo para demo/local 

**Alternativa descartada:** Se descarto crearlos a mano ya que no sirve para el arranque desde cero

**Consecuencias:** Permite arrancar desde cero sin pasos manuales. 

## Secretos env

**Problema:** las contraseñas de las bases estaban escritas en application.yml y en el SQL de init-db, y el secreto de `servicio-sistema` en el realm era `**********`. Con ese secreto, un client_credentials devolvía un token con rol SISTEMA.

**Decision:** 
- Dejamos las cotnraseñas y secretos seteadas en el env.example, que es la plantilla con los valores de demo. 
- 01-init-schemas.sql lee las pw desde el .env con \set de psql.
- Compose usa ${VAR:?message} para las contraseñas que lee el init, si falta alguna, al hacer docker compose up, falla y devuelve un mensajke. 
- Los `application.yml` usan `{$VAR}` sin valor por defecto
- El realm usa `${SERVICIO_SISTEMA_SECRET}`

**Alternativa descartada:** Se descarto usar placesholders para agilizar el arranque desde cero

**Consecuencias:** Permite hacer cp .env.example .env y compose up -d y que ande a la primera sin tener que configurar nada. Sin la variable, un servicio falla ("password authentication failed"). Los valoreas demo son publicos, hay que cambiarlos fuera de local!.



