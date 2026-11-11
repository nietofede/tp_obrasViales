package com.obrasviales.api_gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // En el borde SIEMPRE se genera uno nuevo: se ignora el que mande el cliente.
        String id = UUID.randomUUID().toString();

        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(new ConCorrelationId(request, id), new SinHeaderDuplicado(response));
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    // El servicio de destino tambien devuelve X-Correlation-Id y el proxy lo copia:
    // sin esto la respuesta saldria con el header repetido. Aca lo reemplazamos.
    private static class SinHeaderDuplicado extends HttpServletResponseWrapper {
        SinHeaderDuplicado(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void addHeader(String name, String value) {
            if (HEADER.equalsIgnoreCase(name)) {
                super.setHeader(name, value);
            } else {
                super.addHeader(name, value);
            }
        }
    }

    // El request es de solo lectura: para reenviar nuestro id a los servicios
    // lo envolvemos y sobreescribimos lo que devuelven los metodos de headers.
    private static class ConCorrelationId extends HttpServletRequestWrapper {
        private final String id;

        ConCorrelationId(HttpServletRequest request, String id) {
            super(request);
            this.id = id;
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? id : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(Collections.singletonList(id))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> nombres = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            nombres.removeIf(HEADER::equalsIgnoreCase);
            nombres.add(HEADER);
            return Collections.enumeration(nombres);
        }
    }
}