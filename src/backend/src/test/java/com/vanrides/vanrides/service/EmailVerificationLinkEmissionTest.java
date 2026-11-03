package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.repository.EmailVerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationLinkEmissionTest {

    private static final String BASE_URL =
            "https://api.vanrides.test/auth/email-verifications/confirm";

    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private LoginUserDirectoryService userDirectory;
    @Mock private EmailDeliveryGateway emailDeliveryGateway;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private LoginIdentityService loginIdentityService;

    @InjectMocks private EmailVerificationService service;

    @Test
    void shouldEmitTheCredentialOnlyInTheUrlFragment() {
        Long userId = 91L;
        String email = "passenger@vanrides.test";
        LoginUserDirectoryService.LoginUserSnapshot snapshot =
                new LoginUserDirectoryService.LoginUserSnapshot(
                        userId,
                        UserRole.PASSENGER,
                        email,
                        12L,
                        false,
                        true,
                        null
                );
        ReflectionTestUtils.setField(service, "publicBaseUrl", BASE_URL);
        ReflectionTestUtils.setField(service, "tokenTtlMinutes", 60L);
        when(userDirectory.lockAccountByRoleAndId(UserRole.PASSENGER, userId))
                .thenReturn(Optional.of(snapshot));
        when(loginIdentityService.lockExactOwnerForEmailVerification(
                email,
                UserRole.PASSENGER,
                userId
        )).thenReturn(Optional.of(email));
        when(tokenRepository.findAllByUserRoleAndUserIdAndConsumedAtIsNull(
                UserRole.PASSENGER.claimValue(),
                userId
        )).thenReturn(List.of());
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);

        service.sendVerificationIfAllowed(UserRole.PASSENGER, userId, email);

        verify(emailDeliveryGateway).sendEmail(eq(email), anyString(), html.capture(), text.capture());
        Matcher link = Pattern.compile(Pattern.quote(BASE_URL) + "#token=[A-Za-z0-9._%~-]+")
                .matcher(text.getValue());
        assertThat(link.find()).isTrue();
        assertThat(text.getValue()).doesNotContain("?token=");
        assertThat(html.getValue())
                .contains("href=\"" + link.group() + "\"")
                .doesNotContain("?token=");
    }
}
