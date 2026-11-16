package com.obrasviales.ms_operaciones.modelo;

// enum con todos los estados de la orden. Las transiciones validas (RN-11) las controla cada servicio:
// EjecucionService.exigirEstado y el chequeo del principio de OrdenTrabajoServiceImpl.habilitarOrden.
public enum EstadoOrden {
    PROGRAMADA,
    RECURSOS_ASIGNADOS,
    EVALUACION_CLIMATICA,
    HABILITADA,
    EN_EJECUCION,
    COMPLETADA,
    PENDIENTE_CONFIRMACION_CLIMATICA,
    SUSPENDIDA,
    REPROGRAMADA,
    CANCELADA
}