-- Datos iniciales de prueba para la demo (Job diario y evaluación de órdenes)
-- Las 3 órdenes inician listas para evaluación climática y operativa con fecha de hoy

-- 1. Orden destinada a ser HABILITADA
INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    1, 1, CURRENT_DATE, 'EVALUACION_CLIMATICA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 2. Orden destinada a ser SUSPENDIDA POR CLIMA
INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    2, 2, CURRENT_DATE, 'EVALUACION_CLIMATICA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 3. Orden destinada a ser CANCELADA / RECHAZADA POR DISTANCIA
INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    3, 3, CURRENT_DATE, 'EVALUACION_CLIMATICA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);