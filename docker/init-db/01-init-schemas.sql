-- 1. Acá creamos el usuario y base de datos para Keycloak
CREATE USER keycloak_user WITH ENCRYPTED PASSWORD 'keycloak_pass';
CREATE DATABASE keycloak_db OWNER keycloak_user;
GRANT ALL PRIVILEGES ON DATABASE keycloak_db TO keycloak_user;

-- 2. Acá creamos usuarios dedicados por cada microservicio
CREATE USER usr_obras WITH ENCRYPTED PASSWORD 'obras_pass';
CREATE USER usr_recursos WITH ENCRYPTED PASSWORD 'recursos_pass';
CREATE USER usr_operaciones WITH ENCRYPTED PASSWORD 'operaciones_pass';

-- 3. Acá creamos las bases de datos de cada microservicios
CREATE DATABASE ms_obras OWNER usr_obras;
CREATE DATABASE ms_recursos OWNER usr_recursos;
CREATE DATABASE ms_operaciones OWNER usr_operaciones;

GRANT ALL PRIVILEGES ON DATABASE ms_obras TO usr_obras;
GRANT ALL PRIVILEGES ON DATABASE ms_recursos TO usr_recursos;
GRANT ALL PRIVILEGES ON DATABASE ms_operaciones TO usr_operaciones;

-- 4. Por defecto Postgres da CONNECT a PUBLIC (cualquier usuario) en toda base nueva.
-- Lo quitamos para que cada usuario solo pueda conectarse a su propia base.
REVOKE CONNECT ON DATABASE keycloak_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE ms_obras FROM PUBLIC;
REVOKE CONNECT ON DATABASE ms_recursos FROM PUBLIC;
REVOKE CONNECT ON DATABASE ms_operaciones FROM PUBLIC;
