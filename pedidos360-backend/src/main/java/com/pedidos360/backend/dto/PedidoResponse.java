package com.pedidos360.backend.dto;

import com.pedidos360.backend.model.EstadoPedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoResponse(
        Long id,
        String cliente,
        String email,
        List<String> items,
        BigDecimal total,
        EstadoPedido estado,
        String creador,
        Instant createdAt) {
}