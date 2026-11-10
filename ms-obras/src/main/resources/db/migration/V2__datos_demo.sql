
INSERT INTO obras (nombre, descripcion) VALUES ('Ruta Provincial 34', 'Repavimentación acceso norte');
INSERT INTO tramos (obra_id, latitud, longitud, superficie_m2) VALUES (1, -31.245, -64.456, 5000.00);
INSERT INTO fases (tramo_id, tipo_fase, estado) VALUES (1, 'RELEVAMIENTO', 'COMPLETADA');
INSERT INTO fases (tramo_id, tipo_fase, estado, fase_previa_id) VALUES (1, 'BASE', 'PENDIENTE', 1);