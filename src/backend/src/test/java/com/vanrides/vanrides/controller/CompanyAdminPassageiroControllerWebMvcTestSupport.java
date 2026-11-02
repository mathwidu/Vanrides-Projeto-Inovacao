package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.CompanyAdminPassageiroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

abstract class CompanyAdminPassageiroControllerWebMvcTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected CompanyAdminPassageiroService companyAdminPassageiroService;

    @MockitoBean
    protected AuthorizationService authorizationService;

    @MockitoBean
    protected PassageiroRepository passageiroRepository;

    @MockitoBean
    protected MotoristaRepository motoristaRepository;

    @MockitoBean
    protected CompanyAdminRepository companyAdminRepository;

    protected String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
