package com.obrasviales.ms_operaciones.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;



@Getter
@Setter
public class OpenMeteoResponseDTO {

    @JsonProperty("current")
    private CurrentData current;

    @Getter
    @Setter
    public static class CurrentData {
        // tienen que estar en ingles porque es la respuesta de la api
        @JsonProperty("temperature_2m")
        private Double temperature2m;

        @JsonProperty("relative_humidity_2m")
        private Double relativeHumidity2m;

        @JsonProperty("precipitation_probability")
        private Double precipitationProbability;
    }
}