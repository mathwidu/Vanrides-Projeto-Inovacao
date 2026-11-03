package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.config.CorsConfig;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.MotoristaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MotoristaController.class)
@Import(CorsConfig.class)
class CorsPreflightWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MotoristaService motoristaService;

    @MockBean
    private AuthorizationService authorizationService;

    // Necessário porque o FilterAuthBasic é carregado no contexto WebMvc.
    @MockBean
    private PassageiroRepository passageiroRepository;

    @MockBean
    private MotoristaRepository motoristaRepository;

    @MockBean
    private JwtService jwtService;

    @Test
    void shouldReturnCorsHeadersOnMotoristasPreflight() throws Exception {
        mockMvc.perform(options("/motoristas")
                        .header("Origin", "https://app.exemplo")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().string("Access-Control-Allow-Origin", not(isEmptyOrNullString())))
                .andExpect(header().exists("Access-Control-Allow-Methods"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")));
    }
}
