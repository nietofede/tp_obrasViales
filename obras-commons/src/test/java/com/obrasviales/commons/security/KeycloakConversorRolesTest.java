package com.obrasviales.commons.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// SEG-01: los roles de Keycloak (claim realm_access.roles) se convierten en authorities ROLE_*, que es lo que
// entiende hasRole() de Spring Security.
class KeycloakConversorRolesTest {

    private final KeycloakConversorRoles conversor = new KeycloakConversorRoles();

    private Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("usuario");
        claims.forEach(builder::claim);
        if (claims.isEmpty()) {
            builder.claim("sub", "usuario");
        }
        return builder.build();
    }

    private List<String> nombres(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void convierteCadaRolEnUnaAuthorityConPrefijoRole() {
        Jwt token = jwt(Map.of("realm_access", Map.of("roles", List.of("PLANIFICADOR", "AUDITOR"))));

        assertThat(nombres(conversor.convert(token))).containsExactlyInAnyOrder("ROLE_PLANIFICADOR", "ROLE_AUDITOR");
    }

    @Test
    void sinRealmAccessDevuelveListaVacia() {
        assertThat(conversor.convert(jwt(Map.of()))).isEmpty();
    }

    @Test
    void realmAccessSinRolesDevuelveListaVacia() {
        Jwt token = jwt(Map.of("realm_access", Map.of("otra_cosa", "x")));

        assertThat(conversor.convert(token)).isEmpty();
    }

    @Test
    void realmAccessVacioDevuelveListaVacia() {
        Jwt token = jwt(Map.of("realm_access", Map.of()));

        assertThat(conversor.convert(token)).isEmpty();
    }
}
