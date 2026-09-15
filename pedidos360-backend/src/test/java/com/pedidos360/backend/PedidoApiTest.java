package com.pedidos360.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityTestConfig.class)
class PedidoApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void sinToken_noPuedeListarPedidos() throws Exception {
        mvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValido_sinRol_listaPedidos() throws Exception {
        String token = TestJwt.token("cliente1", List.of("PEDIDOS_CLIENTE"));
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void tokenRolCliente_noPuedeCrearPedido() throws Exception {
        String token = TestJwt.token("cliente1", List.of("PEDIDOS_CLIENTE"));
        mvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenRolAdmin_creaPedido() throws Exception {
        String token = TestJwt.token("admin1", List.of("PEDIDOS_ADMIN"));
        mvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.estado").value("RECIBIDO"))
                .andExpect(jsonPath("$.creador").value("admin1"));
    }

    @Test
    void tokenAudienciaIncorrecta_esRechazado() throws Exception {
        String token = TestJwt.tokenAudienciaIncorrecta("atacante", List.of("PEDIDOS_ADMIN"));
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpirado_esRechazado() throws Exception {
        String token = TestJwt.tokenExpirado("cliente1");
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String validBody() {
        return """
                {"cliente":"Pedro Pérez","email":"pedro@acme.com",
                 "items":["Pizza familiar","Coca Cola 2L"],"total":35.60}
                """;
    }
}