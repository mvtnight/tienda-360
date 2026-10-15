package com.pedidos360.admin.service;

import com.pedidos360.admin.dto.BindingRequest;
import com.pedidos360.admin.dto.ColaRequest;
import com.pedidos360.admin.dto.ExchangeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.stereotype.Service;

/**
 * Encapsula la manipulacion dinamica de la topologia RabbitMQ
 * (colas, exchanges y bindings) en {@link AmqpAdmin} (RabbitAdmin).
 *
 * <p>El controlador REST solo invoca estos metodos de alto nivel y no conoce
 * detalles internos de RabbitMQ.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final MensajeriaQueryService inspector;

    public RabbitAdminService(AmqpAdmin amqpAdmin, MensajeriaQueryService inspector) {
        this.amqpAdmin = amqpAdmin;
        this.inspector = inspector;
    }

    // ---------- Colas ----------

    /** Crea (declara) una cola durable/no durable. Idempotente en RabbitMQ. */
    public void crearCola(ColaRequest request) {
        boolean durable = request.durable() == null || request.durable();
        boolean autoDelete = request.autoDelete() != null && request.autoDelete();
        amqpAdmin.declareQueue(new Queue(request.queueName(), durable, false, autoDelete));
        log.info("[admin] cola '{}' declarada (durable={}, autoDelete={})",
                request.queueName(), durable, autoDelete);
    }

    /** Elimina una cola. Si no existe, lanza 404 (via inspector). */
    public void eliminarCola(String nombre) {
        inspector.obtenerCola(nombre); // 404 controlado si no existe
        amqpAdmin.deleteQueue(nombre);
        log.info("[admin] cola '{}' eliminada", nombre);
    }

    // ---------- Exchanges ----------

    /** Crea (declara) un exchange del tipo indicado (direct|topic|fanout|headers). */
    public void crearExchange(ExchangeRequest request) {
        boolean durable = request.durable() == null || request.durable();
        boolean autoDelete = request.autoDelete() != null && request.autoDelete();
        amqpAdmin.declareExchange(construirExchange(
                request.exchangeName(), request.type(), durable, autoDelete));
        log.info("[admin] exchange '{}' ({}) declarado", request.exchangeName(), request.type());
    }

    /** Elimina un exchange. Si no existe, lanza 404. */
    public void eliminarExchange(String nombre) {
        if (!inspector.existeExchange(nombre)) {
            throw new com.pedidos360.admin.web.RecursoNoEncontradoException(
                    "No existe el exchange '" + nombre + "'");
        }
        amqpAdmin.deleteExchange(nombre);
        log.info("[admin] exchange '{}' eliminado", nombre);
    }

    // ---------- Bindings ----------

    /** Vincula una cola a un exchange con una routing key. */
    public void crearBinding(BindingRequest request) {
        amqpAdmin.declareBinding(new Binding(
                request.queueName(),
                Binding.DestinationType.QUEUE,
                request.exchangeName(),
                request.routingKey(),
                null));
        log.info("[admin] binding {}-[{}]->{} declarado",
                request.exchangeName(), request.routingKey(), request.queueName());
    }

    /** Desvincula una cola de un exchange. */
    public void eliminarBinding(BindingRequest request) {
        amqpAdmin.removeBinding(new Binding(
                request.queueName(),
                Binding.DestinationType.QUEUE,
                request.exchangeName(),
                request.routingKey(),
                null));
        log.info("[admin] binding {}-[{}]->{} eliminado",
                request.exchangeName(), request.routingKey(), request.queueName());
    }

    private Exchange construirExchange(String nombre, String tipo, boolean durable, boolean autoDelete) {
        return switch (tipo.toLowerCase()) {
            case "topic" -> new TopicExchange(nombre, durable, autoDelete);
            case "fanout" -> new FanoutExchange(nombre, durable, autoDelete);
            case "headers" -> new HeadersExchange(nombre, durable, autoDelete);
            default -> new DirectExchange(nombre, durable, autoDelete);
        };
    }
}
