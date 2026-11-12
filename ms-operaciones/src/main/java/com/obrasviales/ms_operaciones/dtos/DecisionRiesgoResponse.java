package com.obrasviales.ms_operaciones.dtos;

import com.obrasviales.ms_operaciones.modelo.EstadoOrden;

// ordenId es la orden que estaba suspendida,nuevaOrdenId solo si se reprogramo.
public record DecisionRiesgoResponse(Long faseId, DecisionRiesgoRequest.DecisionRiesgo decision,
                                     Long ordenId, EstadoOrden estadoOrden, Long nuevaOrdenId) {
}
