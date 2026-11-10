CREATE TABLE obras (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    descripcion TEXT,
    estado VARCHAR(50) DEFAULT 'PLANIFICADA'
);

CREATE TABLE tramos (
    id SERIAL PRIMARY KEY,
    obra_id INT NOT NULL REFERENCES obras(id),
    latitud VARCHAR(50) NOT NULL,
    longitud VARCHAR(50) NOT NULL,
    superficie_m2 DECIMAL(10, 2) NOT NULL
);

CREATE TABLE fases (
    id SERIAL PRIMARY KEY,
    tramo_id INT NOT NULL REFERENCES tramos(id),
    tipo_fase VARCHAR(100) NOT NULL,
    estado VARCHAR(50) default 'PENDIENTE',
    fase_previa_id INT REFERENCES fases(id)
);

INSERT INTO obras (nombre, descripcion) VALUES ('Ruta Provincial 34', 'Repavimentación acceso norte');
INSERT INTO tramos (obra_id, latitud, longitud, superficie_m2) VALUES (1, '-31.245', '-64.456', 5000.00);
INSERT INTO fases (tramo_id, tipo_fase, estado) VALUES (1, 'RELEVAMIENTO', 'COMPLETADA');
INSERT INTO fases (tramo_id, tipo_fase, estado, fase_previa_id) VALUES (1, 'BASE', 'PENDIENTE', 1);