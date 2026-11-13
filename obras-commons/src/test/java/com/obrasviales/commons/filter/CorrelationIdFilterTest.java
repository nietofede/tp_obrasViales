package com.obrasviales.commons.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// INT-01: cada pedido lleva un correlation-id (el que manda el cliente o uno nuevo) que se ve en el MDC mientras
// se procesa, vuelve en la respuesta y se limpia al terminar.
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filtro = new CorrelationIdFilter();

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @Test
    void usaElIdQueManda_elClienteYLoDevuelveEnLaRespuesta() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "id-del-cliente");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idDentro = new AtomicReference<>();
        FilterChain cadena = (req, res) -> idDentro.set(MDC.get("correlationId"));

        filtro.doFilter(request, response, cadena);

        assertThat(idDentro.get()).isEqualTo("id-del-cliente");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("id-del-cliente");
    }

    @Test
    void sinHeaderGeneraUnUuidNuevo() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idDentro = new AtomicReference<>();
        FilterChain cadena = (req, res) -> idDentro.set(MDC.get("correlationId"));

        filtro.doFilter(new MockHttpServletRequest(), response, cadena);

        assertThat(idDentro.get()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(idDentro.get());
    }

    @Test
    void dosPedidosSinHeaderRecibenIdsDistintos() throws Exception {
        MockHttpServletResponse primera = new MockHttpServletResponse();
        MockHttpServletResponse segunda = new MockHttpServletResponse();
        FilterChain cadena = (req, res) -> { };

        filtro.doFilter(new MockHttpServletRequest(), primera, cadena);
        filtro.doFilter(new MockHttpServletRequest(), segunda, cadena);

        assertThat(primera.getHeader("X-Correlation-Id")).isNotEqualTo(segunda.getHeader("X-Correlation-Id"));
    }

    @Test
    void limpiaElMdcAlTerminarAunqueLaCadenaFalle() {
        FilterChain cadenaQueFalla = (req, res) -> {
            throw new IllegalStateException("falla adentro");
        };

        try {
            filtro.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), cadenaQueFalla);
        } catch (Exception esperada) {
            // el filtro no traga la excepcion
        }

        assertThat(MDC.get("correlationId")).isNull();
    }
}
