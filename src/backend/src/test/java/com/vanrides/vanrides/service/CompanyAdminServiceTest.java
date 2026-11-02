package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;

import com.vanrides.vanrides.dto.CompanyAdminBootstrapRequest;
import com.vanrides.vanrides.dto.CompanyAdminResponse;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyAdmin;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminServiceTest {

    private static final String BCRYPT_SHAPED_PASSWORD =
            "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u";

    @Mock
    private CompanyAdminRepository companyAdminRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private LoginIdentityService loginIdentityService;

    @InjectMocks
    private CompanyAdminService companyAdminService;

    @BeforeEach
    void configureIdentitySeam() {
        lenient().when(loginIdentityService.requireAvailable(any()))
                .thenAnswer(invocation -> normalize(invocation.getArgument(0, String.class)));
        lenient().when(loginIdentityService.executeIdentityWrite(any()))
                .thenAnswer(invocation -> {
                    Supplier<?> write = invocation.getArgument(0);
                    return write.get();
                });
    }

    @Test
    void shouldBootstrapFirstAdminForTargetCompany() {
        Company company = new Company("VanRides Default", "default");
        company.setId(7L);
        when(companyService.createNewForPublicBootstrap("VanRides Nova")).thenReturn(company);
        when(companyAdminRepository.findByCompanyId(7L)).thenReturn(Optional.empty());
        when(companyAdminRepository.saveAndFlush(any(CompanyAdmin.class))).thenAnswer(invocation -> {
            CompanyAdmin entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 99L);
            return entity;
        });

        CompanyAdminBootstrapRequest request = bootstrapRequest("VanRides Nova", "Admin@VanRides.dev");

        CompanyAdminResponse response = companyAdminService.bootstrapFirstAdmin(request);

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getCompanyId()).isEqualTo(7L);
        assertThat(response.getCompanySlug()).isEqualTo("default");
        assertThat(response.getEmail()).isEqualTo("admin@vanrides.dev");

        ArgumentCaptor<CompanyAdmin> captor = ArgumentCaptor.forClass(CompanyAdmin.class);
        verify(companyAdminRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSenha()).startsWith("$2");
        assertThat(captor.getValue().isEmailVerified()).isFalse();
        verify(emailVerificationService).markPendingAndRevokeSessions(eq(com.vanrides.vanrides.auth.UserRole.COMPANY_ADMIN), eq(99L));
        verify(emailVerificationService).sendVerificationIfAllowed(eq(com.vanrides.vanrides.auth.UserRole.COMPANY_ADMIN), eq(99L), eq("admin@vanrides.dev"));
    }

    @Test
    void shouldTreatBcryptShapedBootstrapPasswordAsRaw() {
        Company company = new Company("Empresa Segura", "empresa-segura");
        company.setId(71L);
        when(companyService.createNewForPublicBootstrap("Empresa Segura")).thenReturn(company);
        when(companyAdminRepository.findByCompanyId(71L)).thenReturn(Optional.empty());
        when(companyAdminRepository.saveAndFlush(any(CompanyAdmin.class))).thenAnswer(invocation -> {
            CompanyAdmin entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 711L);
            return entity;
        });
        CompanyAdminBootstrapRequest request = bootstrapRequest(
                "Empresa Segura",
                "secure.admin@vanrides.dev"
        );
        request.setSenha(BCRYPT_SHAPED_PASSWORD);

        companyAdminService.bootstrapFirstAdmin(request);

        ArgumentCaptor<CompanyAdmin> captor = ArgumentCaptor.forClass(CompanyAdmin.class);
        verify(companyAdminRepository).saveAndFlush(captor.capture());
        String storedHash = captor.getValue().getSenha();
        assertThat(storedHash).isNotEqualTo(BCRYPT_SHAPED_PASSWORD);
        assertThat(storedHash).startsWith("$2a$12$");
        assertThat(BCrypt.verifyer().verify(
                BCRYPT_SHAPED_PASSWORD.toCharArray(),
                storedHash.toCharArray()
        ).verified).isTrue();
    }

    @Test
    void shouldAutoVerifyBootstrapAdminWhenEmailDomainIsConfiguredForQa() {
        ReflectionTestUtils.setField(companyAdminService, "qaAutoVerifyEmailDomains", "vanrides.dev");
        Company company = new Company("Igrejinha Lab", "igrlab-qa");
        company.setId(77L);
        when(companyService.createNewForPublicBootstrap("Igrejinha Lab")).thenReturn(company);
        when(companyAdminRepository.findByCompanyId(77L)).thenReturn(Optional.empty());
        when(companyAdminRepository.saveAndFlush(any(CompanyAdmin.class))).thenAnswer(invocation -> {
            CompanyAdmin entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 770L);
            return entity;
        });

        CompanyAdminBootstrapRequest request = bootstrapRequest("Igrejinha Lab", "lab.igrlab@vanrides.dev");

        CompanyAdminResponse response = companyAdminService.bootstrapFirstAdmin(request);

        assertThat(response.isEmailVerificado()).isTrue();
        ArgumentCaptor<CompanyAdmin> captor = ArgumentCaptor.forClass(CompanyAdmin.class);
        verify(companyAdminRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().isEmailVerified()).isTrue();
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
    }

    @Test
    void shouldRejectBootstrapWhenCompanyAlreadyHasAdmin() {
        Company company = new Company("VanRides Default", "default");
        company.setId(7L);
        when(companyService.createNewForPublicBootstrap("VanRides Default")).thenReturn(company);
        CompanyAdmin existingAdmin = new CompanyAdmin();
        existingAdmin.setEmail("existing@vanrides.dev");
        existingAdmin.setEmailVerified(true);
        when(companyAdminRepository.findByCompanyId(7L)).thenReturn(Optional.of(existingAdmin));

        CompanyAdminBootstrapRequest request = bootstrapRequest("VanRides Default", "admin@vanrides.dev");

        assertThatThrownBy(() -> companyAdminService.bootstrapFirstAdmin(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("Esta empresa já possui um administrador.");
                });
        verify(companyAdminRepository, never()).saveAndFlush(any(CompanyAdmin.class));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectPendingAdminRetryWithoutMutatingIdentity() {
        Company company = new Company("Empresa Pendente", "empresa-pendente");
        company.setId(8L);
        when(companyService.createNewForPublicBootstrap("Empresa Pendente")).thenReturn(company);
        CompanyAdmin pending = new CompanyAdmin();
        ReflectionTestUtils.setField(pending, "id", 81L);
        pending.setCompany(company);
        pending.setNome("Admin Original");
        pending.setEmail("original@vanrides.dev");
        pending.setTelefone("51999990100");
        pending.setSenha("$2a$12$original.hash");
        pending.setEmailVerified(false);
        when(companyAdminRepository.findByCompanyId(8L)).thenReturn(Optional.of(pending));

        CompanyAdminBootstrapRequest request = bootstrapRequest("Empresa Pendente", "attacker@vanrides.dev");

        assertThatThrownBy(() -> companyAdminService.bootstrapFirstAdmin(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("Esta empresa já possui um administrador.");
                });

        assertThat(pending.getNome()).isEqualTo("Admin Original");
        assertThat(pending.getEmail()).isEqualTo("original@vanrides.dev");
        assertThat(pending.getTelefone()).isEqualTo("51999990100");
        assertThat(pending.getSenha()).isEqualTo("$2a$12$original.hash");
        assertThat(pending.isEmailVerified()).isFalse();
        verify(companyAdminRepository, never()).saveAndFlush(any(CompanyAdmin.class));
        verifyNoInteractions(loginIdentityService, emailVerificationService);
    }

    @Test
    void shouldTranslateOnlyTheCompanyOwnershipConstraintToLegacyConflict() {
        Company company = new Company("Empresa Concorrente", "empresa-concorrente");
        company.setId(9L);
        when(companyService.createNewForPublicBootstrap("Empresa Concorrente")).thenReturn(company);
        when(companyAdminRepository.findByCompanyId(9L)).thenReturn(Optional.empty());
        DataIntegrityViolationException failure = integrityViolation("uk_company_admin_company");
        when(companyAdminRepository.saveAndFlush(any(CompanyAdmin.class)))
                .thenThrow(failure);
        when(loginIdentityService.hasConstraint(failure, "uk_company_admin_company"))
                .thenReturn(true);

        CompanyAdminBootstrapRequest request = bootstrapRequest("Empresa Concorrente", "race@vanrides.dev");

        assertThatThrownBy(() -> companyAdminService.bootstrapFirstAdmin(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("Esta empresa já possui um administrador.");
                });

        verify(companyAdminRepository).saveAndFlush(any(CompanyAdmin.class));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldPropagateAnUnrelatedIntegrityFailureInsteadOfMaskingItAsCompanyConflict() {
        Company company = new Company("Empresa Concorrente", "empresa-concorrente");
        company.setId(10L);
        when(companyService.createNewForPublicBootstrap("Empresa Concorrente")).thenReturn(company);
        when(companyAdminRepository.findByCompanyId(10L)).thenReturn(Optional.empty());
        DataIntegrityViolationException failure = integrityViolation("uk_unrelated_constraint");
        when(companyAdminRepository.saveAndFlush(any(CompanyAdmin.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> companyAdminService.bootstrapFirstAdmin(
                bootstrapRequest("Empresa Concorrente", "race@vanrides.dev")))
                .isSameAs(failure);

        verify(loginIdentityService).hasConstraint(failure, "uk_company_admin_company");
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldKeepCompanyCreationAndKnownEmailConflictInsideTheBootstrapTransaction() {
        Company company = new Company("Empresa Nova", "empresa-nova");
        company.setId(11L);
        when(companyService.createNewForPublicBootstrap("Empresa Nova")).thenReturn(company);
        when(companyAdminRepository.findByCompanyId(11L)).thenReturn(Optional.empty());
        LoginIdentityConflictException conflict = new LoginIdentityConflictException();
        when(loginIdentityService.requireAvailable("taken@vanrides.dev"))
                .thenThrow(conflict);

        assertThatThrownBy(() -> companyAdminService.bootstrapFirstAdmin(
                bootstrapRequest("Empresa Nova", "taken@vanrides.dev")))
                .isSameAs(conflict)
                .isInstanceOfSatisfying(LoginIdentityConflictException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).isEqualTo("O e-mail informado já está em uso.");
                });

        var order = inOrder(companyService, companyAdminRepository, loginIdentityService);
        order.verify(companyService).createNewForPublicBootstrap("Empresa Nova");
        order.verify(companyAdminRepository).findByCompanyId(11L);
        order.verify(loginIdentityService).requireAvailable("taken@vanrides.dev");
        verify(companyAdminRepository, never()).saveAndFlush(any(CompanyAdmin.class));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectMissingCompanyNameBeforeAnyInteraction() {
        CompanyAdminBootstrapRequest request = new CompanyAdminBootstrapRequest();
        request.setNome("Admin Aurora");
        request.setEmail("admin.aurora@vanrides.dev");
        request.setTelefone("51999999999");
        request.setSenha("Admin!123");

        assertThatThrownBy(() -> companyAdminService.bootstrapFirstAdmin(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason())
                            .isEqualTo("Nome da empresa é obrigatório para o primeiro cadastro.");
                });
        verifyNoInteractions(companyService, companyAdminRepository, loginIdentityService,
                emailVerificationService);
    }

    private CompanyAdminBootstrapRequest bootstrapRequest(String companyName, String email) {
        CompanyAdminBootstrapRequest request = new CompanyAdminBootstrapRequest();
        request.setCompanyName(companyName);
        request.setNome("Admin Portal");
        request.setEmail(email);
        request.setTelefone("51999999999");
        request.setSenha("Admin!123");
        return request;
    }

    private DataIntegrityViolationException integrityViolation(String constraintName) {
        ConstraintViolationException hibernate = new ConstraintViolationException(
                "constraint failure",
                new SQLException("duplicate key", "23505"),
                "insert",
                constraintName
        );
        return new DataIntegrityViolationException("write failed", hibernate);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
