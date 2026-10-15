package com.pedidos360.backend.messaging;

import com.pedidos360.backend.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publicador de eventos de dominio.
 *
 * <p>Es <b>fail-safe</b>: si el broker no esta disponible, se registra una
 * advertencia y se continua. Asi la cadena HTTP publica (SPA -> BFF -> Backend)
 * nunca se cae por un problema de mensajeria.
 */
@Component
public class EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventoPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(String exchange, String routingKey, PedidoEvento evento) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, evento);
            log.info("[MQ] evento='{}' exchange='{}' rk='{}' pedidoId={}",
                    evento.tipo(), exchange, routingKey, evento.pedidoId());
        } catch (Exception ex) {
            log.warn("[MQ] no se pudo publicar '{}' (exchange='{}'): {}",
                    evento.tipo(), exchange, ex.getMessage());
        }
    }

    /** Dominio notificaciones (Direct). */
    public void notificar(PedidoEvento evento) {
        publicar(RabbitConfig.EX_NOTIFICACION, RabbitConfig.RK_NOTIFICACION, evento);
    }

    /** Dominio documentos (Topic). */
    public void documento(String subRoutingKey, PedidoEvento evento) {
        publicar(RabbitConfig.EX_DOCUMENTO, "pedido.documento." + subRoutingKey, evento);
    }

    /** Dominio tickets (Topic). */
    public void ticket(String subRoutingKey, PedidoEvento evento) {
        publicar(RabbitConfig.EX_TICKET, "ticket." + subRoutingKey, evento);
    }
}
