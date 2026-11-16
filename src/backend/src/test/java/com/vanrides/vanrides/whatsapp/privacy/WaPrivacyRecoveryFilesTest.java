package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs({OS.LINUX,OS.MAC})
class WaPrivacyRecoveryFilesTest {
    @TempDir Path temporary;
    private Path request(String content) throws Exception {
        Files.setPosixFilePermissions(temporary,PosixFilePermissions.fromString("rwx------"));
        Path path=temporary.resolve("prepare.request.json"); Files.writeString(path,content);
        Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------")); return path.toRealPath();
    }
    private String valid() {
        return WaJson.object("{}").put("schemaVersion",1).put("operation","PREPARE").put("environment","wa070-local")
            .put("nonce","a".repeat(32)).put("password",WaCrypto.randomToken()).put("credential",WaCrypto.randomToken()).toString();
    }
    @Test void privateBoundedRequestHasNoHumanSubjectsTargetOrClockOverrides() throws Exception {
        var file=new WaPrivacyRecoveryFiles(request(valid()));
        assertThat(file.request.operation()).isEqualTo(WaPrivacyRecoveryFiles.Operation.PREPARE);
        assertThat(file.request.toString()).isEqualTo("WaPrivacyRecoveryRequest[redacted]");
        try(var files=Files.list(temporary)) { assertThat(files.noneMatch(p->p.getFileName().toString().startsWith(".owner-probe-"))).isTrue(); }
        assertThat(Files.exists(temporary.resolve("prepare.request.json"))).isTrue();
        var body=WaJson.object(valid()).put("subjectId","human-input");
        assertThatThrownBy(()->new WaPrivacyRecoveryFiles(request(body.toString()))).isInstanceOf(RuntimeException.class);
    }
    @Test void wrongPermissionsAndSymlinkCannotOpenARequest() throws Exception {
        Path file=request(valid()); Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-r--r--"));
        assertThatThrownBy(()->new WaPrivacyRecoveryFiles(file)).isInstanceOf(IllegalStateException.class);
        Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
        Path link=temporary.resolve("linked.request.json"); Files.createSymbolicLink(link,file);
        assertThatThrownBy(()->new WaPrivacyRecoveryFiles(link)).isInstanceOf(Exception.class);
    }
    @Test void duplicateKeysOtherEnvironmentAndNonIntegralVersionAreRejected() throws Exception {
        String raw=valid().replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1");
        assertThatThrownBy(()->new WaPrivacyRecoveryFiles(request(raw))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->new WaPrivacyRecoveryFiles(request(WaJson.object(valid()).put("environment","vanrides-mvp-hml").toString())))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new WaPrivacyRecoveryFiles(request(WaJson.object(valid()).put("schemaVersion",1.0).toString())))
            .isInstanceOf(IllegalStateException.class);
    }
}
