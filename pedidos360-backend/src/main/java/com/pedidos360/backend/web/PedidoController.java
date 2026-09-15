package com.pedidos360.backend.web;

import com.pedidos360.backend.dto.EstadoRequest;
import com.pedidos360.backend.dto.PedidoRequest;
import com.pedidos360.backend.dto.PedidoResponse;
import com.pedidos360.backend.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PedidoResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PedidoResponse> obtener(@PathVariable Long id) {
        PedidoResponse pedido = service.obtenerPorId(id);
        return pedido != null ? ResponseEntity.ok(pedido) : ResponseEntity.notFound().build();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PEDIDOS_ADMIN','PEDIDOS_VENDEDOR')")
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody PedidoRequest request) {
        PedidoResponse creado = service.crear(request, usuarioActual());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('PEDIDOS_ADMIN')")
    public ResponseEntity<PedidoResponse> cambiarEstado(@PathVariable Long id,
            @Valid @RequestBody EstadoRequest request) {
        boolean actualizado = service.cambiarEstado(id, request.estado());
        if (!actualizado) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PEDIDOS_ADMIN')")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        boolean cancelado = service.cancelar(id);
        return cancelado ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getSubject();
        }
        return auth != null ? auth.getName() : "desconocido";
    }
}