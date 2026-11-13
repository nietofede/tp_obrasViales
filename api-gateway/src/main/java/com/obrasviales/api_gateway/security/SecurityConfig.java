package com.obrasviales.api_gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

// El gateway autentica (valida el JWT) y aplica reglas gruesas por ruta y metodo: para cada servicio y metodo
// deja pasar la union de los roles que algun endpoint acepta. El permiso fino sigue en el @PreAuthorize de
// cada servicio. Asi un rol que no tiene nada que hacer ahi recibe 403 aunque mande un cuerpo invalido
// (antes llegaba al servicio y la validacion respondia 400 antes que la seguridad).
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String PLANIFICADOR = "PLANIFICADOR";
    private static final String CAPATAZ = "CAPATAZ";
    private static final String AUDITOR = "AUDITOR";
    private static final String ADMIN = "ADMIN";
    private static final String SISTEMA = "SISTEMA";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()

                // los endpoints internos se llaman entre servicios, sin pasar por el gateway
                .requestMatchers("/api/*/internos/**").denyAll()

                // ms-obras
                .requestMatchers(HttpMethod.GET, "/api/obras/**").hasAnyRole(PLANIFICADOR, CAPATAZ, AUDITOR)
                .requestMatchers(HttpMethod.POST, "/api/obras/**").hasAnyRole(PLANIFICADOR, CAPATAZ, SISTEMA)
                .requestMatchers(HttpMethod.PUT, "/api/obras/**").hasRole(PLANIFICADOR)
                .requestMatchers(HttpMethod.PATCH, "/api/obras/**").hasAnyRole(PLANIFICADOR, SISTEMA)

                // ms-operaciones
                .requestMatchers(HttpMethod.GET, "/api/operaciones/**").hasAnyRole(PLANIFICADOR, CAPATAZ, AUDITOR, ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/operaciones/**").hasAnyRole(PLANIFICADOR, CAPATAZ, SISTEMA)
                .requestMatchers(HttpMethod.PUT, "/api/operaciones/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.PATCH, "/api/operaciones/**").hasAnyRole(PLANIFICADOR, CAPATAZ)

                // ms-recursos: sus endpoints se estan completando (rama de Recursos); por ahora solo autenticacion
                .requestMatchers("/api/recursos/**").authenticated()

                // cualquier otra combinacion de ruta y metodo no la expone ningun servicio
                .requestMatchers("/api/**").denyAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDeRoles())));
        return http.build();
    }

    // Mismo criterio que obras-commons: los roles de realm_access.roles pasan a ROLE_<rol>.
    // Se repite aca porque el gateway no depende de commons.
    static JwtAuthenticationConverter conversorDeRoles() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(SecurityConfig::rolesDeKeycloak);
        return conversor;
    }

    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> rolesDeKeycloak(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> mapa) || !(mapa.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return ((Collection<Object>) roles).stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
                .toList();
    }
}
