-- Recorte de alcance (ver docs/DECISIONES.md): la decision sobre una fase en riesgo ya no se registra aparte
-- (el PLANIFICADOR reprograma la fase en Obras y crea la orden nueva) y el cierre de una orden ya no guarda
-- horas de maquina ni consumo real de materiales (la orden se completa con el avance al 100 %).
DROP TABLE IF EXISTS fases_en_riesgo;
DROP TABLE IF EXISTS cierre_consumos;
DROP TABLE IF EXISTS cierres_orden;
