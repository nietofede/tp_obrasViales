package com.obrasviales.ms_operaciones.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

// para el cierre: horas de maquina y consumo real de materiales 
public record CierreOrdenRequest(
        @NotNull(message = "Las horas de máquina son obligatorias")
        @DecimalMin(value = "0.0", message = "Las horas de máquina no pueden ser negativas")
        BigDecimal horasMaquina,
        @NotNull(message = "El consumo de materiales es obligatorio (puede ir vacío)")
        @Valid
        List<MaterialConsumido> materiales) {

    public record MaterialConsumido(
            @NotBlank(message = "El material es obligatorio")
            @Size(max = 100, message = "El nombre del material no puede superar los 100 caracteres")
            String material,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.0", message = "La cantidad no puede ser negativa")
            BigDecimal cantidad,
            @NotBlank(message = "La unidad es obligatoria")
            @Size(max = 20, message = "La unidad no puede superar los 20 caracteres")
            String unidad) {
    }
}
