package com.obrasviales.ms_operaciones.dtos;

import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
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

    public static OrdenTrabajoDTO desde(OrdenTrabajo orden) {
        OrdenTrabajoDTO dto = new OrdenTrabajoDTO();
        dto.setId(orden.getId());
        dto.setFaseId(orden.getFaseId());
        dto.setTramoId(orden.getTramoId());
        dto.setFechaProgramada(orden.getFechaProgramada());
        dto.setEstado(orden.getEstado());
        dto.setReservaId(orden.getReservaId());
        dto.setHoraSalidaCamion(orden.getHoraSalidaCamion());
        dto.setContadorSuspensiones(orden.getContadorSuspensiones());
        return dto;
    }
}
