package com.pedidos360.backend.messaging;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Base de los consumidores de dominio.
 *
 * <p>Implementa el contrato de <b>ACK manual</b>: las subclases solo definen
 * {@link #procesar(PedidoEvento)}. El mensaje se reintenta hasta {@value #MAX_INTENTOS}
 * veces; si sigue fallando se hace NACK sin requeue, lo que lo enruta a la
 * Dead Letter Queue configurada en {@code RabbitConfig}.
 */
public abstract class AbstractEventoListener {

    private static final Logger log = LoggerFactory.getLogger(AbstractEventoListener.class);
    private static final int MAX_INTENTOS = 3;

    /** Marca usada en pruebas para forzar el fallo de un mensaje y ver la DLQ. */
    protected static final String MARCA_FALLO = "FORZAR_DLQ";

    private final String dominio;

    protected AbstractEventoListener(String dominio) {
        this.dominio = dominio;
    }

    /** Logica de negocio concreta del consumidor. */
    protected abstract void procesar(PedidoEvento evento);

    /** Permite demostrar la DLQ publicando un evento cuyo detalle contiene la marca. */
    protected boolean esPruebaFallo(PedidoEvento evento) {
        return evento.detalle() != null && evento.detalle().contains(MARCA_FALLO);
    }

    protected void recibir(PedidoEvento evento, Channel channel, long tag) throws IOException {
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                procesar(evento);
                channel.basicAck(tag, false);
                return;
            } catch (Exception ex) {
                boolean ultimo = intento == MAX_INTENTOS;
                log.warn("[{}] intento {}/{} fallo para pedido #{}: {}",
                        dominio, intento, MAX_INTENTOS, evento.pedidoId(), ex.getMessage());
                if (ultimo) {
                    log.error("[{}] pedido #{} enviado a DLQ tras {} intentos",
                            dominio, evento.pedidoId(), MAX_INTENTOS);
                    channel.basicNack(tag, false, false);
                    return;
                }
                dormir(400L * intento);
            }
        }
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
