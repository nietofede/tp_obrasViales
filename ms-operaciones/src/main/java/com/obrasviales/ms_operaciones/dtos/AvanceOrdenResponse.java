package com.obrasviales.ms_operaciones.dtos;

import java.math.BigDecimal;

// Avance de la fase tal como quedo en Obras despues de registrarlo.
public record AvanceOrdenResponse(Long ordenId, Long faseId, BigDecimal avanceFase, String estadoFase) {
}
