package com.vanrides.vanrides.whatsapp.commands;

import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingLinks;
import com.vanrides.vanrides.whatsapp.transport.WaFoundationResponses;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit human phrases override an unrelated wizard, never infer approval from a model. */
final class WaGuidedPairingCommands {
    private static final Set<String> REQUEST=Set.of("conectar meu celular","conectar celular","conectar o celular","renovar vinculo","renovar o vinculo");
    private static final String APPROVE="conectar este celular",CANCEL="cancelar conexao";
    private final WaInboxAccess inbox;private final WaCommandStore store;private final WaSettings settings;private final WaClock clock;
    private final JdbcTemplate jdbc;private final WaConversationPolicy conversation;private final WaPairingLinks links;
    WaGuidedPairingCommands(JdbcTemplate jdbc,WaInboxAccess inbox,WaCommandStore store,WaSettings settings,WaClock clock,WaCrypto crypto) {
        this.jdbc=jdbc;this.inbox=inbox;this.store=store;this.settings=settings;this.clock=clock;
        conversation=new WaConversationPolicy(jdbc,clock,settings.environment());links=new WaPairingLinks(jdbc,settings,clock,crypto);
    }
    boolean recognizes(WaProfileState actor,WaInboxAccess.Context event) {
        String text=text(event);
        return settings.enabled() && !settings.teamApiOnly() && actor.role().equals("DRIVER") && actor.status().equals("ACTIVE")
            && actor.driverEnabled() && WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion())
            && event.normalizedKind().equals("TEXT") && conversation.freshness(event).equals("CURRENT")
            && !conversation.paused(actor.identityId()) && (REQUEST.contains(text) || APPROVE.equals(text) || CANCEL.equals(text));
    }
    WaDecision execute(WaPrincipal worker,WaProfileState actor,WaInboxAccess.Context event,String key,String hash,String correlation) {
        UUID command=UUID.randomUUID(),box=UUID.randomUUID();String text=text(event),response="";
        boolean changed=false,managed=false;
        try {
            if(REQUEST.contains(text)) {
                links.issue(actor.identityId(),event.eventId(),command,box);changed=true;managed=true;
            } else if(CANCEL.equals(text)) {
                links.cancel(actor.identityId());changed=true;response="Pedido de conexão cancelado. Para conectar um celular depois, envie Conectar meu celular.";
            } else {
                changed=links.approve(actor.identityId(),event.eventId(),event.normalizedPayload());
                links.returnLink(actor.identityId(),box);managed=true;
                if(changed) jdbc.update("UPDATE wa.profile SET version=version+1,updated_at=? WHERE identity_id=?",
                    java.sql.Timestamp.from(clock.now()),actor.identityId());
            }
        } catch(WaProblem problem) {
            response=switch(problem.code()) {
                case VERSION_CONFLICT -> "Confirme o pedido mais recente que enviei. Depois de ler essa mensagem, envie Conectar este celular novamente.";
                case ACTIVE_RUN_EXISTS -> "A localização da viagem ainda precisa ser interrompida no celular atual. Pare o compartilhamento e aguarde a confirmação antes de conectar outro aparelho.";
                case INTENT_EXPIRED -> "Este pedido de conexão venceu ou foi substituído. Envie Conectar meu celular para receber um link novo.";
                default -> "Não consegui concluir a conexão. Envie Conectar meu celular para conferir o cadastro e preparar um link novo.";
            };
        }
        WaProfileState current=store.profile(actor.identityId());
        WaDecision result=store.result(WaDecision.ok(Map.of("changed",changed)),command,List.of(box),current,
            conversation.nextStep(current,settings.enabled()),correlation);
        store.save(command,event,key,"RESPOND_TO_MESSAGE",hash,result);
        if(!managed) WaFoundationResponses.saveConversation(jdbc,box,command,actor.identityId(),response,clock.now());
        store.audit(actor.identityId(),worker,actor.role(),"RESPOND_TO_MESSAGE",command,correlation);
        inbox.revalidate(event);conversation.requireCurrent(event);
        return result;
    }
    private static String text(WaInboxAccess.Context event) { return WaAttendanceAnswers.normalize(event.normalizedPayload().path("text").asText()); }
}
