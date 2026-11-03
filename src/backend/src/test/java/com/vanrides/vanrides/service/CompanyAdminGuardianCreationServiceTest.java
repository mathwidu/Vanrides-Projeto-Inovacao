package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminResponsavelCreateRequest;
import com.vanrides.vanrides.dto.AdminResponsavelDetailResponse;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminGuardianCreationServiceTest {

    private static final String BCRYPT_SHAPED_PASSWORD =
            "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u";

    @Mock private GuardianRepository guardianRepository;
    @Mock private GuardianPassageiroRepository guardianPassageiroRepository;
    @Mock private CompanyRepository companyRepository;
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
    void shouldCreateNewGuardianThroughGlobalNamespace() {
        AdminResponsavelCreateRequest request = request("Mae Nova", " Mae.Nova@vanrides.dev ", "Resp!123");
        stubAbsentOwner(request.getEmail(), "mae.nova@vanrides.dev");
        stubCompanyAndSave(66L, 99L);

        AdminResponsavelDetailResponse response = service.criarResponsavel(66L, request);

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getEmail()).isEqualTo("mae.nova@vanrides.dev");
        assertThat(response.isAcessoConfigurado()).isTrue();
        ArgumentCaptor<Guardian> captor = ArgumentCaptor.forClass(Guardian.class);
        verify(guardianRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSenha()).startsWith("$2");
        verify(emailVerificationService).markPendingAndRevokeSessions(UserRole.GUARDIAN, 99L);
        verify(emailVerificationService).sendVerificationIfAllowed(
                UserRole.GUARDIAN,
                99L,
                "mae.nova@vanrides.dev"
        );
    }

    @Test
    void shouldTreatBcryptShapedGuardianCreationPasswordAsRaw() {
        AdminResponsavelCreateRequest request = request(
                "Responsavel Seguro",
                "guardian.secure@vanrides.dev",
                BCRYPT_SHAPED_PASSWORD
        );
        stubAbsentOwner(request.getEmail(), request.getEmail());
        stubCompanyAndSave(66L, 103L);

        service.criarResponsavel(66L, request);

        ArgumentCaptor<Guardian> captor = ArgumentCaptor.forClass(Guardian.class);
        verify(guardianRepository).saveAndFlush(captor.capture());
        String storedHash = captor.getValue().getSenha();
        assertThat(storedHash).isNotEqualTo(BCRYPT_SHAPED_PASSWORD);
        assertThat(storedHash).startsWith("$2a$12$");
        assertThat(BCrypt.verifyer().verify(
                BCRYPT_SHAPED_PASSWORD.toCharArray(),
                storedHash.toCharArray()
        ).verified).isTrue();
    }

    @Test
    void shouldReserveNamespaceForGuardianWithoutPassword() {
        AdminResponsavelCreateRequest request = request("Mae Sem Acesso", "sem.acesso@vanrides.dev", null);
        stubAbsentOwner(request.getEmail(), request.getEmail());
        stubCompanyAndSave(66L, 102L);

        AdminResponsavelDetailResponse response = service.criarResponsavel(66L, request);

        assertThat(response.isAcessoConfigurado()).isFalse();
        verify(loginIdentityService).requireAvailable("sem.acesso@vanrides.dev");
        verify(loginIdentityService).executeIdentityWrite(any());
        verify(guardianRepository).saveAndFlush(any(Guardian.class));
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
    }

    @Test
    void shouldRejectPendingGuardianFromSameCompanyWithoutAdoption() {
        Guardian existing = guardian(44L, "Mae Pendente", "owner@vanrides.dev");
        existing.setCompany(company(66L));
        existing.setSenha("$2a$12$existing.hash");

        assertExistingGuardianIsNotAdopted(existing, "OWNER@vanrides.dev");
    }

    @Test
    void shouldRejectVerifiedGuardianFromSameCompanyWithoutAdoption() {
        Guardian existing = guardian(45L, "Mae Verificada", "owner@vanrides.dev");
        existing.setCompany(company(66L));
        existing.setSenha("$2a$12$verified.hash");
        existing.setEmailVerified(true);

        assertExistingGuardianIsNotAdopted(existing, "OWNER@vanrides.dev");
    }

    @Test
    void shouldRejectPendingGuardianFromAnotherCompanyWithoutAdoption() {
        Guardian existing = guardian(46L, "Mae Outro Tenant", "owner@vanrides.dev");
        existing.setCompany(company(77L));
        existing.setSenha("$2a$12$another.hash");

        assertExistingGuardianIsNotAdopted(existing, "OWNER@vanrides.dev");
    }

    @Test
    void shouldRejectOwnerFromAnotherRoleWithGenericConflict() {
        AdminResponsavelCreateRequest request = request("Mae", "shared@vanrides.dev", null);
        when(loginIdentityService.requireAvailable(request.getEmail()))
                .thenThrow(new LoginIdentityConflictException());

        assertThatThrownBy(() -> service.criarResponsavel(66L, request))
                .isInstanceOfSatisfying(LoginIdentityConflictException.class,
                        exception -> assertThat(exception.getApiCode()).isEqualTo("EMAIL_ALREADY_IN_USE"));

        verify(guardianRepository, never()).findById(any());
        verify(companyRepository, never()).findById(any());
        verify(guardianRepository, never()).saveAndFlush(any());
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
        verify(emailVerificationService, never()).revokeAccountArtifacts(any(), any());
    }

    private void stubAbsentOwner(String raw, String normalized) {
        when(loginIdentityService.requireAvailable(raw)).thenReturn(normalized);
    }

    private void assertExistingGuardianIsNotAdopted(Guardian existing, String requestedEmail) {
        String originalName = existing.getNome();
        String originalEmail = existing.getEmail();
        String originalPhone = existing.getTelefone();
        String originalPassword = existing.getSenha();
        Company originalCompany = existing.getCompany();
        boolean originalVerified = existing.isEmailVerified();
        when(loginIdentityService.requireAvailable(requestedEmail))
                .thenThrow(new LoginIdentityConflictException());

        assertThatThrownBy(() -> service.criarResponsavel(
                66L,
                request("Perfil Controlado pelo Atacante", requestedEmail, "Attacker!123")))
                .isInstanceOfSatisfying(LoginIdentityConflictException.class,
                        exception -> assertThat(exception.getApiCode()).isEqualTo("EMAIL_ALREADY_IN_USE"));

        assertThat(existing.getNome()).isEqualTo(originalName);
        assertThat(existing.getEmail()).isEqualTo(originalEmail);
        assertThat(existing.getTelefone()).isEqualTo(originalPhone);
        assertThat(existing.getSenha()).isEqualTo(originalPassword);
        assertThat(existing.getCompany()).isSameAs(originalCompany);
        assertThat(existing.isEmailVerified()).isEqualTo(originalVerified);
        verify(guardianRepository, never()).findById(any());
        verify(companyRepository, never()).findById(any());
        verify(guardianRepository, never()).saveAndFlush(any());
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
        verify(emailVerificationService, never()).revokeAccountArtifacts(any(), any());
    }

    private void stubCompanyAndSave(Long companyId, Long guardianId) {
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company(companyId)));
        when(guardianRepository.saveAndFlush(any(Guardian.class))).thenAnswer(invocation -> {
            Guardian guardian = invocation.getArgument(0);
            setId(guardian, guardianId);
            return guardian;
        });
        when(guardianPassageiroRepository.findAllWithPassageiroByGuardianIdAndCompanyId(
                guardianId,
                companyId
        )).thenReturn(List.of());
    }

    private AdminResponsavelCreateRequest request(String nome, String email, String senha) {
        AdminResponsavelCreateRequest request = new AdminResponsavelCreateRequest();
        request.setNome(nome);
        request.setEmail(email);
        request.setTelefone("51999990001");
        request.setSenha(senha);
        return request;
    }

    private Guardian guardian(Long id, String nome, String email) {
        Guardian guardian = new Guardian();
        guardian.setNome(nome);
        guardian.setEmail(email);
        guardian.setTelefone("51999990000");
        setId(guardian, id);
        return guardian;
    }

    private Company company(Long id) {
        Company company = new Company("Empresa " + id, "empresa-" + id);
        company.setId(id);
        return company;
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
