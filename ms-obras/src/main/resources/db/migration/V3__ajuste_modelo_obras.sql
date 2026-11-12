-- Alinea el esquema de V1 con las entidades JPA de ms-obras y agrega los consumos de RN-03

-- tramos: falta el nombre
ALTER TABLE tramos ADD COLUMN nombre VARCHAR(255);
UPDATE tramos SET nombre = 'Tramo ' || id;
ALTER TABLE tramos ALTER COLUMN nombre SET NOT NULL;

-- fases: nombres de columnas y fechas planificadas
ALTER TABLE fases RENAME COLUMN tipo_fase TO tipo;
ALTER TABLE fases RENAME COLUMN fase_previa_id TO fase_anterior_id;
ALTER TABLE fases ADD COLUMN fecha_inicio_planificada DATE;
ALTER TABLE fases ADD COLUMN fecha_fin_planificada DATE;
UPDATE fases SET fecha_inicio_planificada = DATE '2026-12-01',
                 fecha_fin_planificada = DATE '2026-12-05'
 WHERE fase_anterior_id IS NULL;
UPDATE fases SET fecha_inicio_planificada = DATE '2026-12-05',
                 fecha_fin_planificada = DATE '2026-12-10'
 WHERE fase_anterior_id IS NOT NULL;
ALTER TABLE fases ALTER COLUMN fecha_inicio_planificada SET NOT NULL;
ALTER TABLE fases ALTER COLUMN fecha_fin_planificada SET NOT NULL;
ALTER TABLE fases ADD CONSTRAINT fases_fechas_validas
    CHECK (fecha_fin_planificada >= fecha_inicio_planificada);

-- RN-03: consumo de material por m2 segun el tipo de fase (configuracion)
CREATE TABLE consumos_material (
    id BIGSERIAL PRIMARY KEY,
    tipo_fase VARCHAR(100) NOT NULL,
    material VARCHAR(255) NOT NULL,
    consumo_por_m2 NUMERIC(12, 4) NOT NULL CHECK (consumo_por_m2 > 0),
    unidad VARCHAR(20) NOT NULL
);

INSERT INTO consumos_material (tipo_fase, material, consumo_por_m2, unidad) VALUES
    ('BASE', 'Piedra triturada', 0.2500, 'm3'),
    ('SUBBASE', 'Suelo-cemento', 0.2000, 'm3'),
    ('CARPETA_ASFALTICA', 'Mezcla asfaltica', 0.1200, 't'),
    ('LOSA_HORMIGON', 'Hormigon', 0.2000, 'm3'),
    ('LOSA_HORMIGON', 'Acero de refuerzo', 6.0000, 'kg');