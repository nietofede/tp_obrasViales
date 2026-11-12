package com.obrasviales.ms_operaciones.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;



// Parte de la respuesta de Open-Meteo que usamos
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoResponseDTO {

    private CurrentData current;
    private HourlyData hourly;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrentData {
        private String time;

        @JsonProperty("temperature_2m")
        private Double temperature2m;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HourlyData {
        private List<String> time;

        @JsonProperty("precipitation_probability")
        private List<Double> precipitationProbability;

        @JsonProperty("soil_moisture_0_to_1cm")
        private List<Double> soilMoisture0To1cm;
    }
}
