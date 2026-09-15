package com.pedidos360.bff.web;

import com.pedidos360.bff.service.BffRestClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El BFF recibe las llamadas del SPA con el access_token de Azure AD.
 * Spring Security ya las expone SOLO si el token es válido (ver SecurityConfig);
 * aquí se reenvía al API Manager / backend conservando el mismo token.
 */
@RestController
@RequestMapping("/bff")
public class BffController {

    private final BffRestClientService proxy;

    public BffController(BffRestClientService proxy) {
        this.proxy = proxy;
    }

    @GetMapping("/pedidos")
    public ResponseEntity<String> listar(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return proxy.forward("/api/pedidos", HttpMethod.GET, bearer, null);
    }

    @GetMapping("/pedidos/{id}")
    public ResponseEntity<String> obtener(@PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return proxy.forward("/api/pedidos/" + id, HttpMethod.GET, bearer, null);
    }

    @PostMapping("/pedidos")
    public ResponseEntity<String> crear(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
            @Valid @RequestBody String body) {
        return proxy.forward("/api/pedidos", HttpMethod.POST, bearer, body);
    }
}