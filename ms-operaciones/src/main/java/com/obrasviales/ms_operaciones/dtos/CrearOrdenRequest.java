package com.obrasviales.ms_operaciones.dtos;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CrearOrdenRequest {

    @NotNull(message = "El ID de la fase es obligatorio")
    private Long faseId;

    @NotNull(message = "El ID del tramo es obligatorio")
    private Long tramoId;

    @NotNull(message = "La fecha programada es obligatoria")
    @FutureOrPresent(message = "Esta fecha ya pasó")
    private LocalDate fechaProgramada;
}