package com.pedidos360.admin.web;

import com.pedidos360.admin.dto.BindingRequest;
import com.pedidos360.admin.dto.ColaRequest;
import com.pedidos360.admin.dto.ColaResumen;
import com.pedidos360.admin.dto.ExchangeRequest;
import com.pedidos360.admin.dto.MensajeResumen;
import com.pedidos360.admin.dto.PublicarEventoRequest;
import com.pedidos360.admin.dto.PublicarEventoResponse;
import com.pedidos360.admin.service.MensajeriaQueryService;
import com.pedidos360.admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Microservicio administrador de la mensajeria Pedidos360.
 *
 * <p>La creacion/eliminacion de colas, exchanges y bindings se delega en
 * {@link RabbitAdminService}; la consulta/purga/publicacion en
 * {@link MensajeriaQueryService}. Todo el recurso exige rol PEDIDOS_ADMIN
 * (JWT de Azure AD).
 */
@RestController
@RequestMapping("/admin")
@Validated
@PreAuthorize("hasRole('PEDIDOS_ADMIN')")
public class AdminController {

    private final MensajeriaQueryService query;
    private final RabbitAdminService rabbitAdmin;

    public AdminController(MensajeriaQueryService query, RabbitAdminService rabbitAdmin) {
        this.query = query;
        this.rabbitAdmin = rabbitAdmin;
    }

    // ---------- Consultas ----------

    @GetMapping("/queues")
    public List<ColaResumen> colas() {
        return query.listarColas();
    }

    @GetMapping("/dlq")
    public List<ColaResumen> dlqs() {
        return query.listarColas().stream().filter(ColaResumen::esDlq).toList();
    }

    @GetMapping("/queues/{nombre}")
    public ColaResumen cola(@PathVariable String nombre) {
        return query.obtenerCola(nombre);
    }

    @GetMapping("/queues/{nombre}/messages")
    public List<MensajeResumen> mensajes(
            @PathVariable String nombre,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int count) {
        return query.verMensajes(nombre, count);
    }

    @DeleteMapping("/queues/{nombre}/messages")
    public Map<String, String> purgar(@PathVariable String nombre) {
        query.purgar(nombre);
        return Map.of("resultado", "Cola '" + nombre + "' purgada");
    }

    @PostMapping("/eventos")
    public PublicarEventoResponse publicar(@Valid @RequestBody PublicarEventoRequest request) {
        return query.publicar(request);
    }

    // ---------- Administracion de topologia (AmqpAdmin) ----------

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> crearCola(@Valid @RequestBody ColaRequest request) {
        rabbitAdmin.crearCola(request);
        return Map.of("resultado", "Cola '" + request.queueName() + "' creada exitosamente");
    }

    @DeleteMapping("/queues/{nombre}")
    public Map<String, String> eliminarCola(@PathVariable String nombre) {
        rabbitAdmin.eliminarCola(nombre);
        return Map.of("resultado", "Cola '" + nombre + "' eliminada exitosamente");
    }

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> crearExchange(@Valid @RequestBody ExchangeRequest request) {
        rabbitAdmin.crearExchange(request);
        return Map.of("resultado", "Exchange '" + request.exchangeName() + "' creado exitosamente");
    }

    @DeleteMapping("/exchanges/{nombre}")
    public Map<String, String> eliminarExchange(@PathVariable String nombre) {
        rabbitAdmin.eliminarExchange(nombre);
        return Map.of("resultado", "Exchange '" + nombre + "' eliminado exitosamente");
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> crearBinding(@Valid @RequestBody BindingRequest request) {
        rabbitAdmin.crearBinding(request);
        return Map.of("resultado", "Binding creado exitosamente");
    }

    @DeleteMapping("/bindings")
    public Map<String, String> eliminarBinding(@Valid @RequestBody BindingRequest request) {
        rabbitAdmin.eliminarBinding(request);
        return Map.of("resultado", "Binding eliminado exitosamente");
    }
}
