-- Cierre de una orden: horas de maquina y consumo real de materiales.
CREATE TABLE cierres_orden (
    id BIGSERIAL PRIMARY KEY,
    orden_trabajo_id BIGINT NOT NULL UNIQUE REFERENCES ordenes_trabajo(id),
    horas_maquina NUMERIC(8, 2) NOT NULL CHECK (horas_maquina >= 0),
    cerrado TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cierre_consumos (
    cierre_id BIGINT NOT NULL REFERENCES cierres_orden(id),
    material VARCHAR(100) NOT NULL,
    cantidad NUMERIC(14, 2) NOT NULL CHECK (cantidad >= 0),
    unidad VARCHAR(20) NOT NULL
);

CREATE INDEX idx_cierre_consumos_cierre ON cierre_consumos (cierre_id);
