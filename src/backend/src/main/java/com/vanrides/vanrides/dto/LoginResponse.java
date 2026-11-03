package com.vanrides.vanrides.dto;

public class LoginResponse {

    private String tipoUsuario;
    private String mensagem;
    private Long id; // ✅ Novo campo
    private Long motoristaId; // Id do motorista do passageiro (opcional)
    private String companyLinkStatus;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresInSeconds;

    // Construtor vazio
    public LoginResponse() {}

    // Construtor completo
    public LoginResponse(String tipoUsuario, String mensagem, Long id) {
        this.tipoUsuario = tipoUsuario;
        this.mensagem = mensagem;
        this.id = id;
    }

    // Construtor com motoristaId (para login de passageiro)
    public LoginResponse(String tipoUsuario, String mensagem, Long id, Long motoristaId) {
        this.tipoUsuario = tipoUsuario;
        this.mensagem = mensagem;
        this.id = id;
        this.motoristaId = motoristaId;
    }

    // Getters e Setters
    public String getTipoUsuario() {
        return tipoUsuario;
    }

    public void setTipoUsuario(String tipoUsuario) {
        this.tipoUsuario = tipoUsuario;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMotoristaId() {
        return motoristaId;
    }

    public void setMotoristaId(Long motoristaId) {
        this.motoristaId = motoristaId;
    }

    public String getCompanyLinkStatus() {
        return companyLinkStatus;
    }

    public void setCompanyLinkStatus(String companyLinkStatus) {
        this.companyLinkStatus = companyLinkStatus;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(Long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
