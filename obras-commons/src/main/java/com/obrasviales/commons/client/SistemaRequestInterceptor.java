package com.obrasviales.commons.client;

import com.obrasviales.commons.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SistemaRequestInterceptor implements ClientHttpRequestInterceptor {

    private final SistemaTokenService tokenService;

    public SistemaRequestInterceptor(SistemaTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        
        // ponemos correlation id en el header de la peticion para logs y el microservicio
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId != null) {
            request.getHeaders().add(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
        }

        // obtenemos token del sistema y lo ponemos como bearer auth en el header de la peticion
        String token = tokenService.obtenerToken();
        request.getHeaders().setBearerAuth(token);

        return execution.execute(request, body);
    }
}
