package com.obrasviales.ms_operaciones.clientes;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_operaciones.excepciones.ServicioNoDisponibleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Tramos de una obra para el filtro GET /ordenes?obraId=: se pide con el token del usuario, no con el de SISTEMA.
class ObrasHttpClientTramosTest {

    private static final String URL_TRAMOS = "http://obras.test/api/obras/7/tramos";

    private MockRestServiceServer obrasUsuario;
    private MockRestServiceServer obrasSistema;
    private ObrasHttpClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder sistema = RestClient.builder();
        obrasSistema = MockRestServiceServer.bindTo(sistema).build();
        RestClient.Builder usuario = RestClient.builder();
        obrasUsuario = MockRestServiceServer.bindTo(usuario).build();
        cliente = new ObrasHttpClient(sistema.build(), usuario.build(), "http://obras.test");

        Jwt jwt = Jwt.withTokenValue("token-del-planificador")
                .header("alg", "none")
                .claim("sub", "planificador")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void devuelveLosIdsDeLosTramosUsandoElTokenDelUsuario() {
        obrasUsuario.expect(requestTo(URL_TRAMOS))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-del-planificador"))
                .andRespond(withSuccess("""
                        [{"id":1,"obraId":7,"nombre":"Tramo 1","superficieM2":5000.00,"latitud":-31.2,"longitud":-64.4},
                         {"id":3,"obraId":7,"nombre":"Tramo 3","superficieM2":100.00,"latitud":-31.3,"longitud":-64.5}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(cliente.tramosDeObra(7L)).containsExactlyInAnyOrder(1L, 3L);
        obrasUsuario.verify();
        obrasSistema.verify();
    }

    @Test
    void obraSinTramosDevuelveVacio() {
        obrasUsuario.expect(requestTo(URL_TRAMOS)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(cliente.tramosDeObra(7L)).isEmpty();
    }

    @Test
    void obraInexistenteDa404() {
        obrasUsuario.expect(requestTo(URL_TRAMOS)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(RecursoNoEncontradoException.class, () -> cliente.tramosDeObra(7L));
    }

    @Test
    void obrasCaidoDa503() {
        obrasUsuario.expect(requestTo(URL_TRAMOS)).andRespond(withServerError());

        assertThrows(ServicioNoDisponibleException.class, () -> cliente.tramosDeObra(7L));
    }

    @Test
    void sinUsuarioAutenticadoNoLlamaAObras() {
        SecurityContextHolder.clearContext();

        assertThrows(IllegalStateException.class, () -> cliente.tramosDeObra(7L));
        obrasUsuario.verify();
    }
}
