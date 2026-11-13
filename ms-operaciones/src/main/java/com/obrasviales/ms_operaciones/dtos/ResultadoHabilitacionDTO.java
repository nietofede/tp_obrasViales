package com.obrasviales.ms_operaciones.dtos;

import com.obrasviales.ms_operaciones.modelo.EstadoOrden;

// Resultado de habilitar una orden dentro del proceso diario. motivo es null si la orden se proceso bien
// (el estado dice si quedo HABILITADA, SUSPENDIDA, etc.) y trae la razon si fue rechazada o fallo.
public record ResultadoHabilitacionDTO(Long ordenId, EstadoOrden estado, String motivo) {
}
