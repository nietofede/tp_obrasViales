package com.obrasviales.ms_recursos.dto;

import com.obrasviales.ms_recursos.entity.Certificacion;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Maquina;
import com.obrasviales.ms_recursos.entity.Operario;
import com.obrasviales.ms_recursos.entity.PlantaMezcla;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Contratos de entrada y salida de los catálogos de Recursos (máquinas, operarios, certificaciones, plantas y
// cuadrillas). Cada servicio define sus propios DTO: no se comparten con otros servicios ni con commons.
public final class CatalogoDtos {

    private CatalogoDtos() {
    }

    // ---------- Máquinas ----------

    public record MaquinaRequest(
            @NotBlank @Size(max = 50) String codigo,
            @NotBlank @Size(max = 100) String tipo,
            @Pattern(regexp = "DISPONIBLE|EN_MANTENIMIENTO|FUERA_DE_SERVICIO",
                    message = "El estado debe ser DISPONIBLE, EN_MANTENIMIENTO o FUERA_DE_SERVICIO") String estado,
            @Min(0) Integer horasUso) {
    }

    // enMantenimiento solo se informa cuando se consulta con una fecha (RN-05 mira mantenimientos por fecha).
    public record MaquinaResponse(Long id, String codigo, String tipo, String estado, Integer horasUso,
                                  Long cuadrillaId, Boolean enMantenimiento) {
        public static MaquinaResponse desde(Maquina m, Boolean enMantenimiento) {
            return new MaquinaResponse(m.getId(), m.getCodigo(), m.getTipo(), m.getEstado(), m.getHorasUso(),
                    m.getCuadrilla() != null ? m.getCuadrilla().getId() : null, enMantenimiento);
        }
    }

    // ---------- Operarios y certificaciones ----------

    public record OperarioRequest(
            @NotBlank @Size(max = 150) String nombre,
            @Size(max = 100) String keycloakUsername,
            @Pattern(regexp = "MAQUINISTA|CAPATAZ|PEON",
                    message = "El rol operativo debe ser MAQUINISTA, CAPATAZ o PEON") String rolOperativo) {
    }

    public record CertificacionRequest(
            @NotBlank @Size(max = 100) String tipoMaquina,
            @NotNull @FutureOrPresent LocalDate vencimiento) {
    }

    public record CertificacionResponse(Long id, String tipoMaquina, LocalDate vencimiento) {
        public static CertificacionResponse desde(Certificacion c) {
            return new CertificacionResponse(c.getId(), c.getTipoMaquina(), c.getVencimiento());
        }
    }

    public record OperarioResponse(Long id, String nombre, String keycloakUsername, String rolOperativo,
                                   String estado, Long cuadrillaId, List<CertificacionResponse> certificaciones) {
        public static OperarioResponse desde(Operario o) {
            return new OperarioResponse(o.getId(), o.getNombre(), o.getKeycloakUsername(), o.getRolOperativo(),
                    o.getEstado(), o.getCuadrilla() != null ? o.getCuadrilla().getId() : null,
                    o.getCertificaciones().stream().map(CertificacionResponse::desde).toList());
        }
    }

    // ---------- Plantas de mezcla ----------

    public record PlantaRequest(
            @NotBlank @Size(max = 150) String nombre,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitud,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitud,
            Boolean activa) {
    }

    public record PlantaResponse(Long id, String nombre, Double latitud, Double longitud, Boolean activa) {
        public static PlantaResponse desde(PlantaMezcla p) {
            return new PlantaResponse(p.getId(), p.getNombre(), p.getLatitud(), p.getLongitud(), p.getActiva());
        }
    }

    // ---------- Cuadrillas ----------

    public record CuadrillaRequest(
            @NotBlank @Size(max = 100) String nombre,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitudBase,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitudBase,
            @NotNull List<Long> operarioIds,
            @NotNull List<Long> maquinaIds) {
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
