package com.pedidos360.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cliente HTTP hacia la API de gestion de RabbitMQ (plugin rabbitmq_management).
 * Se autentica con Basic auth (usuario/password del broker).
 */
@Configuration
public class RabbitMgmtConfig {

    @Bean
    public RestClient rabbitMgmtRestClient(
            @Value("${app.rabbitmq.mgmt-url}") String baseUrl,
            @Value("${app.rabbitmq.user}") String user,
            @Value("${app.rabbitmq.pass}") String pass) {

        String basic = Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }
}
