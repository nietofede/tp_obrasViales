package com.obrasviales.ms_recursos.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_recursos.service.CatalogoService;
import com.obrasviales.ms_recursos.service.CuadrillaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEG-01: matriz de permisos de las consultas de recursos y del check-in. Para cada endpoint se prueba sin token
// (401) y, con cada rol, que los permitidos pasan (2xx) y los demás reciben 403.
@WebMvcTest({CatalogoController.class, CuadrillaController.class})
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class CatalogoControllerSeguridadTest {

    private static final List<String> ROLES = List.of("ADMIN", "PLANIFICADOR", "CAPATAZ", "AUDITOR", "SISTEMA");

    private static final Set<String> LECTURA = Set.of("ADMIN", "PLANIFICADOR", "CAPATAZ", "AUDITOR");

    record Endpoint(String metodo, String url, String cuerpo, Set<String> permitidos) {
        @Override
        public String toString() {
            return metodo + " " + url;
        }
    }

    private static final String UBICACION = "{\"latitud\":-31.4,\"longitud\":-64.2}";

    static Stream<Endpoint> endpoints() {
        return Stream.of(
                new Endpoint("GET", "/api/recursos/maquinas", null, LECTURA),
                new Endpoint("GET", "/api/recursos/operarios", null, LECTURA),
                new Endpoint("GET", "/api/recursos/cuadrillas", null, LECTURA),
                new Endpoint("POST", "/api/recursos/cuadrillas/1/ubicaciones", UBICACION, Set.of("CAPATAZ")));
    }

    static Stream<Object[]> endpointPorRol() {
        return endpoints().flatMap(e -> ROLES.stream().map(rol -> new Object[]{e, rol}));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogoService catalogoService;

    @MockitoBean
    private CuadrillaService cuadrillaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private MockHttpServletRequestBuilder pedido(Endpoint e) {
        MockHttpServletRequestBuilder req = request(HttpMethod.valueOf(e.metodo()), e.url());
        if (e.cuerpo() != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(e.cuerpo());
        }
        return req;
    }

    @ParameterizedTest(name = "sin token: {0}")
    @MethodSource("endpoints")
    void sinToken_devuelve401(Endpoint e) throws Exception {
        mockMvc.perform(pedido(e)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} con rol {1}")
    @MethodSource("endpointPorRol")
    void cadaRolRecibeLoQueLeCorresponde(Endpoint e, String rol) throws Exception {
        ResultActions resultado = mockMvc.perform(
                pedido(e).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + rol))));

        if (e.permitidos().contains(rol)) {
            int codigo = resultado.andReturn().getResponse().getStatus();
            assertThat(codigo).as("%s con rol %s debía pasar", e, rol).isBetween(200, 299);
        } else {
            resultado.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:problem:forbidden"));
        }
    }

    @Test
    void checkInConLatitudFueraDeRango_devuelve400() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/api/recursos/cuadrillas/1/ubicaciones")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAPATAZ")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitud\":200,\"longitud\":-64}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.latitud").exists());
    }
}
