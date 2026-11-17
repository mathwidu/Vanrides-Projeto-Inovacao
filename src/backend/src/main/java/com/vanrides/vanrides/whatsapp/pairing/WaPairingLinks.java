package com.vanrides.vanrides.whatsapp.pairing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.vanrides.vanrides.whatsapp.pairing.WaPairingLinkStore.ts;

/** A link can propose a key; only a subsequent, correlated WhatsApp message approves it. */
@Service
public class WaPairingLinks {
    private final JdbcTemplate jdbc; private final WaSettings settings; private final WaClock clock; private final WaCrypto crypto;
    private final WaPairingLinkStore links; private final WaPairingRepository pairings; private final WaPairingService sessions;
    private final WaPairingLinkDelivery notices;
    public WaPairingLinks(JdbcTemplate jdbc,WaSettings settings,WaClock clock,WaCrypto crypto) {
        this.jdbc=jdbc;this.settings=settings;this.clock=clock;this.crypto=crypto;
        links=new WaPairingLinkStore(jdbc,settings);pairings=new WaPairingRepository(jdbc);
        sessions=new WaPairingService(jdbc,settings,clock,crypto);notices=new WaPairingLinkDelivery(jdbc,clock,settings);
    }
    @Transactional public ObjectNode issue(UUID driver,UUID inbox,UUID command,UUID box) {
        enabled(); long generation=links.driver(driver);
        if(settings.startLinkBaseUrl().isEmpty()) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        String instance=links.instance(inbox,driver);links.replacePending(driver);
        UUID id=UUID.randomUUID();String token=WaCrypto.randomToken();
        Instant now=clock.now(),until=now.plusSeconds(300).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("""
            INSERT INTO wa.pairing_handoff(id,driver_id,environment,instance_id,source_inbox_id,source_command_id,
              token_hash,generation,created_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?)
            """,id,driver,settings.environment(),instance,inbox,command,crypto.secretHash("pairing-handoff",token),generation,ts(now),ts(until));
        notices.save(id,box,"ISSUE","Vamos conectar seu celular para enviar a localização durante as viagens. "
            +"Abra o link e toque em Conectar este celular. Nenhuma localização é ligada nessa etapa.",token);
        return WaJson.object("{}").put("changed",true);
    }
    @Transactional public ObjectNode preview(WaDpopVerifier.Proof proof,UUID id,ObjectNode body) {
        WaJson.fields(body,"linkToken");var link=owned(id,body);long generation=links.driver(link.driver());
        link=links.find(id,true);links.requireLive(link,generation,clock.now());requireClaimOwner(link,proof);
        boolean same=links.sameDevice(link.driver(),proof.jkt());
        if(!same && links.activeTrip(link.driver())) throw new WaProblem(WaProblem.Code.ACTIVE_RUN_EXISTS);
        return WaJson.object("{}").put("linkId",id.toString()).put("state",links.state(link))
            .put("expiresAt",link.expires().toString()).put("serverTime",clock.now().toString())
            .put("sameCurrentDevice",same).put("replacesDevice",links.replacesDevice(link.driver(),proof.jkt()));
    }
    @Transactional public ObjectNode claim(WaDpopVerifier.Proof proof,UUID id,ObjectNode body,String key) {
        enabled();WaJson.fields(body,"linkToken","publicJwk","pkceChallenge","pkceMethod","platform","appVersion");
        if(key==null || !key.matches("[A-Za-z0-9._:-]{8,128}")) throw WaProblem.invalid();
        var material=WaDpopVerifier.publicMaterial(body.get("publicJwk"));
        String challenge=WaJson.text(body,"pkceChallenge",43,43);WaDpopVerifier.canonical32(challenge);
        String platform=WaJson.text(body,"platform",3,7),version=WaJson.text(body,"appVersion",1,40);
        if(!proof.jkt().equals(material.jkt()) || !"S256".equals(WaJson.text(body,"pkceMethod",4,4))
                || !Set.of("ANDROID","IOS").contains(platform) || !version.matches("[A-Za-z0-9.+_-]+")) throw WaProblem.invalid();
        var link=owned(id,body);long generation=links.driver(link.driver());link=links.find(id,true);
        links.requireLive(link,generation,clock.now());requireClaimOwner(link,proof);
        String hash=WaCrypto.digest(WaJson.canonical(body));
        if(link.pairing()!=null) {
            if(!WaCrypto.same(link.claimHash(),hash)) throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
            return status(link);
        }
        if(links.activeTrip(link.driver())) throw new WaProblem(WaProblem.Code.ACTIVE_RUN_EXISTS);
        if(pairings.hasForeignActiveKey(proof.jkt(),link.driver()) || pairings.hasForeignEnvironment(link.driver(),settings.environment()))
            throw WaProblem.denied();
        UUID pairing=UUID.randomUUID();Instant now=clock.now();
        if(!pairings.createPairing(pairing,material.json(),material.jkt(),challenge,platform,version,settings.environment(),
                crypto.secretHash("pairing-code",WaCrypto.randomToken()),now,link.expires()))
            throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        UUID review=UUID.randomUUID();
        jdbc.update("UPDATE wa.pairing_handoff SET pairing_id=?,claim_key=?,claim_hash=?,state='CLAIMED' WHERE id=?",
            pairing,key,hash,id);
        notices.save(id,review,"REVIEW","Recebi o pedido para conectar um celular "+(platform.equals("IOS")?"iPhone":"Android")+". "
            +(links.replacesDevice(link.driver(),proof.jkt())?"Ele substituirá o aparelho anterior. ":"")
            +"Se você acabou de pedir isso no app, envie Conectar este celular. Se não foi você, envie Cancelar conexão.",null);
        jdbc.update("UPDATE wa.pairing_handoff SET review_outbox_id=? WHERE id=?",review,id);
        return status(links.find(id,false));
    }
    @Transactional public boolean approve(UUID driver,UUID inbox,ObjectNode payload) {
        enabled();long generation=links.driver(driver);var link=links.current(driver);
        links.requireLive(link,generation,clock.now());
        if(link.pairing()==null || !links.instance(inbox,driver).equals(link.instance())) throw WaProblem.denied();
        if(!"CLAIMED".equals(link.state()) && !"APPROVED".equals(link.state())) throw WaProblem.denied();
        if(!com.vanrides.vanrides.whatsapp.transport.WaHumanReviewFreshness.accepted(jdbc,link.review(),link.driver(),link.instance(),inbox,payload,clock.now()))
            throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        if("APPROVED".equals(link.state())) return false;
        String hash=jdbc.queryForObject("SELECT code_hash FROM wa.pairing_challenge WHERE id=?",String.class,link.pairing());
        sessions.confirmHash(driver,hash).requireSuccess();
        jdbc.update("UPDATE wa.pairing_handoff SET state='APPROVED',approved_at=? WHERE id=?",ts(clock.now()),link.id());
        return true;
    }
    @Transactional public void returnLink(UUID driver,UUID box) {
        var link=links.current(driver);
        if(link==null || !"APPROVED".equals(link.state())) throw WaProblem.denied();
        notices.save(link.id(),box,"RETURN","Conexão autorizada. Abra Concluir no app para terminar neste celular. "
            +"Depois, volte aqui para organizar os trajetos ou começar a viagem.",null);
    }
    @Transactional public void cancel(UUID driver) { enabled();links.driver(driver);links.replacePending(driver); }
    private ObjectNode status(WaPairingLinkStore.Link link) {
        return WaJson.object("{}").put("pairingId",link.pairing().toString()).put("state",links.state(link))
            .put("expiresAt",link.expires().toString()).put("serverTime",clock.now().toString());
    }
    private WaPairingLinkStore.Link owned(UUID id,ObjectNode body) {
        String token=WaJson.text(body,"linkToken",43,43);WaDpopVerifier.canonical32(token);
        var link=links.find(id,false);
        if(link==null || !settings.environment().equals(link.environment())
                || !WaCrypto.same(link.hash(),crypto.secretHash("pairing-handoff",token))) throw WaProblem.missing();
        return link;
    }
    private void requireClaimOwner(WaPairingLinkStore.Link link,WaDpopVerifier.Proof proof) {
        if(proof==null || link.pairing()!=null && !proof.jkt().equals(pairings.pairing(link.pairing(),false).jkt()))
            throw WaProblem.denied();
    }
    private void enabled() { if(!settings.enabled()) throw WaProblem.denied(); }
}
