package com.obrasviales.ms_operaciones.dtos;

import com.obrasviales.ms_operaciones.modelo.EstadoOrden;

import java.math.BigDecimal;
import java.util.List;

public record CierreOrdenResponse(Long ordenId, EstadoOrden estado, BigDecimal horasMaquina,
                                  List<CierreOrdenRequest.MaterialConsumido> materiales) {
}
