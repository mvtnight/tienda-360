package com.pedidos360.backend.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia RabbitMQ del dominio Pedidos360.
 *
 * <p>Tres dominios de mensajeria, cada uno con su cola y su Dead Letter Queue:
 * <ul>
 *   <li><b>Notificaciones</b> (Direct): eventos pedido.creado / pedido.estado para avisar al cliente.</li>
 *   <li><b>Documentos</b> (Topic): pedido.documento.* para generacion/envio de PDFs y comprobantes.</li>
 *   <li><b>Tickets</b> (Topic): ticket.* para tickets de soporte asociados a un pedido.</li>
 * </ul>
 *
 * <p>Las colas aplican ACK manual y, cuando un mensaje no puede procesarse, se
 * enruta (NACK sin requeue) a su DLQ correspondiente a traves del dead-letter-exchange.
 */
@Configuration
@EnableRabbit
public class RabbitConfig {

    // ---- Dominio: notificaciones (Direct) ----
    public static final String EX_NOTIFICACION = "pedido.notificacion";
    public static final String EX_NOTIFICACION_DLX = "pedido.notificacion.dlx";
    public static final String Q_NOTIFICACION = "notificaciones.pedidos";
    public static final String Q_NOTIFICACION_DLQ = "notificaciones.pedidos.dlq";
    public static final String RK_NOTIFICACION = "pedido.notificacion";

    // ---- Dominio: documentos (Topic) ----
    public static final String EX_DOCUMENTO = "pedido.documento";
    public static final String EX_DOCUMENTO_DLX = "pedido.documento.dlx";
    public static final String Q_DOCUMENTO = "documentos.pedidos";
    public static final String Q_DOCUMENTO_DLQ = "documentos.pedidos.dlq";
    public static final String RK_DOCUMENTO = "pedido.documento.*";

    // ---- Dominio: tickets (Topic) ----
    public static final String EX_TICKET = "ticket";
    public static final String EX_TICKET_DLX = "ticket.dlx";
    public static final String Q_TICKET = "tickets.pedidos";
    public static final String Q_TICKET_DLQ = "tickets.pedidos.dlq";
    public static final String RK_TICKET = "ticket.*";

    // ------------------------------------------------------------------
    // Exchanges
    // ------------------------------------------------------------------
    @Bean DirectExchange exchangeNotificacion() { return new DirectExchange(EX_NOTIFICACION, true, false); }
    @Bean DirectExchange dlxNotificacion() { return new DirectExchange(EX_NOTIFICACION_DLX, true, false); }

    @Bean TopicExchange exchangeDocumento() { return new TopicExchange(EX_DOCUMENTO, true, false); }
    @Bean DirectExchange dlxDocumento() { return new DirectExchange(EX_DOCUMENTO_DLX, true, false); }

    @Bean TopicExchange exchangeTicket() { return new TopicExchange(EX_TICKET, true, false); }
    @Bean DirectExchange dlxTicket() { return new DirectExchange(EX_TICKET_DLX, true, false); }

    // ------------------------------------------------------------------
    // Colas principales (con dead-letter hacia su DLX)
    // ------------------------------------------------------------------
    @Bean
    Queue colaNotificaciones() {
        return QueueBuilder.durable(Q_NOTIFICACION)
                .deadLetterExchange(EX_NOTIFICACION_DLX)
                .deadLetterRoutingKey(Q_NOTIFICACION_DLQ)
                .build();
    }

    @Bean
    Queue colaDocumentos() {
        return QueueBuilder.durable(Q_DOCUMENTO)
                .deadLetterExchange(EX_DOCUMENTO_DLX)
                .deadLetterRoutingKey(Q_DOCUMENTO_DLQ)
                .build();
    }

    @Bean
    Queue colaTickets() {
        return QueueBuilder.durable(Q_TICKET)
                .deadLetterExchange(EX_TICKET_DLX)
                .deadLetterRoutingKey(Q_TICKET_DLQ)
                .build();
    }

    // ------------------------------------------------------------------
    // Dead Letter Queues
    // ------------------------------------------------------------------
    @Bean Queue dlqNotificaciones() { return QueueBuilder.durable(Q_NOTIFICACION_DLQ).build(); }
    @Bean Queue dlqDocumentos() { return QueueBuilder.durable(Q_DOCUMENTO_DLQ).build(); }
    @Bean Queue dlqTickets() { return QueueBuilder.durable(Q_TICKET_DLQ).build(); }

    // ------------------------------------------------------------------
    // Bindings: colas -> exchanges principales
    // ------------------------------------------------------------------
    @Bean Binding bindingNotificacion() {
        return BindingBuilder.bind(colaNotificaciones()).to(exchangeNotificacion()).with(RK_NOTIFICACION);
    }

    @Bean Binding bindingDocumento() {
        return BindingBuilder.bind(colaDocumentos()).to(exchangeDocumento()).with(RK_DOCUMENTO);
    }

    @Bean Binding bindingTicket() {
        return BindingBuilder.bind(colaTickets()).to(exchangeTicket()).with(RK_TICKET);
    }

    // ------------------------------------------------------------------
    // Bindings: DLQ -> DLX
    // ------------------------------------------------------------------
    @Bean Binding bindingDlqNotificacion() {
        return BindingBuilder.bind(dlqNotificaciones()).to(dlxNotificacion()).with(Q_NOTIFICACION_DLQ);
    }

    @Bean Binding bindingDlqDocumento() {
        return BindingBuilder.bind(dlqDocumentos()).to(dlxDocumento()).with(Q_DOCUMENTO_DLQ);
    }

    @Bean Binding bindingDlqTicket() {
        return BindingBuilder.bind(dlqTickets()).to(dlxTicket()).with(Q_TICKET_DLQ);
    }

    // ------------------------------------------------------------------
    // Infra: converter JSON, admin tolerante y factory con ACK manual
    // ------------------------------------------------------------------
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** Deja que la app arranque aunque RabbitMQ no este disponible aun. */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setIgnoreDeclarationExceptions(true);
        return admin;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory manualAckFactory(ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(false);
        return factory;
    }
}
