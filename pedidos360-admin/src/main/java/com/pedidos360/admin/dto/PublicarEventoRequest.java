package com.pedidos360.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Peticion para publicar un evento de prueba en RabbitMQ.
 * Todas las validaciones se aplican con Bean Validation (@Valid en el controlador).
 */
public record PublicarEventoRequest(

        @NotNull(message = "dominio es obligatorio (NOTIFICACIONES, DOCUMENTOS, TICKETS)")
        Dominio dominio,

        @NotBlank(message = "tipo es obligatorio")
        @Size(max = 60, message = "tipo no puede superar 60 caracteres")
        String tipo,

        @NotNull(message = "pedidoId es obligatorio")
        @Positive(message = "pedidoId debe ser positivo")
        Long pedidoId,

        @NotBlank(message = "cliente es obligatorio")
        @Size(max = 120, message = "cliente no puede superar 120 caracteres")
        String cliente,

        @NotBlank(message = "email es obligatorio")
        @Email(message = "email debe tener formato valido")
        @Size(max = 160, message = "email no puede superar 160 caracteres")
        String email,

        @Size(max = 30, message = "estado no puede superar 30 caracteres")
        String estado,

        @NotNull(message = "total es obligatorio")
        @DecimalMin(value = "0.0", inclusive = true, message = "total no puede ser negativo")
        BigDecimal total,

        @Size(max = 300, message = "detalle no puede superar 300 caracteres")
        String detalle,

        Boolean forzarDlq) {
}
