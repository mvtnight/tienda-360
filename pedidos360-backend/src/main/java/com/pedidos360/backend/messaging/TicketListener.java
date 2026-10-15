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
 * Consumidor del dominio <b>Tickets</b> (exchange Topic, rk ticket.*).
 * Ultimo eslabon de la cadena: registra el ticket de soporte.
 */
@Component
public class TicketListener extends AbstractEventoListener {

    private static final Logger log = LoggerFactory.getLogger(TicketListener.class);

    public TicketListener() {
        super("tickets");
    }

    @RabbitListener(queues = RabbitConfig.Q_TICKET, containerFactory = "manualAckFactory")
    public void onMessage(PedidoEvento evento, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        recibir(evento, channel, tag);
    }

    @Override
    protected void procesar(PedidoEvento evento) {
        if (esPruebaFallo(evento)) {
            throw new IllegalStateException("Fallo simulado en tickets");
        }
        log.info("[tickets] ticket registrado para pedido #{} ({}): {}",
                evento.pedidoId(), evento.tipo(), evento.detalle());
    }
}
