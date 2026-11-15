package com.vanrides.vanrides.whatsapp.team;

import static org.mockito.Mockito.*;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** Uses the real Evolution adapter with an entirely in-process provider response. */
public final class WaTeamEvolutionTestSupport {
    private static final URI ORIGIN = URI.create("https://provider.example.invalid");
    private static final String KEY = "synthetic-evolution-key";
    private static final Map<String, String> HEADERS = Map.of("apikey", KEY, "Content-Type", "application/json");
    private final WaTeamSettings settings = mock(WaTeamSettings.class);
    private final WaTeamRemote remote = mock(WaTeamRemote.class);
    private final URI endpoint;
    private final String request;

    public WaTeamEvolutionTestSupport(String instance, boolean strict, String digits, String confirmedPn) {
        endpoint = ORIGIN.resolve("/chat/whatsappNumbers/" + instance);
        request = WaJson.write(Map.of("numbers", List.of(digits)));
        when(settings.enabled()).thenReturn(true);
        when(settings.strictIngress(instance)).thenReturn(strict);
        when(settings.instance()).thenReturn(instance);
        when(settings.evolutionOrigin()).thenReturn(ORIGIN);
        when(settings.evolutionKey()).thenReturn(KEY);
        var unavailable = new WaTeamRemote.Reply(503, null);
        var payload = WaJson.MAPPER.createArrayNode();
        payload.addObject().put("exists", true).put("number", digits).put("jid", confirmedPn);
        when(remote.exchange(endpoint, HEADERS, request, null))
            .thenReturn(confirmedPn == null ? unavailable : new WaTeamRemote.Reply(200, payload));
        when(remote.exchange(ORIGIN.resolve("/instance/fetchInstances?instanceName=" + instance), Map.of("apikey", KEY), null, null))
            .thenReturn(unavailable);
    }

    public WaTeamSettings settings() { return settings; }
    public WaTeamEvolution evolution() { return new WaTeamEvolution(settings, remote); }

    public void assertLookupCount(int calls) {
        verify(remote, times(calls)).exchange(endpoint, HEADERS, request, null);
    }
}
