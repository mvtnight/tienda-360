package com.pedidos360.bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

/**
 * Encargado de reenviar las llamadas del SPA hacia el API Manager / backend,
 * propagando el MISMO token que ya fue validado por el BFF.
 */
@Service
public class BffRestClientService {

    private final RestClient restClient;

    public BffRestClientService(@Value("${bff.api-url}") String apiUrl) {
        this.restClient = RestClient.builder().baseUrl(apiUrl).build();
    }

    public ResponseEntity<String> forward(String path, HttpMethod method, String bearer, String body) {
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer);
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.exchange((request, response) -> {
                    byte[] payload = response.getBody().readAllBytes();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    return ResponseEntity.status(response.getStatusCode())
                            .headers(headers)
                            .body(new String(payload, StandardCharsets.UTF_8));
                });
    }
}