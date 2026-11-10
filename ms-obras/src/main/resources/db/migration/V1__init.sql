CREATE TABLE obras (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    descripcion TEXT,
    estado VARCHAR(50) DEFAULT 'PLANIFICADA'
);

CREATE TABLE tramos (
    id BIGSERIAL PRIMARY KEY,
    obra_id BIGINT NOT NULL REFERENCES obras(id),
    latitud DOUBLE PRECISION NOT NULL CHECK (latitud BETWEEN -90 AND 90),
    longitud DOUBLE PRECISION NOT NULL CHECK (longitud BETWEEN -180 AND 180),
    superficie_m2 DECIMAL(10, 2) NOT NULL
);

CREATE TABLE fases (
    id BIGSERIAL PRIMARY KEY,
    tramo_id BIGINT NOT NULL REFERENCES tramos(id),
    tipo_fase VARCHAR(100) NOT NULL,
    estado VARCHAR(50) default 'PENDIENTE',
    fase_previa_id BIGINT REFERENCES fases(id)
);
