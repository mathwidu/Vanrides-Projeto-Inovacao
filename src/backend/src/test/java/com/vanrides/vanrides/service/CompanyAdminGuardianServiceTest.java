package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminResponsavelDetailResponse;
import com.vanrides.vanrides.dto.AdminResponsavelUpdateRequest;
import com.vanrides.vanrides.dto.GuardianPasswordChangeRequest;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminGuardianServiceTest {

    private static final String BCRYPT_SHAPED_PASSWORD =
            "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u";

    @Mock private GuardianRepository guardianRepository;
    @Mock private GuardianPassageiroRepository guardianPassageiroRepository;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private LoginIdentityService loginIdentityService;

    @InjectMocks
    private CompanyAdminGuardianService service;

    @BeforeEach
    void executeIdentityWrites() {
        lenient().when(loginIdentityService.executeIdentityWrite(any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
    }

    @Test
    void shouldListCompanyGuardians() {
        Guardian guardian = guardian(71L, "Mae Portal", "mae@vanrides.dev");
        guardian.setDataNascimento(LocalDate.of(1984, 4, 18));
        guardian.setFotoPerfilUrl("/uploads/avatars/guardian-71.png");
        when(guardianRepository.findAllByCompanyIdOrderByNomeAsc(66L)).thenReturn(List.of(guardian));
        when(guardianPassageiroRepository.countByGuardianIdAndCompanyId(71L, 66L)).thenReturn(2L);

        var result = service.listarResponsaveis(66L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNome()).isEqualTo("Mae Portal");
        assertThat(result.get(0).getPassageiroCount()).isEqualTo(2L);
        assertThat(result.get(0).isAcessoConfigurado()).isFalse();
        assertThat(result.get(0).getDataNascimento()).isEqualTo(LocalDate.of(1984, 4, 18));
    }

    @Test
    void shouldReturnGuardianDetailWithLinkedPassengers() {
        Guardian guardian = guardian(72L, "Pai Detalhe", "pai@vanrides.dev");
        Passageiro passageiro = passageiro(22L, "Aluno Um");
        passageiro.setMotorista(motorista(8L, "Motorista Um"));
        GuardianPassageiro link = new GuardianPassageiro();
        link.setGuardian(guardian);
        link.setPassageiro(passageiro);
        when(guardianRepository.findByIdAndCompanyId(72L, 66L)).thenReturn(Optional.of(guardian));
        when(guardianPassageiroRepository.findAllWithPassageiroByGuardianIdAndCompanyId(72L, 66L))
                .thenReturn(List.of(link));

        AdminResponsavelDetailResponse detail = service.buscarDetalhe(72L, 66L).orElseThrow();

        assertThat(detail.getPassageiros()).hasSize(1);
        assertThat(detail.getPassageiros().get(0).getMotorista().getId()).isEqualTo(8L);
    }

    @Test
    void shouldValidateBothOwnersAndPersistPendingEmailUpdateThroughSeam() {
        Guardian guardian = verifiedGuardian(73L, "base@vanrides.dev");
        String storedHash = BCRYPT_SHAPED_PASSWORD.replace("$2a$", "$2b$");
        guardian.setSenha(storedHash);
        AdminResponsavelUpdateRequest request = updateRequest("Responsavel Atualizado", " NEW@vanrides.dev ");
        request.setSenha(null);
        when(guardianRepository.findByIdAndCompanyId(73L, 66L)).thenReturn(Optional.of(guardian));
        when(loginIdentityService.requireOwnedBy("base@vanrides.dev", UserRole.GUARDIAN, 73L))
                .thenReturn("base@vanrides.dev");
        when(loginIdentityService.requireAvailableFor(request.getEmail(), UserRole.GUARDIAN, 73L))
                .thenReturn("new@vanrides.dev");
        when(guardianRepository.saveAndFlush(guardian)).thenReturn(guardian);
        stubEmptyDetail(73L, 66L);

        AdminResponsavelDetailResponse response = service.atualizarResponsavel(73L, 66L, request);

        assertThat(response.getEmail()).isEqualTo("new@vanrides.dev");
        assertThat(guardian.getSenha()).isEqualTo(storedHash);
        assertPending(guardian);
        verify(loginIdentityService).executeIdentityWrite(any());
        verify(emailVerificationService).markPendingAndRevokeSessions(UserRole.GUARDIAN, 73L);
    }

    @Test
    void shouldKeepPendingStateInMemoryWhenPendingEmailChanges() {
        Guardian guardian = verifiedGuardian(75L, "old@vanrides.dev");
        when(guardianRepository.findById(75L)).thenReturn(Optional.of(guardian));
        when(loginIdentityService.requireOwnedBy("old@vanrides.dev", UserRole.GUARDIAN, 75L))
                .thenReturn("old@vanrides.dev");
        when(loginIdentityService.requireAvailableFor("new@vanrides.dev", UserRole.GUARDIAN, 75L))
                .thenReturn("new@vanrides.dev");
        when(guardianRepository.saveAndFlush(guardian)).thenReturn(guardian);

        service.updatePendingGuardianEmail(75L, "new@vanrides.dev");

        assertThat(guardian.getEmail()).isEqualTo("new@vanrides.dev");
        assertPending(guardian);
        verify(loginIdentityService).executeIdentityWrite(any());
    }

    @Test
    void shouldUpdateOwnPasswordForGuardian() {
        Guardian guardian = guardian(73L, "Responsavel Base", "base@vanrides.dev");
        guardian.setSenha(BCrypt.withDefaults().hashToString(12, "Atual!123".toCharArray()));
        GuardianPasswordChangeRequest request = new GuardianPasswordChangeRequest();
        request.setSenhaAtual("Atual!123");
        request.setNovaSenha("Nova!123");
        when(guardianRepository.findByIdAndCompanyId(73L, 66L)).thenReturn(Optional.of(guardian));

        service.atualizarMinhaSenha(73L, 66L, request);

        ArgumentCaptor<Guardian> captor = ArgumentCaptor.forClass(Guardian.class);
        verify(guardianRepository).save(captor.capture());
        assertThat(BCrypt.verifyer().verify("Nova!123".toCharArray(), captor.getValue().getSenha().toCharArray()).verified)
                .isTrue();
    }

    @Test
    void shouldPreserveGuardianWhenPassengersRemainLinked() {
        Guardian guardian = guardian(74L, "Mae Vinculada", "vinculada@vanrides.dev");
        when(guardianRepository.findByIdAndCompanyId(74L, 66L)).thenReturn(Optional.of(guardian));
        when(guardianPassageiroRepository.countByGuardianIdAndCompanyId(74L, 66L)).thenReturn(1L);

        assertThatThrownBy(() -> service.removerResponsavel(74L, 66L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Remova os vínculos");

        verify(loginIdentityService, never()).requireOwnedBy(any(), any(), any());
        verify(emailVerificationService, never()).revokeAccountArtifacts(any(), any());
        verify(guardianRepository, never()).delete(any());
    }

    @Test
    void shouldRevokeArtifactsBeforeDeletingOwnedUnlinkedGuardian() {
        Guardian guardian = guardian(76L, "Mae Removivel", "remove@vanrides.dev");
        when(guardianRepository.findByIdAndCompanyId(76L, 66L)).thenReturn(Optional.of(guardian));
        when(guardianPassageiroRepository.countByGuardianIdAndCompanyId(76L, 66L)).thenReturn(0L);
        when(loginIdentityService.requireOwnedBy("remove@vanrides.dev", UserRole.GUARDIAN, 76L))
                .thenReturn("remove@vanrides.dev");

        service.removerResponsavel(76L, 66L);

        InOrder order = inOrder(loginIdentityService, emailVerificationService, guardianRepository);
        order.verify(loginIdentityService).requireOwnedBy("remove@vanrides.dev", UserRole.GUARDIAN, 76L);
        order.verify(emailVerificationService).revokeAccountArtifacts(UserRole.GUARDIAN, 76L);
        order.verify(guardianRepository).delete(guardian);
        order.verify(guardianRepository).flush();
    }

    private void stubEmptyDetail(Long guardianId, Long companyId) {
        when(guardianPassageiroRepository.findAllWithPassageiroByGuardianIdAndCompanyId(guardianId, companyId))
                .thenReturn(List.of());
    }

    private AdminResponsavelUpdateRequest updateRequest(String nome, String email) {
        AdminResponsavelUpdateRequest request = new AdminResponsavelUpdateRequest();
        request.setNome(nome);
        request.setEmail(email);
        request.setTelefone("51999990073");
        request.setSenha("Nova!123");
        return request;
    }

    private Guardian verifiedGuardian(Long id, String email) {
        Guardian guardian = guardian(id, "Responsavel", email);
        guardian.setEmailVerified(true);
        guardian.setEmailVerifiedAt(OffsetDateTime.now());
        guardian.setEmailVerificationLastSentAt(OffsetDateTime.now());
        return guardian;
    }

    private void assertPending(Guardian guardian) {
        assertThat(guardian.isEmailVerified()).isFalse();
        assertThat(guardian.getEmailVerifiedAt()).isNull();
        assertThat(guardian.getEmailVerificationLastSentAt()).isNull();
    }

    private Guardian guardian(Long id, String nome, String email) {
        Guardian guardian = new Guardian();
        guardian.setNome(nome);
        guardian.setEmail(email);
        guardian.setTelefone("51999990000");
        setId(guardian, id);
        return guardian;
    }

    private Passageiro passageiro(Long id, String nome) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome(nome);
        passageiro.setEmail("passageiro@vanrides.dev");
        passageiro.setTelefone("51999990010");
        return passageiro;
    }

    private Motorista motorista(Long id, String nome) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome(nome);
        motorista.setEmail("motorista@vanrides.dev");
        motorista.setTelefone("51999990099");
        return motorista;
    }

    private void setId(Guardian guardian, Long id) {
        try {
            var field = Guardian.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(guardian, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
