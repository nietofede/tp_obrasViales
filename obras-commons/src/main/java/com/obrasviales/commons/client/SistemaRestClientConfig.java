package com.obrasviales.commons.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class SistemaRestClientConfig {

    @Bean
    public RestClient sistemaRestClient(SistemaRequestInterceptor interceptor) {
        // timeouts para que un servicio colgado no cuelgue al que lo llama
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .requestFactory(fabrica)
                .requestInterceptor(interceptor)
                .build();
    }
}