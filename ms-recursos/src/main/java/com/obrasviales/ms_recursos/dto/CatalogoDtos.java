package com.obrasviales.ms_recursos.dto;

import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Maquina;
import com.obrasviales.ms_recursos.entity.Operario;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

// Contratos de consulta de los recursos (máquinas, operarios y cuadrillas) y del check-in del capataz.
// Los datos de los recursos se cargan con Flyway: no hay altas ni ediciones por API.
// Cada servicio define sus propios DTO: no se comparten con otros servicios ni con commons.
public final class CatalogoDtos {

    private CatalogoDtos() {
    }

    public record MaquinaResponse(Long id, String codigo, String tipo, String estado, Integer horasUso, Long cuadrillaId) {
        public static MaquinaResponse desde(Maquina m) {
            return new MaquinaResponse(m.getId(), m.getCodigo(), m.getTipo(), m.getEstado(), m.getHorasUso(),
                    m.getCuadrilla() != null ? m.getCuadrilla().getId() : null);
        }
    }

    public record OperarioResponse(Long id, String nombre, String keycloakUsername, String rolOperativo,
                                   String estado, Long cuadrillaId) {
        public static OperarioResponse desde(Operario o) {
            return new OperarioResponse(o.getId(), o.getNombre(), o.getKeycloakUsername(), o.getRolOperativo(),
                    o.getEstado(), o.getCuadrilla() != null ? o.getCuadrilla().getId() : null);
        }
    }

    public record CuadrillaResponse(Long id, String nombre, Double ultimaLatitud, Double ultimaLongitud,
                                    LocalDateTime fechaCheckin, List<Long> operarioIds, List<Long> maquinaIds) {
        public static CuadrillaResponse desde(Cuadrilla c, List<Operario> operarios, List<Maquina> maquinas) {
            return new CuadrillaResponse(c.getId(), c.getNombre(), c.getUltimaLatitud(), c.getUltimaLongitud(),
                    c.getFechaCheckin(),
                    operarios.stream().map(Operario::getId).toList(),
                    maquinas.stream().map(Maquina::getId).toList());
        }
    }

    public record UbicacionRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitud,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitud) {
    }
}
