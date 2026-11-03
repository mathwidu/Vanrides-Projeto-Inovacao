package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminMotoristaDeletionServiceTest {

    @Mock private MotoristaRepository motoristaRepository;
    @Mock private LoginIdentityService loginIdentityService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private ViagemRepository viagemRepository;

    @InjectMocks
    private CompanyAdminMotoristaService companyAdminMotoristaService;

    @Test
    void shouldDeleteDriverWhenNoTripIsInProgress() {
        Motorista motorista = new Motorista();
        motorista.setId(8L);
        motorista.setEmail("driver@vanrides.dev");

        when(motoristaRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(motorista));
        when(viagemRepository.existsByMotoristaIdAndStatusAndCompanyId(
                8L,
                ViagemStatus.IN_PROGRESS,
                66L
        )).thenReturn(false);

        companyAdminMotoristaService.deletarMotorista(66L, 8L);

        verify(loginIdentityService).requireOwnedBy("driver@vanrides.dev", UserRole.DRIVER, 8L);
        verify(emailVerificationService).revokeAccountArtifacts(UserRole.DRIVER, 8L);
        verify(motoristaRepository).delete(motorista);
        verify(motoristaRepository).flush();
    }

    @Test
    void shouldRejectDeleteWhenTripIsInProgress() {
        Motorista motorista = new Motorista();
        motorista.setId(8L);
        motorista.setEmail("driver@vanrides.dev");

        when(motoristaRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(motorista));
        when(viagemRepository.existsByMotoristaIdAndStatusAndCompanyId(
                8L,
                ViagemStatus.IN_PROGRESS,
                66L
        )).thenReturn(true);

        assertThatThrownBy(() -> companyAdminMotoristaService.deletarMotorista(66L, 8L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("viagem em andamento");

        verify(motoristaRepository, never()).delete(motorista);
    }

    @Test
    void shouldTranslateDeleteIntegrityViolationToConflict() {
        Motorista motorista = new Motorista();
        motorista.setId(8L);
        motorista.setEmail("driver@vanrides.dev");

        when(motoristaRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(motorista));
        when(viagemRepository.existsByMotoristaIdAndStatusAndCompanyId(
                8L,
                ViagemStatus.IN_PROGRESS,
                66L
        )).thenReturn(false);
        DataIntegrityViolationException violation = new DataIntegrityViolationException("fk_motorista");
        doThrow(violation)
                .when(motoristaRepository)
                .delete(motorista);
        when(loginIdentityService.hasConstraint(eq(violation), anyString()))
                .thenAnswer(invocation -> "fk_passageiro_motorista".equals(
                        invocation.getArgument(1, String.class)
                ));

        assertThatThrownBy(() -> companyAdminMotoristaService.deletarMotorista(66L, 8L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vínculos existentes");
    }
}
