package com.obrasviales.ms_obras.dto;

import com.obrasviales.ms_obras.domain.Obra;

public record ObraResponse(Long id, String nombre, String descripcion) {

    public static ObraResponse desde(Obra obra) {
        return new ObraResponse(obra.getId(), obra.getNombre(), obra.getDescripcion());
    }
}