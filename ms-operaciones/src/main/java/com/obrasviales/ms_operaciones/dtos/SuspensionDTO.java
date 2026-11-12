package com.obrasviales.ms_operaciones.dtos;

import com.obrasviales.ms_operaciones.modelo.MotivoSuspension;
import com.obrasviales.ms_operaciones.modelo.Suspension;

import java.time.LocalDateTime;

public record SuspensionDTO(Long id, Long ordenTrabajoId, Long faseId, MotivoSuspension motivo,
                            String detalle, boolean vigente, LocalDateTime fecha) {

    public static SuspensionDTO desde(Suspension s) {
        return new SuspensionDTO(s.getId(), s.getOrdenTrabajoId(), s.getFaseId(), s.getMotivo(),
                s.getDetalle(), s.isVigente(), s.getFecha());
    }
}
