package com.pedidos360.backend.service;

import com.pedidos360.backend.dto.PedidoRequest;
import com.pedidos360.backend.dto.PedidoResponse;
import com.pedidos360.backend.messaging.EventoPublisher;
import com.pedidos360.backend.messaging.PedidoEvento;
import com.pedidos360.backend.model.EstadoPedido;
import com.pedidos360.backend.model.Pedido;
import com.pedidos360.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository repository;
    private final EventoPublisher publisher;

    public PedidoService(PedidoRepository repository, EventoPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> listar() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtenerPorId(Long id) {
        return repository.findById(id).map(this::toResponse).orElse(null);
    }

    @Transactional
    public PedidoResponse crear(PedidoRequest request, String creador) {
        Pedido pedido = new Pedido();
        pedido.setCliente(request.cliente());
        pedido.setEmail(request.email());
        pedido.setItems(List.copyOf(request.items()));
        pedido.setTotal(request.total());
        pedido.setCreador(creador);
        PedidoResponse response = toResponse(repository.save(pedido));
        publisher.notificar(PedidoEvento.de("pedido.creado", response.id(), response.cliente(),
                response.email(), response.estado().name(), response.total(),
                "Pedido creado con " + response.items().size() + " item(s)"));
        return response;
    }

    @Transactional
    public boolean cancelar(Long id) {
        return repository.findById(id).map(pedido -> {
            if (pedido.getEstado() != EstadoPedido.CANCELADO) {
                pedido.setEstado(EstadoPedido.CANCELADO);
                repository.save(pedido);
                publisher.notificar(PedidoEvento.de("pedido.cancelado", pedido.getId(), pedido.getCliente(),
                        pedido.getEmail(), pedido.getEstado().name(), pedido.getTotal(),
                        "Pedido cancelado por el administrador"));
                publisher.ticket("cancelacion", PedidoEvento.de("ticket.abierto", pedido.getId(),
                        pedido.getCliente(), pedido.getEmail(), pedido.getEstado().name(), pedido.getTotal(),
                        "Ticket automatico: pedido cancelado"));
            }
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean cambiarEstado(Long id, EstadoPedido estado) {
        return repository.findById(id).map(pedido -> {
            pedido.setEstado(estado);
            repository.save(pedido);
            publisher.notificar(PedidoEvento.de("pedido.estado", pedido.getId(), pedido.getCliente(),
                    pedido.getEmail(), pedido.getEstado().name(), pedido.getTotal(),
                    "Estado actualizado a " + pedido.getEstado().name()));
            return true;
        }).orElse(false);
    }

    private PedidoResponse toResponse(Pedido p) {
        return new PedidoResponse(
                p.getId(),
                p.getCliente(),
                p.getEmail(),
                List.copyOf(p.getItems()),
                p.getTotal(),
                p.getEstado(),
                p.getCreador(),
                p.getCreatedAt());
    }
}