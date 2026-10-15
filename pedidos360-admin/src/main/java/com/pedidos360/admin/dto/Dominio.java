package com.pedidos360.admin.dto;

/** Dominios de mensajeria soportados y su enrutamiento. */
public enum Dominio {
    NOTIFICACIONES("pedido.notificacion", "pedido.notificacion", "Direct"),
    DOCUMENTOS("pedido.documento", "pedido.documento.generado", "Topic"),
    TICKETS("ticket", "ticket.abierto", "Topic");

    private final String exchange;
    private final String routingKey;
    private final String tipoExchange;

    Dominio(String exchange, String routingKey, String tipoExchange) {
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.tipoExchange = tipoExchange;
    }

    public String exchange() { return exchange; }
    public String routingKey() { return routingKey; }
    public String tipoExchange() { return tipoExchange; }
}
