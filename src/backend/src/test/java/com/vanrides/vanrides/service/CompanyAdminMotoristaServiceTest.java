package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminMotoristaCreateRequest;
import com.vanrides.vanrides.dto.AdminMotoristaUpdateRequest;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminMotoristaServiceTest {
    @Mock private MotoristaRepository motoristaRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private LoginIdentityService loginIdentityService;
    @Mock private MotoristaService motoristaService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private DriverCompanyLinker driverCompanyLinker;
    @InjectMocks
    private CompanyAdminMotoristaService companyAdminMotoristaService;
    @BeforeEach
    void setUpIdentityBoundary() {
        lenient().when(loginIdentityService.requireAvailable(any(String.class)))
                .thenAnswer(invocation -> {
                    String raw = invocation.getArgument(0, String.class);
                    return raw.trim().toLowerCase();
                });
        lenient().when(loginIdentityService.executeIdentityWrite(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
    }

    @Test
    void shouldListDriversWithinCompany() {
        Motorista motorista = new Motorista();
        motorista.setId(7L);
        motorista.setNome("Motorista Um");
        motorista.setEmail("mot1@vanrides.dev");
        motorista.setTelefone("51999990007");
        motorista.setDataNascimento(LocalDate.of(1987, 2, 16));
        motorista.setFotoPerfilUrl("/uploads/avatars/motorista-7.png");

        when(motoristaRepository.findAllByCompanyIdOrderByNomeAsc(66L)).thenReturn(List.of(motorista));

        var result = companyAdminMotoristaService.listarMotoristas(66L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNome()).isEqualTo("Motorista Um");
        assertThat(result.get(0).getDataNascimento()).isEqualTo(LocalDate.of(1987, 2, 16));
        assertThat(result.get(0).getFotoPerfilUrl()).isEqualTo("/uploads/avatars/motorista-7.png");
    }

    @Test
    void shouldCreateDriverWithinAdminCompany() {
        Company company = new Company("VanRides QA", "qa");
        company.setId(66L);

        AdminMotoristaCreateRequest request = createRequest("Motorista Novo", "Motorista.Novo@vanrides.dev", "Motorista@123");

        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(motoristaService.salvarNovoMotorista(any(Motorista.class), anyString())).thenAnswer(invocation -> {
            Motorista entity = invocation.getArgument(0);
            entity.setId(8L);
            return entity;
        });

        var response = companyAdminMotoristaService.criarMotorista(66L, request);

        assertThat(response.getId()).isEqualTo(8L);
        assertThat(response.getEmail()).isEqualTo("motorista.novo@vanrides.dev");

        ArgumentCaptor<Motorista> captor = ArgumentCaptor.forClass(Motorista.class);
        verify(motoristaService).salvarNovoMotorista(captor.capture(), eq("Motorista@123"));
        assertThat(captor.getValue().getCompany().getId()).isEqualTo(66L);
    }

    @Test
    void shouldAutoVerifyDriverWhenEmailDomainIsConfiguredForQa() {
        ReflectionTestUtils.setField(companyAdminMotoristaService, "qaAutoVerifyEmailDomains", "vanrides.dev");
        Company company = new Company("VanRides QA", "qa");
        company.setId(66L);

        AdminMotoristaCreateRequest request = createRequest("Motorista QA", "Motorista.QA@vanrides.dev", "Motorista@123");

        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(motoristaService.salvarNovoMotorista(any(Motorista.class), anyString())).thenAnswer(invocation -> {
            Motorista entity = invocation.getArgument(0);
            entity.setId(88L);
            return entity;
        });

        var response = companyAdminMotoristaService.criarMotorista(66L, request);

        assertThat(response.isEmailVerificado()).isTrue();
        ArgumentCaptor<Motorista> captor = ArgumentCaptor.forClass(Motorista.class);
        verify(motoristaService).salvarNovoMotorista(captor.capture(), eq("Motorista@123"));
        assertThat(captor.getValue().isEmailVerified()).isTrue();
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
    }

    @Test
    void shouldRejectVerifiedPublicDriverWithoutAdoption() {
        Motorista publicDriver = new Motorista();
        publicDriver.setId(91L);
        publicDriver.setNome("Motorista App");
        publicDriver.setEmail("motorista.app@vanrides.dev");
        publicDriver.setTelefone("51999990091");
        publicDriver.setSenha("$2a$12$hash.fake");
        publicDriver.setEmailVerified(true);

        assertExistingDriverIsNotAdopted(publicDriver, "Motorista.App@vanrides.dev");
    }

    @Test
    void shouldRejectPendingDriverFromSameCompanyWithoutAdoption() {
        Company company = new Company("VanRides QA", "qa");
        company.setId(66L);

        Motorista pendingDriver = new Motorista();
        pendingDriver.setId(92L);
        pendingDriver.setNome("Motorista Pendente");
        pendingDriver.setEmail("motorista.pendente@vanrides.dev");
        pendingDriver.setTelefone("51999990092");
        pendingDriver.setSenha("$2a$12$existing.hash");
        pendingDriver.setCompany(company);
        pendingDriver.setEmailVerified(false);

        assertExistingDriverIsNotAdopted(pendingDriver, "Motorista.Pendente@vanrides.dev");
    }

    @Test
    void shouldRejectPendingDriverFromAnotherCompanyWithoutAdoption() {
        Company anotherCompany = new Company("Outra Empresa", "outra");
        anotherCompany.setId(77L);
        Motorista pendingDriver = new Motorista();
        pendingDriver.setId(93L);
        pendingDriver.setNome("Motorista Outra Empresa");
        pendingDriver.setEmail("motorista.outra@vanrides.dev");
        pendingDriver.setTelefone("51999990093");
        pendingDriver.setSenha("$2a$12$another.hash");
        pendingDriver.setCompany(anotherCompany);
        pendingDriver.setEmailVerified(false);

        assertExistingDriverIsNotAdopted(pendingDriver, "Motorista.Outra@vanrides.dev");
    }

    @Test
    void shouldRejectNewDriverWithoutPassword() {
        AdminMotoristaCreateRequest request = createRequest("Motorista Sem Senha", "sem.senha@vanrides.dev", "");

        assertThatThrownBy(() -> companyAdminMotoristaService.criarMotorista(66L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Senha é obrigatória");
    }

    @Test
    void shouldRejectCrossRoleOwnerWithGenericConflictAndNoArtifacts() {
        AdminMotoristaCreateRequest request = createRequest("Duplicado", "duplicado@vanrides.dev", "Motorista@123");
        when(loginIdentityService.requireAvailable("duplicado@vanrides.dev"))
                .thenThrow(new LoginIdentityConflictException());

        assertThatThrownBy(() -> companyAdminMotoristaService.criarMotorista(66L, request))
                .isInstanceOfSatisfying(LoginIdentityConflictException.class,
                        exception -> assertThat(exception.getApiCode()).isEqualTo("EMAIL_ALREADY_IN_USE"));

        verify(motoristaRepository, never()).findById(any());
        verify(companyRepository, never()).findById(any());
        verify(motoristaService, never()).salvarNovoMotorista(any(), any());
        verify(driverCompanyLinker, never()).link(any(), any(), any(), any());
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
        verify(emailVerificationService, never()).revokeAccountArtifacts(any(), any());
    }

    @Test
    void shouldUpdateDriverWithinCompany() {
        String storedHash = "$2b$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u";
        Motorista motorista = new Motorista();
        motorista.setId(8L);
        motorista.setNome("Motorista Antigo");
        motorista.setEmail("antigo@vanrides.dev");
        motorista.setTelefone("51999990008");
        motorista.setSenha(storedHash);

        AdminMotoristaUpdateRequest request = new AdminMotoristaUpdateRequest();
        request.setNome("Motorista Atualizado");
        request.setEmail("motorista.atualizado@vanrides.dev");
        request.setTelefone("51999991111");

        when(motoristaRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(motorista));
        when(loginIdentityService.requireOwnedBy("antigo@vanrides.dev", UserRole.DRIVER, 8L))
                .thenReturn("antigo@vanrides.dev");
        when(loginIdentityService.requireAvailableFor(
                "motorista.atualizado@vanrides.dev",
                UserRole.DRIVER,
                8L)).thenReturn("motorista.atualizado@vanrides.dev");
        when(motoristaRepository.saveAndFlush(any(Motorista.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = companyAdminMotoristaService.editarMotorista(66L, 8L, request);

        assertThat(response.getNome()).isEqualTo("Motorista Atualizado");
        assertThat(motorista.getEmail()).isEqualTo("motorista.atualizado@vanrides.dev");
        assertThat(motorista.getTelefone()).isEqualTo("51999991111");
        assertThat(motorista.getSenha()).isEqualTo(storedHash);
    }

    @Test
    void shouldRejectUpdateWhenEmailBelongsToAnotherDriver() {
        Motorista motorista = new Motorista();
        motorista.setId(8L);
        motorista.setEmail("motorista@vanrides.dev");

        AdminMotoristaUpdateRequest request = new AdminMotoristaUpdateRequest();
        request.setNome("Motorista Atualizado");
        request.setEmail("duplicado@vanrides.dev");
        request.setTelefone("51999991111");

        when(motoristaRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(motorista));
        when(loginIdentityService.requireOwnedBy("motorista@vanrides.dev", UserRole.DRIVER, 8L))
                .thenReturn("motorista@vanrides.dev");
        when(loginIdentityService.requireAvailableFor("duplicado@vanrides.dev", UserRole.DRIVER, 8L))
                .thenThrow(new LoginIdentityConflictException());

        assertThatThrownBy(() -> companyAdminMotoristaService.editarMotorista(66L, 8L, request))
                .isInstanceOf(LoginIdentityConflictException.class);
    }

    private void assertExistingDriverIsNotAdopted(Motorista existing, String requestedEmail) {
        String originalName = existing.getNome();
        String originalEmail = existing.getEmail();
        String originalPhone = existing.getTelefone();
        String originalPassword = existing.getSenha();
        Company originalCompany = existing.getCompany();
        boolean originalVerified = existing.isEmailVerified();
        when(loginIdentityService.requireAvailable(requestedEmail))
                .thenThrow(new LoginIdentityConflictException());

        AdminMotoristaCreateRequest request = createRequest(
                "Perfil Controlado pelo Atacante",
                requestedEmail,
                "Attacker@123"
        );

        assertThatThrownBy(() -> companyAdminMotoristaService.criarMotorista(66L, request))
                .isInstanceOfSatisfying(LoginIdentityConflictException.class,
                        exception -> assertThat(exception.getApiCode()).isEqualTo("EMAIL_ALREADY_IN_USE"));

        assertThat(existing.getNome()).isEqualTo(originalName);
        assertThat(existing.getEmail()).isEqualTo(originalEmail);
        assertThat(existing.getTelefone()).isEqualTo(originalPhone);
        assertThat(existing.getSenha()).isEqualTo(originalPassword);
        assertThat(existing.getCompany()).isSameAs(originalCompany);
        assertThat(existing.isEmailVerified()).isEqualTo(originalVerified);
        verify(motoristaRepository, never()).findById(any());
        verify(companyRepository, never()).findById(any());
        verify(motoristaService, never()).salvarNovoMotorista(any(), any());
        verify(driverCompanyLinker, never()).link(any(), any(), any(), any());
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
        verify(emailVerificationService, never()).revokeAccountArtifacts(any(), any());
    }

    private AdminMotoristaCreateRequest createRequest(String name, String email, String password) {
        AdminMotoristaCreateRequest request = new AdminMotoristaCreateRequest();
        request.setNome(name);
        request.setEmail(email);
        request.setTelefone("51999990008");
        request.setSenha(password);
        return request;
    }
}
