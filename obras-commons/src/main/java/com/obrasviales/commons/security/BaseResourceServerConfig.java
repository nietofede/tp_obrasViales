package com.obrasviales.commons.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

// esto es para que todos puedan hacer lo del jwt usando esto
@Configuration
@EnableWebSecurity
@EnableMethodSecurity 
public class BaseResourceServerConfig {

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        // acá hacemos lo del conversor de roles de keycloak a spring security
        jwtConverter.setJwtGrantedAuthoritiesConverter(new KeycloakConversorRoles());
        return jwtConverter;
    }

    // configurramos tipo una cadena de filtros 
    @Bean
    public SecurityFilterChain baseSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // primero se deshabilita el csrf, porque no se usan cookies o algo raro, solo jwt
            .csrf(csrf -> csrf.disable())
            
            // Define que la API no creará ni usará sesiones HTTP en el servidor (JSESSIONID)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Reglas de autorización de endpoints:
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()  // Permite acceso libre a endpoints de monitoreo/health Check
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll() // Swagger UI y contrato OpenAPI sin token
                .anyRequest().authenticated()               // Exige token  para cualquier otra ruta 
            )
            
            // Configura el servidor como un Resource Server que valida tokens JWT firmados por Keycloak
            .oauth2ResourceServer(oauth2 -> oauth2
                // 401 y 403 del filtro de seguridad en el mismo formato que el resto de los errores
                .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint())
                .accessDeniedHandler(new ProblemDetailAccessDeniedHandler())
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }
}