package com.obrasviales.commons.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SistemaRestClientConfig {

    @Bean
    public RestClient sistemaRestClient(SistemaRequestInterceptor interceptor) {
        return RestClient.builder()
                .requestInterceptor(interceptor)
                .build();
    }
}