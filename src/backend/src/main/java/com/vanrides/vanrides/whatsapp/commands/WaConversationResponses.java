package com.vanrides.vanrides.whatsapp.commands;

import com.vanrides.vanrides.whatsapp.api.WaProblem;

/** Approved synthetic-cohort copy. Not a published privacy policy or production channel approval. */
public final class WaConversationResponses {
    public static final String POLICY = "conversation-v1";
    public static final String NOTICE = "Neste teste do VanRides, vamos usar seu nome e suas mensagens para preparar o cadastro e organizar o transporte. "
        + "O ponto de embarque será informado por você. Para motoristas, o aplicativo envia localização durante um acompanhamento iniciado e pode ser interrompido no app. "
        + "Para pedir atendimento, escreva FALAR COM SUPORTE. Se concordar em continuar o teste, responda ACEITO.";
    private WaConversationResponses() { }
    public static String prompt(String stage) {
        return switch (stage) {
            case "WELCOME" -> "Para começar seu cadastro no teste do VanRides, envie uma mensagem em texto. Para atendimento, escreva FALAR COM SUPORTE.";
            case "ASK_NAME" -> "Como você prefere ser chamado? Envie seu nome em texto.";
            case "ASK_NOTICE" -> NOTICE;
            case "PAIRING" -> "Abra o aplicativo VanRides pelo ícone e envie aqui o código de pareamento exibido. Para atendimento, escreva FALAR COM SUPORTE.";
            case "READY" -> "Seu cadastro está preparado. As demais funções serão liberadas nas próximas etapas do teste. Para atendimento, escreva FALAR COM SUPORTE.";
            default -> "O atendimento desta conversa está pausado para suporte humano.";
        };
    }
    public static String text(String code, String stage) {
        return switch (code) {
            case "HELP", "CONTINUE" -> prompt(stage);
            case "CLARIFY" -> "Não consegui identificar o que você deseja. Escreva AJUDA para ver a próxima etapa ou FALAR COM SUPORTE para atendimento.";
            case "UNSUPPORTED_CONTENT" -> "Neste teste, envie texto ou um ponto de localização estático. Não processamos áudio, imagem, arquivos ou localização ao vivo.";
            case "STATIC_PIN_REQUIRED" -> "Envie um ponto de localização estático pelo WhatsApp. A localização ao vivo e os pontos citados de outra mensagem não são usados.";
            case "FEATURE_PENDING" -> "Essa função ainda não está disponível nesta etapa do teste. Escreva AJUDA para continuar o cadastro.";
            case "STALE_MESSAGE" -> "Não foi possível confirmar a atualidade desta mensagem. Envie uma nova mensagem para continuar.";
            case "DRIVER_INVITE_REQUIRED" -> "O acesso de motorista exige um convite de teste autorizado. Dizer que você é motorista não habilita esse acesso.";
            default -> throw new IllegalArgumentException("WA_RESPONSE_NOT_IMPLEMENTED");
        };
    }
    public static String rejected(WaProblem.Code error) {
        return switch (error) {
            case INVALID_CREDENTIAL, ACTION_NOT_ALLOWED -> "Não foi possível confirmar esta ação. Escreva AJUDA para continuar ou FALAR COM SUPORTE para atendimento.";
            case INTENT_EXPIRED -> "Esta confirmação não está mais válida. Envie uma nova mensagem e siga a orientação atual.";
            case RATE_LIMITED -> "O limite de tentativas foi atingido. Aguarde cinco minutos ou escreva FALAR COM SUPORTE.";
            default -> throw new IllegalArgumentException("WA_RESPONSE_NOT_IMPLEMENTED");
        };
    }
    public static String support() { return "Seu pedido de atendimento foi registrado. A conversa está pausada até a retomada pelo suporte humano."; }
}
