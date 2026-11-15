package com.vanrides.vanrides.whatsapp.commands;

import com.vanrides.vanrides.whatsapp.api.WaProblem;

/** Approved synthetic-cohort copy. Not a published privacy policy or production channel approval. */
public final class WaConversationResponses {
    public static final String POLICY = "conversation-v1";
    private static final String SUPPORT = "Se precisar falar com alguém da equipe, envie FALAR COM SUPORTE.";
    private static final String GUIDANCE = "Para retomar de onde parou, envie AJUDA.";
    private static final String HELP_AND_SUPPORT = GUIDANCE + " " + SUPPORT;
    private static final String PRODUCT = "O VanRides ajuda a organizar o transporte de van pelo WhatsApp. "
        + "Quem dirige organiza dias, horários e passageiros. Quem viaja escolhe suas rotinas, informa o ponto de embarque e pode confirmar presença.";
    private static final String INVITATIONS = "Passageiro: abra o link enviado pelo seu motorista e mande a mensagem que aparecer aqui. "
        + "Se recebeu o convite em texto, envie a mensagem do convite nesta conversa. Se ainda não recebeu, peça ao seu motorista.\n"
        + "Motorista: envie aqui o convite de motorista que recebeu da equipe do VanRides.";
    private static final String DATA_USE = "Neste teste do VanRides, usamos seu nome e suas mensagens para preparar o cadastro e organizar o transporte. ";
    private static final String CONSENT = "\n\nSe você concorda em continuar o teste, responda ACEITO.\n\n" + HELP_AND_SUPPORT;
    public static final String NOTICE = DATA_USE
        + "Ao se vincular a um motorista, você informa seu ponto de embarque pelo WhatsApp." + CONSENT;
    private static final String DRIVER_NOTICE = DATA_USE
        + "O aplicativo VanRides Motorista envia sua localização durante um acompanhamento iniciado por você. "
        + "Você pode interromper o compartilhamento pelo aplicativo." + CONSENT;
    private static final String INVITE_PASSENGERS = "Com a rotina ativa, envie CONVIDAR para ver as opções. "
        + "CONVIDAR LINK gera um link que você pode compartilhar. CONVIDAR seguido do número envia o convite pelo bot "
        + "quando o passageiro já autorizou o recebimento neste WhatsApp.";
    private static final String ROUTINE = "Para criar uma rotina com os dias e horários da van, envie ROTINAS e depois CRIAR. "
        + "Com a rotina ativa, envie CONVIDAR para adicionar passageiros.";
    private static final String PAIRING = "Para compartilhar sua localização durante um trajeto, vincule o aplicativo VanRides Motorista. "
        + "Abra o aplicativo pelo ícone e envie aqui o código de pareamento exibido.";
    private static final String ATTENDANCE = "Com o vínculo com seu motorista aprovado e uma viagem disponível, envie PRESENÇA para informar se vai na ida ou na volta.";

    enum Question { PRODUCT, REGISTRATION, APP, ROUTINE, INVITE, ATTENDANCE, INVITES_ALLOWED, INVITES_STOPPED, OTHER }

    private WaConversationResponses() { }
    static Question question(String text) {
        return switch (text) {
            case "como funciona", "como funciona o vanrides", "o que e o vanrides", "o que e vanrides" -> Question.PRODUCT;
            case "como me cadastro", "como faco meu cadastro", "como faco para me cadastrar" -> Question.REGISTRATION;
            case "preciso baixar o app", "preciso baixar o aplicativo", "preciso instalar o aplicativo", "como vinculo o aplicativo" -> Question.APP;
            case "como crio uma rotina", "como faco para criar uma rotina" -> Question.ROUTINE;
            case "como convido passageiros", "como convido um passageiro", "como envio um convite" -> Question.INVITE;
            case "como confirmo minha presenca", "como aviso que vou" -> Question.ATTENDANCE;
            case "tenho uma duvida", "posso tirar uma duvida" -> Question.OTHER;
            default -> null;
        };
    }
    static String faq(Question question, String stage, String role) {
        boolean driver = role.equals("DRIVER");
        boolean registered = stage.equals("PAIRING") || stage.equals("READY");
        return switch (question) {
            case PRODUCT -> PRODUCT + "\n\n" + GUIDANCE;
            case REGISTRATION -> stage.equals("ASK_NOTICE")
                ? "O próximo passo é ler o aviso de privacidade. Envie AJUDA para receber o aviso novamente."
                : prompt(stage, role);
            case APP -> appGuidance(stage, driver) + "\n\n" + GUIDANCE;
            case ROUTINE -> (!driver ? "Quem configura os dias e horários é o motorista. "
                + "Passageiros escolhem as rotinas disponíveis depois de enviar o convite recebido do motorista."
                : registered ? ROUTINE
                : "Depois de concluir seu cadastro de motorista, você poderá criar a rotina de dias e horários e convidar passageiros.")
                + "\n\n" + GUIDANCE;
            case INVITE -> (!driver ? "Seu motorista pode compartilhar o link com você ou pedir ao bot para enviar o convite no WhatsApp. "
                + "Para permitir convites enviados pelo bot ao seu número, envie ACEITO RECEBER CONVITES."
                : registered ? INVITE_PASSENGERS
                : "Depois de concluir seu cadastro de motorista e criar uma rotina ativa, você poderá enviar convites por link ou pelo número do passageiro.")
                + "\n\n" + GUIDANCE;
            case ATTENDANCE -> (driver
                ? "Passageiros vinculados podem informar pelo WhatsApp se vão na ida ou na volta. Envie RESUMO para ver sua operação quando o cadastro estiver pronto."
                : ATTENDANCE) + "\n\n" + GUIDANCE;
            case INVITES_ALLOWED -> "Você pode receber convites pelo seu número nas próximas 24 horas. "
                + "Para interromper o recebimento, envie PARAR CONVITES.";
            case INVITES_STOPPED -> "O recebimento de convites pelo seu número foi interrompido. "
                + "Para receber novamente, envie ACEITO RECEBER CONVITES.";
            case OTHER -> "Pode perguntar! "
                + (driver ? "Posso ajudar com o cadastro, a rotina, os convites e o aplicativo."
                    : "Posso ajudar com o cadastro, o convite e a confirmação de presença.")
                + "\n\n" + HELP_AND_SUPPORT;
        };
    }
    private static String appGuidance(String stage, boolean driver) {
        if (!driver) return "Quem viaja de van usa só o WhatsApp. "
            + "O aplicativo VanRides Motorista é usado por quem dirige para compartilhar a localização durante um trajeto.";
        return switch (stage) {
            case "READY" -> "Seu aplicativo VanRides Motorista já está vinculado. Use-o para compartilhar sua localização durante um trajeto.";
            case "PAIRING" -> PAIRING;
            default -> "Depois de concluir seu cadastro, vincule o aplicativo VanRides Motorista para compartilhar a localização durante um trajeto.";
        };
    }
    static String prompt(String stage, String role) {
        return switch (stage) {
            case "WELCOME" -> PRODUCT + "\n\n" + INVITATIONS + "\n\n" + SUPPORT;
            case "ASK_NAME" -> "Como você prefere ser chamado? Envie seu nome em texto.\n\n" + SUPPORT;
            case "ASK_NOTICE" -> role.equals("DRIVER") ? DRIVER_NOTICE : NOTICE;
            case "PAIRING" -> ROUTINE + "\n\n" + PAIRING + "\n\n" + HELP_AND_SUPPORT;
            case "READY" -> (role.equals("DRIVER") ? ROUTINE
                : "Se ainda precisa se vincular a um motorista, peça a ele um convite de passageiro do VanRides e envie aqui. "
                    + ATTENDANCE) + "\n\n" + HELP_AND_SUPPORT;
            default -> "O atendimento desta conversa está pausado para suporte humano.";
        };
    }
    static String text(String code, String stage, String role) {
        return switch (code) {
            case "HELP", "CONTINUE" -> prompt(stage, role);
            case "CLARIFY" -> stage.equals("ASK_NAME")
                ? "Envie só o nome pelo qual prefere ser chamado, como Ana Silva. Use letras e espaços. Hífen e apóstrofo também são aceitos.\n\n" + GUIDANCE
                : "Não consegui identificar o que você deseja. Escreva AJUDA para ver a próxima etapa ou FALAR COM SUPORTE para atendimento.";
            case "UNSUPPORTED_CONTENT" -> "Neste teste, envie texto ou um ponto de localização estático. Não processamos áudio, imagem, arquivos ou localização ao vivo.";
            case "STATIC_PIN_REQUIRED" -> "Envie um ponto de localização estático pelo WhatsApp. A localização ao vivo e os pontos citados de outra mensagem não são usados.";
            case "FEATURE_PENDING" -> "Essa função ainda não está disponível nesta etapa do teste. Escreva AJUDA para continuar o cadastro.";
            case "STALE_MESSAGE" -> "Não foi possível confirmar a atualidade desta mensagem. Envie uma nova mensagem para continuar.";
            case "DRIVER_INVITE_REQUIRED" -> "Para começar como motorista, peça à equipe do VanRides seu convite de motorista e envie esse convite aqui.\n\n" + SUPPORT;
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
