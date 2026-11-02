package com.vanrides.vanrides.whatsapp.commands;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WaConversationPolicyTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private final WaConversationPolicy policy = new WaConversationPolicy(null, new WaClock(Clock.fixed(NOW, ZoneOffset.UTC)), "test");

    @Test void closedHelpPhrasesNeverBecomeAClarificationOrSupportRequest() {
        for (String text : List.of("AJUDA", " help! ", "Menu.", "o que você faz?")) {
            var event = event(text);
            assertThat(policy.isHelp(event)).isTrue();
            assertThat(policy.replyAllowed("HELP", WaProfileState.absent(event.identityId()), event)).isTrue();
            assertThat(policy.replyAllowed("CLARIFY", WaProfileState.absent(event.identityId()), event)).isFalse();
            assertThat(policy.explicitSupport(event)).isFalse();
        }
    }
    @Test void supportAcceptsOnlyTheSameLimitedPhrasesAsTheConsumer() {
        for (String text : List.of("falar com suporte", "quero falar com suporte!", "falar com atendente",
                "quero atendimento humano", "atendimento humano?")) assertThat(policy.explicitSupport(event(text))).isTrue();
        for (String text : List.of("ajuda", "não quero falar com suporte", "falar com suporte amanhã", "quero atendimento", "falarcomsuporte"))
            assertThat(policy.explicitSupport(event(text))).isFalse();
    }
    @Test void normalizationRemovesOnlyOneFinalPunctuationAndDoesNotEraseNegation() {
        assertThat(WaConversationPolicy.normalize(" áceÍto! ")).isEqualTo("aceito");
        assertThat(WaConversationPolicy.normalize("ACEITO!!")).isEqualTo("aceito!");
        assertThat(WaConversationPolicy.normalize("não aceito!")).isEqualTo("nao aceito");
        assertThat(WaConversationPolicy.normalize("  falar   com  suporte. ")).isEqualTo("falar com suporte");
    }
    @Test void everyEcmascriptWhitespaceCharacterIsTrimmedAndCollapsedAsInNode() {
        for (int codePoint : new int[]{9, 10, 11, 12, 13, 32, 0x00A0, 0x1680, 0x2000, 0x2001, 0x2002, 0x2003,
                0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF}) {
            String space = Character.toString(codePoint);
            String text = space + "FALAR" + space + "COM" + space + "SUPORTE!" + space;
            assertThat(WaConversationPolicy.normalize(text)).isEqualTo("falar com suporte");
            assertThat(policy.explicitSupport(event(text))).isTrue();
        }
    }
    @Test void unicodeNormalizationDoesNotBroadenTheNodeWhitespaceOrCaseVocabulary() {
        for (int codePoint : new int[]{0x0085, 0x180E, 0x200B, 0x001C, 0x001D, 0x001E, 0x001F}) {
            String separator = Character.toString(codePoint);
            assertThat(policy.explicitSupport(event(separator + "FALAR" + separator + "COM SUPORTE" + separator))).isFalse();
        }
        assertThat(policy.explicitSupport(event("FALAR COM \u017FUPORTE"))).isFalse();
    }
    @Test void removingFinalPunctuationDoesNotTrimTheSpaceItExposes() {
        assertThat(WaConversationPolicy.normalize("AJUDA\u00A0!\uFEFF")).isEqualTo("ajuda ");
        assertThat(policy.isHelp(event("AJUDA\u00A0!\uFEFF"))).isFalse();
        assertThat(policy.explicitSupport(event("FALAR COM SUPORTE !"))).isFalse();
    }
    private static WaInboxAccess.Context event(String text) {
        return new WaInboxAccess.Context(UUID.randomUUID(), UUID.randomUUID(), WaJson.MAPPER.createObjectNode()
            .put("text", text).put("occurredAt", NOW.toString()), "TEXT", NOW.plusSeconds(60), NOW.plusSeconds(3600));
    }
}
