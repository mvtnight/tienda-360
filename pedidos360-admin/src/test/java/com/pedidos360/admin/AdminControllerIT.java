package com.pedidos360.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integracion del microservicio administrador contra un RabbitMQ
 * local (management en http://localhost:15672). No forma parte de la suite
 * por defecto (sufijo IT); ejecutar con:
 *   mvnw.cmd -Dtest=AdminControllerIT test
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerIT {

    @Autowired
    private MockMvc mockMvc;

    private static final String ADMIN = "ROLE_PEDIDOS_ADMIN";

    @Test
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/admin/queues")).andExpect(status().isUnauthorized());
    }

    @Test
    void conRolAdminListaColas() throws Exception {
        mockMvc.perform(get("/admin/queues")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='notificaciones.pedidos')]").exists());
    }

    @Test
    void listaDlqs() throws Exception {
        mockMvc.perform(get("/admin/dlq")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='notificaciones.pedidos.dlq')]").exists());
    }

    @Test
    void publicaEventoValido() throws Exception {
        String body = """
                {"dominio":"NOTIFICACIONES","tipo":"pedido.creado","pedidoId":4242,
                 "cliente":"IT Test","email":"it@correo.com","total":100.00}
                """;
        mockMvc.perform(post("/admin/eventos")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrutado").value(true))
                .andExpect(jsonPath("$.exchange").value("pedido.notificacion"));
    }

    @Test
    void publicaEventoInvalidoDevuelve400() throws Exception {
        String body = """
                {"dominio":"NOTIFICACIONES","tipo":"pedido.creado","pedidoId":4242,
                 "cliente":"IT Test","email":"correo-invalido","total":-5}
                """;
        mockMvc.perform(post("/admin/eventos")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.email").exists())
                .andExpect(jsonPath("$.detalle.total").exists());
    }

    @Test
    void creaYEliminaCola() throws Exception {
        String nombre = "it.cola.prueba";
        mockMvc.perform(post("/admin/queues")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queueName\":\"" + nombre + "\",\"durable\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/admin/queues/" + nombre)
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(nombre));

        mockMvc.perform(delete("/admin/queues/" + nombre)
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void crearColaInvalidaDevuelve400() throws Exception {
        mockMvc.perform(post("/admin/queues")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queueName\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.queueName").exists());
    }

    @Test
    void eliminarColaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(delete("/admin/queues/cola.que.no.existe.xyz")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN))))
                .andExpect(status().isNotFound());
    }

    @Test
    void creaExchangeBindingYLosElimina() throws Exception {
        String cola = "it.cola.binding";
        String exchange = "it.exchange.prueba";
        String routing = "it.routing.#";

        mockMvc.perform(post("/admin/queues")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queueName\":\"" + cola + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/admin/exchanges")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchangeName\":\"" + exchange + "\",\"type\":\"topic\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/admin/bindings")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queueName\":\"" + cola + "\",\"exchangeName\":\"" + exchange
                                + "\",\"routingKey\":\"" + routing + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/admin/bindings")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queueName\":\"" + cola + "\",\"exchangeName\":\"" + exchange
                                + "\",\"routingKey\":\"" + routing + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/admin/exchanges/" + exchange)
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/admin/queues/" + cola)
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void crearExchangeTipoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/admin/exchanges")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchangeName\":\"it.exchange.malo\",\"type\":\"xyz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.type").exists());
    }

    @Test
    void eliminarExchangeInexistenteDevuelve404() throws Exception {
        mockMvc.perform(delete("/admin/exchanges/exchange.que.no.existe.xyz")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN))))
                .andExpect(status().isNotFound());
    }
}
