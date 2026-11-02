package com.vanrides.vanrides.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class CompanyAdminOpenApiController {

    @GetMapping(value = "/company-admins/openapi", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> openApi() {
        return ResponseEntity.ok(spec());
    }

    private Map<String, Object> spec() {
        Map<String, Object> document = orderedMap();
        document.put("openapi", "3.0.3");
        document.put("info", orderedMap(
                "title", "VanRides API",
                "version", "dev",
                "description", "Contrato minimo operacional para portal empresa e apps VanRides."
        ));
        document.put("paths", paths());
        document.put("components", components());
        return document;
    }

    private Map<String, Object> paths() {
        Map<String, Object> paths = orderedMap();
        paths.put("/login", orderedMap("post", operation(
                "Autenticar usuario",
                "Autentica passageiro, motorista, responsavel ou admin da empresa.",
                false
        )));
        paths.put("/auth/refresh", orderedMap("post", operation(
                "Renovar access token",
                "Renova o token curto usando refresh token ativo.",
                false
        )));
        paths.put("/account/me", orderedMap(
                "get", operation("Consultar perfil", "Retorna o perfil do usuario autenticado.", true),
                "patch", operation("Atualizar perfil", "Atualiza dados editaveis da conta autenticada.", true)
        ));
        paths.put("/company-admins/dashboard", orderedMap("get", operation(
                "Resumo operacional",
                "Retorna atalhos e indicadores principais do portal empresa.",
                true
        )));
        paths.put("/company-admins/motoristas", orderedMap(
                "get", operation("Listar motoristas", "Lista motoristas vinculados a empresa.", true),
                "post", operation("Criar motorista", "Cria motorista operacional pela empresa.", true)
        ));
        paths.put("/company-admins/passageiros", orderedMap(
                "get", operation("Listar passageiros", "Lista passageiros vinculados a empresa.", true),
                "post", operation("Criar passageiro", "Cria passageiro operacional pela empresa.", true)
        ));
        paths.put("/company-admins/servicos-recorrentes", orderedMap(
                "get", operation("Listar servicos recorrentes", "Lista servicos-base da operacao.", true),
                "post", operation("Criar servico recorrente", "Cria servico-base de operacao.", true)
        ));
        paths.put("/company-admins/viagens", orderedMap(
                "get", operation("Listar viagens", "Lista viagens do dia ou periodo filtrado.", true),
                "post", operation("Criar viagem manual", "Cria excecao operacional a partir de servico recorrente.", true)
        ));
        paths.put("/support/tickets", orderedMap(
                "get", operation("Listar tickets", "Lista chamados do usuario autenticado.", true),
                "post", operation("Abrir ticket", "Abre chamado para suporte operacional.", true)
        ));
        paths.put("/viagens/{id}/rota-eventos", orderedMap("get", operation(
                "Eventos realtime da viagem",
                "Canal de acompanhamento operacional da rota e paradas.",
                true
        )));
        return paths;
    }

    private Map<String, Object> components() {
        return orderedMap("securitySchemes", orderedMap(
                "bearerAuth", orderedMap(
                        "type", "http",
                        "scheme", "bearer",
                        "bearerFormat", "JWT"
                ),
                "basicAuth", orderedMap(
                        "type", "http",
                        "scheme", "basic"
                )
        ));
    }

    private Map<String, Object> operation(String summary, String description, boolean secured) {
        Map<String, Object> operation = orderedMap(
                "summary", summary,
                "description", description,
                "responses", orderedMap(
                        "200", orderedMap("description", "OK"),
                        "400", orderedMap("description", "Requisicao invalida"),
                        "401", orderedMap("description", "Nao autenticado")
                )
        );
        if (secured) {
            operation.put("security", List.of(orderedMap("bearerAuth", List.of())));
        }
        return operation;
    }

    @SafeVarargs
    private final <T> Map<String, T> orderedMap(Object... keyValues) {
        Map<String, T> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            @SuppressWarnings("unchecked")
            T value = (T) keyValues[i + 1];
            map.put((String) keyValues[i], value);
        }
        return map;
    }
}
