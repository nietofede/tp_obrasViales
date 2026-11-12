-- Datos iniciales de prueba para la demo (Job diario y evaluación de órdenes)
-- Las 3 órdenes inician en estado PROGRAMADA para la fecha actual

-- 1. Orden destinada a ser HABILITADA (Tramo 1 cercano, Fase 2 con fase anterior completada)
INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    2, 1, CURRENT_DATE, 'PROGRAMADA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 2. Orden destinada a ser SUSPENDIDA POR CLIMA
INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    2, 1, CURRENT_DATE, 'PROGRAMADA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 3. Orden destinada a ser CANCELADA / RECHAZADA POR DISTANCIA (Tramo lejano en V5 de ms-obras)
INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    3, 2, CURRENT_DATE, 'PROGRAMADA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
