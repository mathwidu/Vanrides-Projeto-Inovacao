package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AccountMeResponse;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAvatarServiceTest {

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private GuardianRepository guardianRepository;

    @Mock
    private CompanyAdminRepository companyAdminRepository;

    @Mock
    private AccountMeService accountMeService;

    @InjectMocks
    private AccountAvatarService accountAvatarService;

    @Test
    void shouldPersistUploadedDriverAvatarUrlAndReturnSafeAccountDto() {
        Motorista motorista = new Motorista("Bruno Motorista", "bruno@vanrides.dev", "51922222222", "hash");
        motorista.setId(31L);
        AccountMeResponse response = new AccountMeResponse(
                31L,
                "Motorista",
                "DRIVER",
                "Bruno Motorista",
                "bruno@vanrides.dev",
                "51922222222",
                null,
                "/uploads/avatars/driver-31-test.png",
                true,
                null,
                null,
                31L,
                null,
                null
        );
        when(motoristaRepository.findById(31L)).thenReturn(Optional.of(motorista));
        when(motoristaRepository.save(any(Motorista.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountMeService.resolve(UserRole.DRIVER, 31L)).thenReturn(response);

        AccountMeResponse result = accountAvatarService.updateAvatar(
                UserRole.DRIVER,
                31L,
                "/uploads/avatars/driver-31-test.png"
        );

        assertThat(result.getFotoPerfilUrl()).isEqualTo("/uploads/avatars/driver-31-test.png");
        assertThat(motorista.getFotoPerfilUrl()).isEqualTo("/uploads/avatars/driver-31-test.png");
        verify(accountMeService).resolve(UserRole.DRIVER, 31L);
    }

    @Test
    void shouldRejectInvalidAvatarUrlBeforeSaving() {
        assertThatThrownBy(() -> accountAvatarService.updateAvatar(
                UserRole.DRIVER,
                31L,
                "http://example.com/avatar.txt"
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
