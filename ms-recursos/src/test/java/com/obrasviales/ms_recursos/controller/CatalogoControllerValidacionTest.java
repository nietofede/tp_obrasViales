package com.obrasviales.ms_recursos.controller;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.MaquinaResponse;
import com.obrasviales.ms_recursos.service.CatalogoService;
import com.obrasviales.ms_recursos.service.ConfiguracionService;
import com.obrasviales.ms_recursos.service.CuadrillaService;
import com.obrasviales.ms_recursos.service.ReservaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// TEC-01: la entrada se valida con Bean Validation (400 con el detalle por campo) y los errores del service
// salen con el código HTTP y el formato estándar (404, 409 y 422 con codigoRegla).
@WebMvcTest({CatalogoController.class, CuadrillaController.class,
        ConfiguracionController.class, ReservaConsultaController.class})
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class CatalogoControllerValidacionTest {

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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor como(String rol) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void maquinaSinCodigo_devuelve400ConElCampo() throws Exception {
        mockMvc.perform(post("/api/recursos/maquinas").with(como("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"EXCAVADORA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.codigo").exists());
        verifyNoInteractions(catalogoService);
    }

    @Test
    void maquinaConEstadoInventado_devuelve400() throws Exception {
        mockMvc.perform(post("/api/recursos/maquinas").with(como("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"X-1\",\"tipo\":\"T\",\"estado\":\"ROTA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.estado").exists());
    }

    @Test
    void certificacionYaVencida_devuelve400() throws Exception {
        mockMvc.perform(post("/api/recursos/operarios/1/certificaciones").with(como("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoMaquina\":\"PAVIMENTADORA\",\"vencimiento\":\"2020-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.vencimiento").exists());
    }

    @Test
    void plantaConLatitudFueraDeRango_devuelve400() throws Exception {
        mockMvc.perform(post("/api/recursos/plantas").with(como("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"P\",\"latitud\":200,\"longitud\":-64}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.latitud").exists());
    }

    @Test
    void cuadrillaSinListas_devuelve400() throws Exception {
        mockMvc.perform(post("/api/recursos/cuadrillas").with(como("PLANIFICADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"G\",\"latitudBase\":-31,\"longitudBase\":-64}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.operarioIds").exists())
                .andExpect(jsonPath("$.errores.maquinaIds").exists());
    }

    @Test
    void configuracionConValoresNegativos_devuelve400() throws Exception {
        mockMvc.perform(put("/api/recursos/configuracion/desplazamiento").with(como("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"viajeMaximoCuadrillaMin\":0,\"duracionJornadaMin\":480,"
                                + "\"ventanaTransporteMezclaMin\":60,\"margenCamionMin\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.viajeMaximoCuadrillaMin").exists())
                .andExpect(jsonPath("$.errores.margenCamionMin").exists());
    }

    @Test
    void codigoDeMaquinaRepetido_devuelve409() throws Exception {
        when(catalogoService.crearMaquina(any())).thenThrow(new ConflictoEstadoException("Ya existe"));

        mockMvc.perform(post("/api/recursos/maquinas").with(como("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"PAV-01\",\"tipo\":\"PAVIMENTADORA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:conflict"));
    }

    @Test
    void cuadrillaQueIncumpleRn04_devuelve422ConElCodigo() throws Exception {
        when(cuadrillaService.crear(any())).thenThrow(new ReglaNegocioException("Sin certificación vigente", "RN-04"));

        mockMvc.perform(post("/api/recursos/cuadrillas").with(como("PLANIFICADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"G\",\"latitudBase\":-31,\"longitudBase\":-64,"
                                + "\"operarioIds\":[1],\"maquinaIds\":[1]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigoRegla").value("RN-04"));
    }

    @Test
    void reservaInexistente_devuelve404() throws Exception {
        when(reservaService.obtener(anyLong())).thenThrow(new RecursoNoEncontradoException("Reserva no encontrada: 9"));

        mockMvc.perform(get("/api/recursos/reservas/9").with(como("AUDITOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void fechaMalFormadaEnElFiltroDeMaquinas_devuelve400() throws Exception {
        mockMvc.perform(get("/api/recursos/maquinas?fecha=ayer").with(como("AUDITOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void maquinaCreada_devuelve201ConElCuerpo() throws Exception {
        when(catalogoService.crearMaquina(any()))
                .thenReturn(new MaquinaResponse(7L, "EXC-01", "EXCAVADORA", "DISPONIBLE", 0, null, null));

        mockMvc.perform(post("/api/recursos/maquinas").with(como("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"EXC-01\",\"tipo\":\"EXCAVADORA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
    }
}
