-- Escenario de demostración del job diario de habilitación (paso 11).
-- Acompaña a V4__seed_demo_orders.sql de ms-operaciones, que siembra 3 órdenes PROGRAMADA:
--   hoy y mañana sobre la fase 2 del tramo 1, y pasado mañana sobre la fase 3 del tramo 2.
-- Las fechas se calculan con la hora de Argentina (CURRENT_DATE usa UTC en Docker), igual que en ese seed.
-- En una base limpia el tramo nuevo queda con id 2 y la fase nueva con id 3, que es lo que espera el seed;
-- con una base anterior hay que recrearla (docker compose down -v).

-- 1. Fechas de las fases del tramo 1, coherentes con las órdenes.
--    La fase 1 termina ayer: si una orden de la fase 2 se suspende, la saga la reprograma al día siguiente
--    y RN-02 exige que no empiece antes del fin de la fase anterior. Con la fase 1 en diciembre, eso fallaba.
UPDATE fases
   SET fecha_inicio_planificada = (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date - 10,
       fecha_fin_planificada    = (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date - 1
 WHERE tramo_id = 1 AND tipo = 'RELEVAMIENTO';

UPDATE fases
   SET fecha_inicio_planificada = (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date,
       fecha_fin_planificada    = (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date + 5
 WHERE tramo_id = 1 AND tipo = 'BASE';

-- 2. Tramo lejano (Río Cuarto, unos 200 km de la cuadrilla Alfa en Córdoba). El viaje supera los 90 min
--    de viaje máximo, así que Recursos rechaza la reserva por RN-07 y la orden queda PROGRAMADA con el motivo.
INSERT INTO tramos (obra_id, nombre, latitud, longitud, superficie_m2)
VALUES (1, 'Tramo 2 - Acceso Río Cuarto', -33.1300, -64.3500, 3000.00);

-- 3. Fase BASE del tramo lejano, sin fase anterior (RN-01 no la frena). Es BASE porque la cuadrilla Alfa
--    cumple operarios, máquina y certificación para ese trabajo: lo único que falla es la distancia.
INSERT INTO fases (tramo_id, tipo, estado, fecha_inicio_planificada, fecha_fin_planificada)
SELECT t.id, 'BASE', 'PENDIENTE',
       (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date + 2,
       (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date + 7
  FROM tramos t
 WHERE t.nombre = 'Tramo 2 - Acceso Río Cuarto';
