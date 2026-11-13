package com.obrasviales.ms_operaciones.dtos;

import java.time.LocalDateTime;

public record EvaluacionClimaticaResponseDTO(
        Long id,
        Double humedadSueloRegistrada,
        Double probPrecipitacionRegistrada,
        Double temperaturaRegistrada,
        boolean umbralesCumplidos,
        LocalDateTime fechaEvaluacion 
) {
    public static EvaluacionClimaticaResponseDTO desde(com.obrasviales.ms_operaciones.modelo.EvaluacionClimatica eval) {
        return new EvaluacionClimaticaResponseDTO(
                eval.getId(),
                eval.getHumedadSueloRegistrada(),
                eval.getProbPrecipitacionRegistrada(),
                eval.getTemperaturaRegistrada(),
                eval.getUmbralesCumplidos(),
                eval.getFechaEvaluacion() // Ajustá al getter real de fecha de tu entidad si se llama distinto
        );
    }
}