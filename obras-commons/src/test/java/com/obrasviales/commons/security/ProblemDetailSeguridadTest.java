package com.obrasviales.commons.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

// SEG-01: el 401 y el 403 que salen del filtro de seguridad llevan el mismo cuerpo de error que el resto
// de la API. Se prueban las dos clases directamente, sin levantar Spring.
class ProblemDetailSeguridadTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/obras");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    private String campo(String ruta) throws Exception {
        return JsonPath.read(response.getContentAsString(), ruta);
    }

    // ---------- 401 ----------

    @Test
    void sinToken_devuelve401ConCuerpoEstandar() throws Exception {
        new ProblemDetailAuthenticationEntryPoint()
                .commence(request, response, new InsufficientAuthenticationException("sin token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(campo("$.type")).isEqualTo("urn:problem:unauthorized");
        assertThat(campo("$.title")).isEqualTo("No autenticado");
        assertThat((Integer) JsonPath.read(response.getContentAsString(), "$.status")).isEqualTo(401);
        assertThat(campo("$.detail")).isNotBlank();
        assertThat(campo("$.instance")).isEqualTo("/api/obras");
        assertThat(campo("$.timestamp")).isNotBlank();
    }

    @Test
    void conservaElHeaderWwwAuthenticateDelEstandarBearer() throws Exception {
        new ProblemDetailAuthenticationEntryPoint()
                .commence(request, response, new InsufficientAuthenticationException("sin token"));

        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
    }

    @Test
    void incluyeElCorrelationIdSiEstaEnElMdc() throws Exception {
        MDC.put("correlationId", "abc-123");

        new ProblemDetailAuthenticationEntryPoint()
                .commence(request, response, new InsufficientAuthenticationException("sin token"));

        assertThat(campo("$.correlationId")).isEqualTo("abc-123");
    }

    @Test
    void sinCorrelationIdNoAgregaElCampo() throws Exception {
        new ProblemDetailAuthenticationEntryPoint()
                .commence(request, response, new InsufficientAuthenticationException("sin token"));

        assertThat(response.getContentAsString()).doesNotContain("correlationId");
    }

    @Test
    void escapaLasComillasYBarrasDeLaRutaParaQueElJsonSigaSiendoValido() throws Exception {
        MockHttpServletRequest rara = new MockHttpServletRequest("GET", "/api/a\"b\\c");

        new ProblemDetailAuthenticationEntryPoint()
                .commence(rara, response, new InsufficientAuthenticationException("sin token"));

        assertThat(campo("$.instance")).isEqualTo("/api/a\"b\\c");
    }

    // ---------- 403 ----------

    @Test
    void sinPermisos_devuelve403ConCuerpoEstandar() throws Exception {
        MDC.put("correlationId", "xyz-789");

        new ProblemDetailAccessDeniedHandler().handle(request, response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(campo("$.type")).isEqualTo("urn:problem:forbidden");
        assertThat(campo("$.title")).isEqualTo("Acceso denegado");
        assertThat(campo("$.instance")).isEqualTo("/api/obras");
        assertThat(campo("$.correlationId")).isEqualTo("xyz-789");
    }
}
