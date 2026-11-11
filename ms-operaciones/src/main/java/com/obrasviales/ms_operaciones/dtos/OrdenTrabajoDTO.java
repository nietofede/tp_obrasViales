package com.obrasviales.ms_operaciones.dtos;

import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class OrdenTrabajoDTO {
    private Long id;
    private Long faseId;
    private Long tramoId;
    private LocalDate fechaProgramada;
    private EstadoOrden estado;
    private Long reservaId;
    private LocalDateTime horaSalidaCamion;
    private Integer contadorSuspensiones;
}
