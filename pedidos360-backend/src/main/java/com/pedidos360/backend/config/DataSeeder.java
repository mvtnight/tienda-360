package com.pedidos360.backend.config;

import com.pedidos360.backend.model.Pedido;
import com.pedidos360.backend.repository.PedidoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final PedidoRepository repository;

    public DataSeeder(PedidoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        Pedido p1 = new Pedido();
        p1.setCliente("Ana Gómez");
        p1.setEmail("ana@acme.com");
        p1.setItems(List.of("Laptop Pro 16", "Teclado mecánico"));
        p1.setTotal(new BigDecimal("1899.99"));

        Pedido p2 = new Pedido();
        p2.setCliente("Carlos Ruiz");
        p2.setEmail("carlos@acme.com");
        p2.setItems(List.of("Monitor 27\" 4K"));
        p2.setTotal(new BigDecimal("429.50"));

        repository.saveAll(List.of(p1, p2));
    }
}