package com.pedidos360.admin.web;

/** Recurso de RabbitMQ inexistente (cola/exchange). */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
