package com.obrasviales.ms_operaciones.modelo;

import java.util.Set;

// enum con todos los estados de la orden
public enum EstadoOrden {
    PROGRAMADA,
    RECURSOS_ASIGNADOS,
    EVALUACION_CLIMATICA,
    HABILITADA,
    EN_RIESGO,
    EN_EJECUCION,
    COMPLETADA,
    PENDIENTE_CONFIRMACION_CLIMATICA,
    SUSPENDIDA,
    FINALIZADA,
    REPROGRAMADA,
    CANCELADA;

    // este metodo te dice a que estados se puede transicionar 
    // es un switch con todos los estados, cada uno tiene un set de estados a los que puede ir 
    // si el nuevo estado esta en el set devuelve true, sino false. Para los finales puse que devuelva false de una 
    public boolean puedeTransicionarHacia(EstadoOrden nuevoEstado) {
        return switch (this) {
            case PROGRAMADA -> Set.of(RECURSOS_ASIGNADOS, CANCELADA, SUSPENDIDA).contains(nuevoEstado);
            case RECURSOS_ASIGNADOS -> Set.of(EVALUACION_CLIMATICA, SUSPENDIDA, CANCELADA).contains(nuevoEstado);
            case EVALUACION_CLIMATICA -> Set.of(HABILITADA, PENDIENTE_CONFIRMACION_CLIMATICA, SUSPENDIDA).contains(nuevoEstado);
            case PENDIENTE_CONFIRMACION_CLIMATICA -> Set.of(HABILITADA, SUSPENDIDA).contains(nuevoEstado);
            case HABILITADA -> Set.of(EN_EJECUCION, SUSPENDIDA).contains(nuevoEstado);
            case EN_EJECUCION -> Set.of(COMPLETADA, SUSPENDIDA).contains(nuevoEstado);
            case SUSPENDIDA -> Set.of(REPROGRAMADA, CANCELADA).contains(nuevoEstado);
            case EN_RIESGO -> Set.of(REPROGRAMADA, CANCELADA).contains(nuevoEstado);
            case REPROGRAMADA, COMPLETADA, CANCELADA, FINALIZADA -> false;
        };
    }
}