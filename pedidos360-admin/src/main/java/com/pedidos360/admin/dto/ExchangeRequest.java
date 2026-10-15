package com.pedidos360.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Peticion para crear un exchange dinamicamente (delegado a AmqpAdmin).
 */
public record ExchangeRequest(

        @NotBlank(message = "El nombre del exchange es obligatorio.")
        @Size(min = 3, max = 50, message = "El nombre del exchange debe tener entre 3 y 50 caracteres.")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre contiene caracteres no permitidos.")
        String exchangeName,

        @NotBlank(message = "El tipo de exchange es obligatorio.")
        @Pattern(regexp = "(?i)direct|topic|fanout|headers",
                message = "Tipo invalido. Use direct, topic, fanout o headers.")
        String type,

        Boolean durable,

        Boolean autoDelete) {
}
