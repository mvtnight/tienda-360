package com.pedidos360.backend.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento de dominio que viaja por RabbitMQ (serializado como JSON).
 * El campo {@code tipo} describe el hecho: pedido.creado, pedido.estado,
 * pedido.cancelado, documento.generado, ticket.abierto, etc.
 */
public record PedidoEvento(
        String tipo,
        Long pedidoId,
        String cliente,
        String email,
        String estado,
        BigDecimal total,
        Instant ocurridoEn,
        String detalle) {

    public static PedidoEvento de(String tipo, Long pedidoId, String cliente, String email,
            String estado, BigDecimal total, String detalle) {
        return new PedidoEvento(tipo, pedidoId, cliente, email, estado, total, Instant.now(), detalle);
    }
}
