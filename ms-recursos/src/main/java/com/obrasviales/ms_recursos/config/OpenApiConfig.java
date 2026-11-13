package com.obrasviales.ms_recursos.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "GOVP - ms-recursos",
                version = "v1",
                description = "Catalogos de recursos (maquinas, operarios, certificaciones, cuadrillas y plantas de mezcla), "
                        + "configuracion de desplazamiento y motor de elegibilidad que propone y reserva recursos "
                        + "para las ordenes de trabajo. Los tiempos de viaje se calculan con OSRM, con datos de "
                        + "(c) OpenStreetMap contributors bajo licencia ODbL (https://www.openstreetmap.org/copyright)."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}