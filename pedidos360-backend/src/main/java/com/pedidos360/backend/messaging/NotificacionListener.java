package com.pedidos360.backend.messaging;

import com.pedidos360.backend.config.RabbitConfig;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio <b>Notificaciones</b> (exchange Direct).
 * Simula el envio de emails al cliente y encadena la generacion de documento.
 */
@Component
public class NotificacionListener extends AbstractEventoListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionListener.class);

    private final EventoPublisher publisher;

    public NotificacionListener(EventoPublisher publisher) {
        super("notificaciones");
        this.publisher = publisher;
    }

    @RabbitListener(queues = RabbitConfig.Q_NOTIFICACION, containerFactory = "manualAckFactory")
    public void onMessage(PedidoEvento evento, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        recibir(evento, channel, tag);
    }

    @Override
    protected void procesar(PedidoEvento evento) {
        if (esPruebaFallo(evento)) {
            throw new IllegalStateException("Fallo simulado en notificaciones");
        }
        log.info("[notificaciones] email '{}' enviado a {} (pedido #{})",
                evento.tipo(), evento.email(), evento.pedidoId());

        // Encadena el siguiente dominio: generacion de documento (Topic)
        publisher.documento("generado", PedidoEvento.de("documento.generado", evento.pedidoId(),
                evento.cliente(), evento.email(), evento.estado(), evento.total(),
                "Comprobante del pedido " + evento.pedidoId()));
    }
}
