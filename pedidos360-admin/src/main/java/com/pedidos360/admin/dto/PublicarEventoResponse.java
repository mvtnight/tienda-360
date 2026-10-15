package com.pedidos360.admin.dto;

/** Resultado de publicar un evento de prueba. */
public record PublicarEventoResponse(
        String dominio,
        String exchange,
        String tipoExchange,
        String routingKey,
        boolean enrutado) {
}
