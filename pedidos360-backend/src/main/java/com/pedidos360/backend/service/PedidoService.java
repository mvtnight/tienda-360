package com.pedidos360.backend.service;

import com.pedidos360.backend.dto.PedidoRequest;
import com.pedidos360.backend.dto.PedidoResponse;
import com.pedidos360.backend.model.EstadoPedido;
import com.pedidos360.backend.model.Pedido;
import com.pedidos360.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository repository;

    public PedidoService(PedidoRepository repository) {
        this.repository = repository;
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
        return toResponse(repository.save(pedido));
    }

    @Transactional
    public boolean cancelar(Long id) {
        return repository.findById(id).map(pedido -> {
            if (pedido.getEstado() != EstadoPedido.CANCELADO) {
                pedido.setEstado(EstadoPedido.CANCELADO);
                repository.save(pedido);
            }
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean cambiarEstado(Long id, EstadoPedido estado) {
        return repository.findById(id).map(pedido -> {
            pedido.setEstado(estado);
            repository.save(pedido);
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