package com.pedidos360.admin.dto;

/** Resumen de una cola de RabbitMQ. */
public record ColaResumen(
        String nombre,
        long mensajes,
        long listos,
        long sinConfirmar,
        long consumidores,
        String estado,
        boolean esDlq) {
}
