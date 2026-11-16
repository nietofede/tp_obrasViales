-- RN-04 (certificación vigente del operario) se quitó del alcance el 2026-11-16: ver docs/DECISIONES.md.
-- V1 y V2 ya están aplicadas y no se editan; los cambios van en esta versión nueva.
DROP TABLE certificaciones;

-- Restos del rol OPERARIO de Keycloak, que se quitó con el recorte: los usuarios 'operario' y 'operario2'
-- ya no existen en el realm. El usuario de Keycloak de un operario solo se usa para reconocer al capataz
-- en el check-in, así que a los demás se les deja vacío.
UPDATE operarios SET keycloak_username = NULL WHERE keycloak_username IN ('operario', 'operario2');

-- rol_operativo es el puesto dentro de la cuadrilla (MAQUINISTA, CAPATAZ o PEON), no un rol de Keycloak.
ALTER TABLE operarios ALTER COLUMN rol_operativo SET DEFAULT 'PEON';
