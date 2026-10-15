package com.pedidos360.admin.dto;

import java.util.Map;

/** Mensaje leido (peek) de una cola, sin retirarlo. */
public record MensajeResumen(
        String payload,
        String exchange,
        String routingKey,
        boolean reenviado,
        Map<String, Object> propiedades) {
}
