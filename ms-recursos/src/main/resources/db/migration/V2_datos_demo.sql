INSERT INTO configuracion_desplazamiento (id, viaje_maximo_cuadrilla_min, duracion_jornada_min, ventana_transporte_mezcla_min, margen_camion_min)
VALUES (1, 90, 480, 60, 15);

INSERT INTO composicion_minima_trabajo (tipo_trabajo, min_operarios, tipo_maquina_requerida, cantidad_maquinas, requiere_mezcla) VALUES
('RELEVAMIENTO', 2, NULL, 0, FALSE),
('BASE', 2, 'RODILLO_COMPACTADOR', 1, FALSE),
('PAVIMENTACION', 3, 'PAVIMENTADORA', 1, TRUE);

INSERT INTO plantas_mezcla (nombre, latitud, longitud, activa) VALUES
('Planta Asfáltica Córdoba Norte', -31.3500, -64.2000, TRUE);

INSERT INTO cuadrillas (nombre, ultima_latitud, ultima_longitud) VALUES
('Cuadrilla Alfa', -31.3800, -64.2200),
('Cuadrilla Beta Lejana', -33.1230, -64.3490);

INSERT INTO operarios (cuadrilla_id, keycloak_username, nombre, rol_operativo, estado) VALUES
(1, 'operario', 'Juan Pérez', 'MAQUINISTA', 'ACTIVO'),
(1, 'capataz', 'Carlos Gómez', 'CAPATAZ', 'ACTIVO'),
(1, 'operario2', 'Mateo Sosa', 'PEON', 'ACTIVO');

INSERT INTO certificaciones (operario_id, tipo_maquina, vencimiento) VALUES
(1, 'PAVIMENTADORA', '2027-12-31'),
(1, 'RODILLO_COMPACTADOR', '2027-12-31'),
(2, 'PAVIMENTADORA', '2025-01-01');

INSERT INTO maquinas (cuadrilla_id, codigo, tipo, estado, horas_uso) VALUES
(1, 'PAV-01', 'PAVIMENTADORA', 'DISPONIBLE', 120),
(1, 'ROD-01', 'RODILLO_COMPACTADOR', 'DISPONIBLE', 340);