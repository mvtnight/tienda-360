package com.pedidos360.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record PedidoRequest(
        @NotBlank String cliente,
        String email,
        @NotEmpty List<@NotBlank String> items,
        @NotNull @DecimalMin(value = "0.01") BigDecimal total) {
}