package com.pedidos360.bff;

import com.pedidos360.bff.service.BffRestClientService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityTestConfig.class)
class BffSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BffRestClientService proxy;

    @Test
    void sinToken_noSePuedeConsumirElBff() throws Exception {
        mvc.perform(get("/bff/pedidos"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(proxy);
    }

    @Test
    void tokenInvalido_noSePuedeConsumirElBff() throws Exception {
        mvc.perform(get("/bff/pedidos").header("Authorization", "Bearer token-manipulado"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(proxy);
    }

    @Test
    void tokenAudienciaIncorrecta_esRechazado() throws Exception {
        String token = TestJwt.tokenAudienciaIncorrecta("atacante", List.of("PEDIDOS_ADMIN"));
        mvc.perform(get("/bff/pedidos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(proxy);
    }

    @Test
    void tokenValido_reenviaAlBackendConElMismoToken() throws Exception {
        String token = TestJwt.token("admin1", List.of("PEDIDOS_ADMIN"));

        when(proxy.forward(anyString(), any(HttpMethod.class), anyString(), isNull()))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));

        mvc.perform(get("/bff/pedidos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bearer = ArgumentCaptor.forClass(String.class);
        verify(proxy).forward(path.capture(), any(HttpMethod.class), bearer.capture(), isNull());

        assertThat(path.getValue()).isEqualTo("/api/pedidos");
        assertThat(bearer.getValue()).isEqualTo("Bearer " + token);
    }

    @Test
    void health_noExigeToken() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk());

        verify(proxy, never()).forward(any(), any(), any(), any());
    }
}