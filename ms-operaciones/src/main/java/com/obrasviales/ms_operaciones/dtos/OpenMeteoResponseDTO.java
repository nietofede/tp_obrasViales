package com.obrasviales.ms_operaciones.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class OpenMeteoResponseDTO {

    private CurrentData current;
    private HourlyData hourly;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CurrentData {
        private String time;

        @JsonProperty("temperature_2m")
        private Double temperature2m;

        @JsonProperty("precipitation_probability")
        private Double precipitationProbability;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class HourlyData {
        private List<String> time;

        @JsonProperty("soil_moisture_0_to_1cm")
        private List<Double> soilMoisture0To1cm;
    }
}