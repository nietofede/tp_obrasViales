
CREATE TABLE suspensiones (
    id BIGSERIAL PRIMARY KEY,
    orden_trabajo_id BIGINT NOT NULL REFERENCES ordenes_trabajo(id),
    fase_id BIGINT NOT NULL,
    motivo VARCHAR(30) NOT NULL,
    detalle VARCHAR(500),
    vigente BOOLEAN NOT NULL DEFAULT TRUE,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_suspensiones_fase ON suspensiones (fase_id);

CREATE TABLE configuracion_suspensiones (
    id BIGINT PRIMARY KEY,
    limite_suspensiones INT NOT NULL CHECK (limite_suspensiones >= 1)
);

INSERT INTO configuracion_suspensiones (id, limite_suspensiones) VALUES (1, 3);

CREATE TABLE fases_en_riesgo (
    id BIGSERIAL PRIMARY KEY,
    fase_id BIGINT NOT NULL,
    orden_trabajo_id BIGINT NOT NULL REFERENCES ordenes_trabajo(id),
    resuelta BOOLEAN NOT NULL DEFAULT FALSE,
    decision VARCHAR(20),
    creado TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resuelta_en TIMESTAMP
);
