package com.pedidos360.admin.service;

import com.pedidos360.admin.dto.ColaRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.stereotype.Service;

/**
 * Encapsula la manipulacion dinamica de colas en {@link AmqpAdmin}
 * (RabbitAdmin). El controlador REST solo invoca estos metodos.
 */
@Service
public class ColaAdminService {

    private static final Logger log = LoggerFactory.getLogger(ColaAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final RabbitAdminService inspector;

    public ColaAdminService(AmqpAdmin amqpAdmin, RabbitAdminService inspector) {
        this.amqpAdmin = amqpAdmin;
        this.inspector = inspector;
    }

    /** Crea (declara) una cola durable/no durable. Idempotente en RabbitMQ. */
    public void crear(ColaRequest request) {
        boolean durable = request.durable() == null || request.durable();
        boolean autoDelete = request.autoDelete() != null && request.autoDelete();
        amqpAdmin.declareQueue(new Queue(request.queueName(), durable, false, autoDelete));
        log.info("[admin] cola '{}' declarada (durable={}, autoDelete={})",
                request.queueName(), durable, autoDelete);
    }

    /** Elimina una cola. Si no existe, lanza 404 (via inspector). */
    public void eliminar(String nombre) {
        inspector.obtenerCola(nombre); // 404 controlado si no existe
        amqpAdmin.deleteQueue(nombre);
        log.info("[admin] cola '{}' eliminada", nombre);
    }
}
