package com.obrasviales.ms_operaciones.dtos;

import com.obrasviales.ms_operaciones.modelo.EstadoOrden;

import java.math.BigDecimal;

// Avance de la fase tal como quedo en Obras despues de registrarlo, y el estado de la orden (COMPLETADA al 100 %).
public record AvanceOrdenResponse(Long ordenId, Long faseId, BigDecimal avanceFase, String estadoFase,
                                  EstadoOrden estadoOrden) {
}
