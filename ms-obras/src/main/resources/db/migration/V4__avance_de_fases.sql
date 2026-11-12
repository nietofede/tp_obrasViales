-- Avance real de cada fase (0 a 100). Al llegar a 100 la fase queda COMPLETADA.
ALTER TABLE fases ADD COLUMN avance_porcentaje INT NOT NULL DEFAULT 0
    CHECK (avance_porcentaje BETWEEN 0 AND 100);
UPDATE fases SET avance_porcentaje = 100 WHERE estado = 'COMPLETADA';