package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountAvatarStorageServiceTest {

    @TempDir
    Path uploadRoot;

    @Test
    void shouldStoreSupportedAvatarAndReturnPublicUrl() throws Exception {
        AccountAvatarStorageService service = new AccountAvatarStorageService(uploadRoot.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        String publicUrl = service.store(UserRole.PASSENGER, 21L, file);

        assertThat(publicUrl).startsWith("/uploads/avatars/passenger-21-");
        assertThat(publicUrl).endsWith(".png");
        Path stored = uploadRoot.resolve(publicUrl.replaceFirst("^/uploads/", ""));
        assertThat(Files.exists(stored)).isTrue();
    }

    @Test
    void shouldRejectUnsupportedAvatar() {
        AccountAvatarStorageService service = new AccountAvatarStorageService(uploadRoot.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.gif",
                "image/gif",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.store(UserRole.PASSENGER, 21L, file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
