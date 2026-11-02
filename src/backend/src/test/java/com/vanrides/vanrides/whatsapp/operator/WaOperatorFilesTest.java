package com.vanrides.vanrides.whatsapp.operator;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class WaOperatorFilesTest {
    @TempDir Path directory;

    @Test void refusesToReplaceADifferentPrivateFileAtTheReservedDestination() throws Exception {
        Path output = directory.resolve("reserved.json");
        try (WaOperatorFiles files = WaOperatorFiles.create(output)) {
            files.prepare(WaJson.MAPPER.createObjectNode().put("status", "PREPARED").put("resourceId", "synthetic-resource"));
            Path other = Files.createFile(directory.resolve("different.json"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.writeString(other, "preserve-different-private-file");
            Files.move(other, output, StandardCopyOption.REPLACE_EXISTING);
            assertThatThrownBy(files::committed).isInstanceOf(WaOperatorFailure.class);
            assertThat(Files.readString(output)).isEqualTo("preserve-different-private-file");
            try (var paths = Files.list(directory)) { assertThat(paths.count()).isEqualTo(1); }
        }
    }
}
