package com.pedidos360.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.admin.dto.ColaResumen;
import com.pedidos360.admin.dto.Dominio;
import com.pedidos360.admin.dto.MensajeResumen;
import com.pedidos360.admin.dto.PublicarEventoRequest;
import com.pedidos360.admin.dto.PublicarEventoResponse;
import com.pedidos360.admin.web.RecursoNoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administra la mensajeria apoyandose en la API REST del plugin
 * rabbitmq_management (listar/purgar colas, espiar mensajes y publicar eventos).
 *
 * <p>Se construyen {@link URI} absolutas con los segmentos ya codificados
 * (el vhost {@code /} viaja como {@code %2F}); asi se evita que el cliente HTTP
 * vuelva a codificar el '%'.
 */
@Service
public class RabbitAdminService {

    private final RestClient client;
    private final String mgmtUrl;
    private final String vhostSeg;
    private final ObjectMapper objectMapper;

    public RabbitAdminService(RestClient rabbitMgmtRestClient,
            @Value("${app.rabbitmq.mgmt-url}") String mgmtUrl,
            @Value("${app.rabbitmq.vhost:/}") String vhost,
            ObjectMapper objectMapper) {
        this.client = rabbitMgmtRestClient;
        this.mgmtUrl = mgmtUrl.replaceAll("/+$", "");
        this.vhostSeg = URLEncoder.encode(vhost, StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    public List<ColaResumen> listarColas() {
        JsonNode arr = client.get()
                .uri(uri("/api/queues/" + vhostSeg))
                .retrieve()
                .body(JsonNode.class);
        List<ColaResumen> colas = new ArrayList<>();
        if (arr != null) {
            for (JsonNode q : arr) {
                colas.add(aResumen(q));
            }
        }
        return colas;
    }

    public ColaResumen obtenerCola(String nombre) {
        try {
            JsonNode q = client.get()
                    .uri(uri("/api/queues/" + vhostSeg + "/" + segmento(nombre)))
                    .retrieve()
                    .body(JsonNode.class);
            return aResumen(q);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new RecursoNoEncontradoException("No existe la cola '" + nombre + "'");
            }
            throw ex;
        }
    }

    public List<MensajeResumen> verMensajes(String nombre, int count) {
        Map<String, Object> body = Map.of(
                "count", count,
                "ackmode", "ack_requeue_true",
                "encoding", "auto",
                "truncate", 50000);
        JsonNode arr = client.post()
                .uri(uri("/api/queues/" + vhostSeg + "/" + segmento(nombre) + "/get"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        List<MensajeResumen> mensajes = new ArrayList<>();
        if (arr != null) {
            for (JsonNode m : arr) {
                mensajes.add(new MensajeResumen(
                        m.path("payload").asText(null),
                        m.path("exchange").asText(null),
                        m.path("routing_key").asText(null),
                        m.path("redelivered").asBoolean(false),
                        aMapa(m.path("properties"))));
            }
        }
        return mensajes;
    }

    public void purgar(String nombre) {
        try {
            client.delete()
                    .uri(uri("/api/queues/" + vhostSeg + "/" + segmento(nombre) + "/contents"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new RecursoNoEncontradoException("No existe la cola '" + nombre + "'");
            }
            throw ex;
        }
    }

    public PublicarEventoResponse publicar(PublicarEventoRequest request) {
        Dominio dominio = request.dominio();
        String detalle = request.detalle() == null ? "" : request.detalle();
        if (Boolean.TRUE.equals(request.forzarDlq())) {
            detalle = (detalle + " FORZAR_DLQ").trim();
        }

        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("tipo", request.tipo());
        evento.put("pedidoId", request.pedidoId());
        evento.put("cliente", request.cliente());
        evento.put("email", request.email());
        evento.put("estado", request.estado() == null ? "RECIBIDO" : request.estado());
        evento.put("total", request.total());
        evento.put("ocurridoEn", Instant.now().toString());
        evento.put("detalle", detalle);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(evento);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar el evento", ex);
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("properties", Map.of("content_type", "application/json"));
        cuerpo.put("routing_key", dominio.routingKey());
        cuerpo.put("payload", payload);
        cuerpo.put("payload_encoding", "string");

        JsonNode resp = client.post()
                .uri(uri("/api/exchanges/" + vhostSeg + "/" + segmento(dominio.exchange()) + "/publish"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class);

        boolean enrutado = resp != null && resp.path("routed").asBoolean(false);
        return new PublicarEventoResponse(dominio.name(), dominio.exchange(),
                dominio.tipoExchange(), dominio.routingKey(), enrutado);
    }

    private URI uri(String path) {
        return URI.create(mgmtUrl + path);
    }

    private String segmento(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private ColaResumen aResumen(JsonNode q) {
        String nombre = q.path("name").asText("");
        return new ColaResumen(
                nombre,
                q.path("messages").asLong(0),
                q.path("messages_ready").asLong(0),
                q.path("messages_unacknowledged").asLong(0),
                q.path("consumers").asLong(0),
                q.path("state").asText("unknown"),
                nombre.endsWith(".dlq"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> aMapa(JsonNode nodo) {
        if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(nodo, Map.class);
    }
}
