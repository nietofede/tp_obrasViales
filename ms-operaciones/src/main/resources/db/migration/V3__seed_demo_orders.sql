-- Datos iniciales de prueba para la demo (Job diario y evaluación de órdenes)
-- Las órdenes inician en estado PROGRAMADA para la fecha actual sobre el Tramo 1 / Fase 2 (BASE)

INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    2, 1, CURRENT_DATE, 'PROGRAMADA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    2, 1, CURRENT_DATE, 'PROGRAMADA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);