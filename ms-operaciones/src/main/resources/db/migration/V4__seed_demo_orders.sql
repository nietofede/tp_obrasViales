-- Datos de demostración para el job diario de habilitación (paso 11).
-- Las 3 órdenes empiezan PROGRAMADA, cada una en su propia fecha: comparten fase y tramo, y en el seed de
-- Recursos solo la cuadrilla Alfa puede trabajar, así que dos órdenes el mismo día chocarían por RN-05.
-- Las fechas se calculan con la hora de Argentina: CURRENT_DATE usa la zona de la sesión (UTC en Docker)
-- y después de las 21 h daría el día siguiente.

-- 1. Hoy: fase 2 del tramo 1 (fase previa completa). Con clima dentro de los umbrales queda HABILITADA.
INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    2, 1, (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date, 'PROGRAMADA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 2. Mañana: misma fase, para mostrar la suspensión por clima. Con el clima real no se puede forzar;
--    ver «Tarea 11» en docs/DECISIONES.md (pendiente de decisión del equipo).
INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    2, 1, (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date + 1, 'PROGRAMADA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 3. Pasado mañana: fase 3 del tramo 2, un tramo lejano que Recursos rechaza por distancia.
--    Necesita ese tramo y esa fase en ms-obras (migración pendiente, a coordinar con el autor de Obras);
--    sin ellos Obras responde 404 y la orden queda PROGRAMADA.
INSERT INTO ordenes_trabajo (
    fase_id, tramo_id, fecha_programada, estado, contador_suspensiones, version, creado, actualizado
) VALUES (
    3, 2, (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date + 2, 'PROGRAMADA', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
