package com.pedidos360.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Peticion para crear una cola dinamicamente (delegado a AmqpAdmin).
 * Valida el nombre con Bean Validation antes de tocar el broker.
 */
public record ColaRequest(

        @NotBlank(message = "El nombre de la cola es obligatorio.")
        @Size(min = 3, max = 50, message = "El nombre de la cola debe tener entre 3 y 50 caracteres.")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre contiene caracteres no permitidos.")
        String queueName,

        Boolean durable,

        Boolean autoDelete) {
}
