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
import java.math.BigDecimal;

/**
 * Consumidor del dominio <b>Documentos</b> (exchange Topic, rk pedido.documento.*).
 * Simula la generacion del PDF/comprobante. Si el pedido supera un monto,
 * encadena la apertura de un ticket de revision.
 */
@Component
public class DocumentoListener extends AbstractEventoListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentoListener.class);
    private static final BigDecimal MONTO_REVISION = new BigDecimal("1000");

    private final EventoPublisher publisher;

    public DocumentoListener(EventoPublisher publisher) {
        super("documentos");
        this.publisher = publisher;
    }

    @RabbitListener(queues = RabbitConfig.Q_DOCUMENTO, containerFactory = "manualAckFactory")
    public void onMessage(PedidoEvento evento, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        recibir(evento, channel, tag);
    }

    @Override
    protected void procesar(PedidoEvento evento) {
        if (esPruebaFallo(evento)) {
            throw new IllegalStateException("Fallo simulado en documentos");
        }
        log.info("[documentos] PDF generado para pedido #{} (monto {})",
                evento.pedidoId(), evento.total());

        // Pedidos de alto monto generan un ticket de revision (Topic ticket.*)
        if (evento.total() != null && evento.total().compareTo(MONTO_REVISION) >= 0) {
            publisher.ticket("abierto", PedidoEvento.de("ticket.abierto", evento.pedidoId(),
                    evento.cliente(), evento.email(), evento.estado(), evento.total(),
                    "Revision por monto alto del pedido " + evento.pedidoId()));
        }
    }
}
