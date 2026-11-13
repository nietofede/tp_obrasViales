package com.obrasviales.ms_recursos.dto;

import com.obrasviales.ms_recursos.entity.ComposicionMinimaTrabajo;
import com.obrasviales.ms_recursos.entity.ConfiguracionDesplazamiento;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Contratos de la configuración que gobierna el motor de elegibilidad (RN-06, RN-07 y RN-08).
public final class ConfiguracionDtos {

    private ConfiguracionDtos() {
    }

    public record ComposicionRequest(
            @NotNull @Min(1) Integer minOperarios,
            @Size(max = 100) String tipoMaquinaRequerida,
            @NotNull @Min(0) Integer cantidadMaquinas,
            @NotNull Boolean requiereMezcla) {
    }

    public record ComposicionResponse(String tipoTrabajo, Integer minOperarios, String tipoMaquinaRequerida,
                                      Integer cantidadMaquinas, Boolean requiereMezcla) {
        public static ComposicionResponse desde(ComposicionMinimaTrabajo c) {
            return new ComposicionResponse(c.getTipoTrabajo(), c.getMinOperarios(), c.getTipoMaquinaRequerida(),
                    c.getCantidadMaquinas(), c.getRequiereMezcla());
        }
    }

    public record DesplazamientoRequest(
            @NotNull @Min(1) Integer viajeMaximoCuadrillaMin,
            @NotNull @Min(1) Integer duracionJornadaMin,
            @NotNull @Min(1) Integer ventanaTransporteMezclaMin,
            @NotNull @Min(0) Integer margenCamionMin) {
    }

    public record DesplazamientoResponse(Integer viajeMaximoCuadrillaMin, Integer duracionJornadaMin,
                                         Integer ventanaTransporteMezclaMin, Integer margenCamionMin) {
        public static DesplazamientoResponse desde(ConfiguracionDesplazamiento c) {
            return new DesplazamientoResponse(c.getViajeMaximoCuadrillaMin(), c.getDuracionJornadaMin(),
                    c.getVentanaTransporteMezclaMin(), c.getMargenCamionMin());
        }
    }
}
