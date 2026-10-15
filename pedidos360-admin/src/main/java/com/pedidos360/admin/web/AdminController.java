package com.pedidos360.admin.web;

import com.pedidos360.admin.dto.ColaRequest;
import com.pedidos360.admin.dto.ColaResumen;
import com.pedidos360.admin.dto.MensajeResumen;
import com.pedidos360.admin.dto.PublicarEventoRequest;
import com.pedidos360.admin.dto.PublicarEventoResponse;
import com.pedidos360.admin.service.ColaAdminService;
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
 * Todo el recurso exige rol PEDIDOS_ADMIN (JWT de Azure AD).
 */
@RestController
@RequestMapping("/admin")
@Validated
@PreAuthorize("hasRole('PEDIDOS_ADMIN')")
public class AdminController {

    private final RabbitAdminService service;
    private final ColaAdminService colaAdmin;

    public AdminController(RabbitAdminService service, ColaAdminService colaAdmin) {
        this.service = service;
        this.colaAdmin = colaAdmin;
    }

    @GetMapping("/queues")
    public List<ColaResumen> colas() {
        return service.listarColas();
    }

    @GetMapping("/dlq")
    public List<ColaResumen> dlqs() {
        return service.listarColas().stream().filter(ColaResumen::esDlq).toList();
    }

    @GetMapping("/queues/{nombre}")
    public ColaResumen cola(@PathVariable String nombre) {
        return service.obtenerCola(nombre);
    }

    /** Crea (declara) una cola dinamicamente via AmqpAdmin. */
    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> crearCola(@Valid @RequestBody ColaRequest request) {
        colaAdmin.crear(request);
        return Map.of("resultado", "Cola '" + request.queueName() + "' creada exitosamente");
    }

    /** Elimina una cola. */
    @DeleteMapping("/queues/{nombre}")
    public Map<String, String> eliminarCola(@PathVariable String nombre) {
        colaAdmin.eliminar(nombre);
        return Map.of("resultado", "Cola '" + nombre + "' eliminada exitosamente");
    }

    @GetMapping("/queues/{nombre}/messages")
    public List<MensajeResumen> mensajes(
            @PathVariable String nombre,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int count) {
        return service.verMensajes(nombre, count);
    }

    @DeleteMapping("/queues/{nombre}/messages")
    public Map<String, String> purgar(@PathVariable String nombre) {
        service.purgar(nombre);
        return Map.of("resultado", "Cola '" + nombre + "' purgada");
    }

    @PostMapping("/eventos")
    public PublicarEventoResponse publicar(@Valid @RequestBody PublicarEventoRequest request) {
        return service.publicar(request);
    }
}
