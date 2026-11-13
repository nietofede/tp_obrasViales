CREATE TABLE ordenes_trabajo (
    id BIGSERIAL PRIMARY KEY,
    fase_id BIGINT NOT NULL,
    tramo_id BIGINT NOT NULL,
    fecha_programada DATE NOT NULL,
    estado VARCHAR(50) NOT NULL,
    reserva_id BIGINT,
    hora_salida_camion TIMESTAMP, 
    contador_suspensiones INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    creado TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE evaluaciones_climaticas (
    id BIGSERIAL PRIMARY KEY,
    orden_trabajo_id BIGINT NOT NULL REFERENCES ordenes_trabajo(id),
    humedad_suelo_registrada DOUBLE PRECISION,
    prob_precipitacion_registrada DOUBLE PRECISION,
    temperatura_registrada DOUBLE PRECISION,
    umbrales_cumplidos BOOLEAN NOT NULL,
    fecha_evaluacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE umbrales_climaticos (
    id BIGSERIAL PRIMARY KEY,
    tipo_trabajo VARCHAR(50) NOT NULL UNIQUE,
    humedad_suelo_maxima DOUBLE PRECISION NOT NULL,
    prob_precipitacion_maxima DOUBLE PRECISION NOT NULL,
    temperatura_minima DOUBLE PRECISION NOT NULL
);
