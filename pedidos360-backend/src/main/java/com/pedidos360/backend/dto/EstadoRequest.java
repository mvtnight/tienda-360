package com.pedidos360.backend.dto;

import com.pedidos360.backend.model.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record EstadoRequest(@NotNull EstadoPedido estado) {
}