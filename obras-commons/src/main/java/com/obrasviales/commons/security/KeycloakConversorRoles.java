package com.obrasviales.commons.security;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;


public class KeycloakConversorRoles implements Converter<Jwt, Collection<GrantedAuthority>>{
    @Override 
    @SuppressWarnings ("unchecked")
    
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // Del JWT de Keycloak, extraemos los roles del claim "realm_access" 
        // y los convertimos en una colección de GrantedAuthority para su uso en Spring Security.
        
        Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access"); // extraemos el realm access del jwt

        // si no hay o esta vacio, devolvemos lista vacia
        if (realmAccess == null || realmAccess.isEmpty()) {
            return new ArrayList<>();
        }

        // del realm access sacamos los roles 
        Collection<String> roles = (Collection<String>) realmAccess.get("roles");

        // si no hay roles devolvemos una lista vacia 
        if (roles == null) {
            return new ArrayList<>();
        }

        // Se mapea cada rol al objeto 'SimpleGrantedAuthority' agregando e prefijo ROLE_.
        // Se hace para Spring Security.
        return roles.stream()
                .map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
                .collect(Collectors.toList());
    }
}

