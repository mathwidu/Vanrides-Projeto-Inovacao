package com.vanrides.vanrides.whatsapp.team;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.stereotype.Component;

/** Fixed provider destinations. A provider response, URL, body or exception never escapes this adapter. */
@Component
class WaTeamRemote {
    record Reply(int status, JsonNode body) { }

    Reply exchange(URI uri, Map<String, String> headers, String body, SSLSocketFactory tls) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) uri.toURL().openConnection(Proxy.NO_PROXY);
            if (tls != null) connection.setSSLSocketFactory(tls);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            headers.forEach(connection::setRequestProperty);
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (var out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status != 200) return new Reply(status, null);
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(262145);
                if (bytes.length == 0 || bytes.length > 262144) throw unavailable();
                return new Reply(status, WaJson.MAPPER.readTree(bytes));
            }
        } catch (IOException | RuntimeException ignored) {
            throw unavailable();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static WaProblem unavailable() { return new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE); }
}
