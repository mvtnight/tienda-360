package com.pedidos360.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Peticion para crear/eliminar un binding cola-exchange (delegado a AmqpAdmin).
 */
public record BindingRequest(

        @NotBlank(message = "El nombre de la cola es obligatorio.")
        @Size(min = 3, max = 50, message = "El nombre de la cola debe tener entre 3 y 50 caracteres.")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre de la cola contiene caracteres no permitidos.")
        String queueName,

        @NotBlank(message = "El nombre del exchange es obligatorio.")
        @Size(min = 3, max = 50, message = "El nombre del exchange debe tener entre 3 y 50 caracteres.")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre del exchange contiene caracteres no permitidos.")
        String exchangeName,

        @NotBlank(message = "La routing key es obligatoria.")
        @Size(max = 50, message = "La routing key no puede superar los 50 caracteres.")
        @Pattern(regexp = "^[a-zA-Z0-9._*#-]+$", message = "La routing key contiene caracteres no permitidos.")
        String routingKey) {
}
