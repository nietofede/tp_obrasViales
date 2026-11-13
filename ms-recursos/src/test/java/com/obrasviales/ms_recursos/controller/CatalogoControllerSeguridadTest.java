package com.obrasviales.ms_recursos.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_recursos.service.CatalogoService;
import com.obrasviales.ms_recursos.service.ConfiguracionService;
import com.obrasviales.ms_recursos.service.CuadrillaService;
import com.obrasviales.ms_recursos.service.ReservaService;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Reserva;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEG-01: matriz de permisos de los endpoints nuevos de Recursos. Para cada endpoint se prueba sin token (401) y,
// con cada uno de los seis roles, que los permitidos pasan (2xx) y los demás reciben 403.
@WebMvcTest({CatalogoController.class, CuadrillaController.class,
        ConfiguracionController.class, ReservaConsultaController.class})
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class CatalogoControllerSeguridadTest {

    private static final List<String> ROLES =
            List.of("ADMIN", "PLANIFICADOR", "CAPATAZ", "OPERARIO", "AUDITOR", "SISTEMA");

    private static final Set<String> LECTURA = Set.of("ADMIN", "PLANIFICADOR", "CAPATAZ", "AUDITOR");

    record Endpoint(String metodo, String url, String cuerpo, Set<String> permitidos) {
        @Override
        public String toString() {
            return metodo + " " + url;
        }
    }

    private static final String MAQUINA = "{\"codigo\":\"EXC-01\",\"tipo\":\"EXCAVADORA\",\"estado\":\"DISPONIBLE\"}";
    private static final String OPERARIO = "{\"nombre\":\"Ana\",\"keycloakUsername\":\"ana\",\"rolOperativo\":\"PEON\"}";
    private static final String CERTIFICACION = "{\"tipoMaquina\":\"PAVIMENTADORA\",\"vencimiento\":\"2099-01-01\"}";
    private static final String PLANTA = "{\"nombre\":\"Planta Sur\",\"latitud\":-31.4,\"longitud\":-64.2}";
    private static final String CUADRILLA =
            "{\"nombre\":\"Gamma\",\"latitudBase\":-31.4,\"longitudBase\":-64.2,\"operarioIds\":[1],\"maquinaIds\":[1]}";
    private static final String UBICACION = "{\"latitud\":-31.4,\"longitud\":-64.2}";
    private static final String COMPOSICION =
            "{\"minOperarios\":2,\"tipoMaquinaRequerida\":\"PAVIMENTADORA\",\"cantidadMaquinas\":1,\"requiereMezcla\":true}";
    private static final String DESPLAZAMIENTO =
            "{\"viajeMaximoCuadrillaMin\":90,\"duracionJornadaMin\":480,"
                    + "\"ventanaTransporteMezclaMin\":60,\"margenCamionMin\":15}";

    static Stream<Endpoint> endpoints() {
        return Stream.of(
                new Endpoint("POST", "/api/recursos/maquinas", MAQUINA, Set.of("ADMIN")),
                new Endpoint("PUT", "/api/recursos/maquinas/1", MAQUINA, Set.of("ADMIN")),
                new Endpoint("GET", "/api/recursos/maquinas", null, LECTURA),
                new Endpoint("POST", "/api/recursos/operarios", OPERARIO, Set.of("ADMIN")),
                new Endpoint("GET", "/api/recursos/operarios", null, LECTURA),
                new Endpoint("POST", "/api/recursos/operarios/1/certificaciones", CERTIFICACION, Set.of("ADMIN")),
                new Endpoint("GET", "/api/recursos/operarios/me/asignaciones", null, Set.of("OPERARIO")),
                new Endpoint("POST", "/api/recursos/plantas", PLANTA, Set.of("ADMIN")),
                new Endpoint("GET", "/api/recursos/plantas", null, Set.of("ADMIN", "PLANIFICADOR")),
                new Endpoint("POST", "/api/recursos/cuadrillas", CUADRILLA, Set.of("PLANIFICADOR")),
                new Endpoint("PUT", "/api/recursos/cuadrillas/1", CUADRILLA, Set.of("PLANIFICADOR")),
                new Endpoint("GET", "/api/recursos/cuadrillas", null, LECTURA),
                new Endpoint("GET", "/api/recursos/cuadrillas/1", null, LECTURA),
                new Endpoint("POST", "/api/recursos/cuadrillas/1/ubicaciones", UBICACION, Set.of("CAPATAZ")),
                new Endpoint("GET", "/api/recursos/tipos-trabajo/composicion-minima", null, LECTURA),
                new Endpoint("PUT", "/api/recursos/tipos-trabajo/BASE/composicion-minima", COMPOSICION,
                        Set.of("ADMIN")),
                new Endpoint("GET", "/api/recursos/configuracion/desplazamiento", null,
                        Set.of("ADMIN", "PLANIFICADOR")),
                new Endpoint("PUT", "/api/recursos/configuracion/desplazamiento", DESPLAZAMIENTO, Set.of("ADMIN")),
                new Endpoint("GET", "/api/recursos/reservas/1", null,
                        Set.of("PLANIFICADOR", "CAPATAZ", "AUDITOR", "SISTEMA")));
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
    private ConfiguracionService configuracionService;

    @MockitoBean
    private ReservaService reservaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // El service de reservas es un mock: hay que darle una reserva para que el controlador pueda armar la respuesta.
    @BeforeEach
    void reservaDeEjemplo() {
        Cuadrilla cuadrilla = new Cuadrilla();
        cuadrilla.setId(1L);
        Reserva reserva = new Reserva();
        reserva.setId(1L);
        reserva.setCuadrilla(cuadrilla);
        when(reservaService.obtener(anyLong())).thenReturn(reserva);
    }

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
    void laMatrizCubreLosSeisRoles() {
        assertThat(ROLES).hasSize(6);
        assertThat(endpoints().count()).isEqualTo(19);
    }
}
