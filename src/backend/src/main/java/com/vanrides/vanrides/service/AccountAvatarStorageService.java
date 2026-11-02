package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountAvatarStorageService {

    public static final long MAX_AVATAR_BYTES = 512L * 1024L;

    private static final Map<String, String> EXTENSION_BY_MIME_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path uploadRoot;

    public AccountAvatarStorageService(@Value("${vanrides.uploads.dir:uploads}") String uploadRoot) {
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
    }

    public String store(UserRole role, Long userId, MultipartFile file) {
        if (role == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de usuário ausente");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Foto de perfil obrigatória.");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Foto de perfil ficou grande demais. Escolha uma imagem menor."
            );
        }

        String extension = resolveExtension(file);
        String filename = "%s-%d-%s%s".formatted(
                role.claimValue().toLowerCase(Locale.ROOT),
                userId,
                UUID.randomUUID(),
                extension
        );
        Path avatarsDir = uploadRoot.resolve("avatars");
        Path target = avatarsDir.resolve(filename).normalize();
        if (!target.startsWith(avatarsDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome de arquivo de avatar inválido.");
        }

        try {
            Files.createDirectories(avatarsDir);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível salvar a foto de perfil."
            );
        }

        return "/uploads/avatars/" + filename;
    }

    private String resolveExtension(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            String normalized = normalizeMimeType(contentType);
            String extension = EXTENSION_BY_MIME_TYPE.get(normalized);
            if (extension != null) {
                return extension;
            }
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String normalizedName = originalFilename.toLowerCase(Locale.ROOT);
            if (normalizedName.endsWith(".jpg") || normalizedName.endsWith(".jpeg")) {
                return ".jpg";
            }
            if (normalizedName.endsWith(".png")) {
                return ".png";
            }
            if (normalizedName.endsWith(".webp")) {
                return ".webp";
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Foto de perfil deve ser JPG, PNG ou WebP.");
    }

    private String normalizeMimeType(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }
}
