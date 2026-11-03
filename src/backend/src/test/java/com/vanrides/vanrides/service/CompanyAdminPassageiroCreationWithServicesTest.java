package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminPassageiroCreateRequest;
import com.vanrides.vanrides.dto.AdminPassageiroDetailResponse;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminPassageiroCreationWithServicesTest {

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private GuardianPassageiroRepository guardianPassageiroRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private LoginIdentityService loginIdentityService;

    @Mock
    private PassageiroService passageiroService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PassengerServiceEligibilityManagementService serviceEligibilityManagementService;

    @Mock
    private ServicoPassageiroRepository servicoPassageiroRepository;

    @Mock
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @InjectMocks
    private CompanyAdminPassageiroService companyAdminPassageiroService;

    @BeforeEach
    void setUpAvailableIdentity() {
        lenient().when(loginIdentityService.requireAvailable(any(String.class)))
                .thenAnswer(invocation -> {
                    String raw = invocation.getArgument(0, String.class);
                    return raw.trim().toLowerCase();
                });
    }

    @Test
    void shouldCreatePassengerWithServiceEligibilitiesInSameTransaction() {
        Company company = company(66L);
        AdminPassageiroCreateRequest request = request("Aluno.Servico@vanrides.dev", List.of(10L, 11L));

        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(serviceEligibilityManagementService.validateServiceIds(66L, List.of(10L, 11L)))
                .thenReturn(List.of(10L, 11L));
        when(passageiroService.salvarNovoPassageiro(any(Passageiro.class), anyString())).thenAnswer(invocation -> {
            Passageiro entity = invocation.getArgument(0);
            entity.setId(26L);
            return entity;
        });
        when(guardianPassageiroRepository.findAllWithGuardianByPassageiroIdAndCompanyId(26L, 66L))
                .thenReturn(List.of());

        AdminPassageiroDetailResponse response = companyAdminPassageiroService.criarPassageiro(66L, request);

        assertThat(response.getId()).isEqualTo(26L);
        ArgumentCaptor<Passageiro> captor = ArgumentCaptor.forClass(Passageiro.class);
        InOrder order = inOrder(serviceEligibilityManagementService, passageiroService);
        order.verify(serviceEligibilityManagementService).validateServiceIds(66L, List.of(10L, 11L));
        order.verify(passageiroService).salvarNovoPassageiro(captor.capture(), eq("Aluno@123"));
        order.verify(serviceEligibilityManagementService)
                .replaceServiceEligibilities(captor.getValue(), 66L, List.of(10L, 11L));
        assertThat(captor.getValue().getCompany().getId()).isEqualTo(66L);
    }

    @Test
    void shouldRejectServiceOutsideCompanyBeforeCreatingPassenger() {
        AdminPassageiroCreateRequest request = request("Aluno.Invalido@vanrides.dev", List.of(999L));
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company(66L)));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço recorrente não encontrado na empresa atual."))
                .when(serviceEligibilityManagementService)
                .validateServiceIds(66L, List.of(999L));

        assertThatThrownBy(() -> companyAdminPassageiroService.criarPassageiro(66L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Serviço recorrente");

        verify(passageiroService, never()).salvarNovoPassageiro(any(), any());
        verify(serviceEligibilityManagementService, never())
                .replaceServiceEligibilities(any(), any(), any());
    }

    @Test
    void shouldRejectCrossRoleOwnerWithGenericConflict() {
        AdminPassageiroCreateRequest request = request("shared@vanrides.dev", null);
        when(loginIdentityService.requireAvailable("shared@vanrides.dev"))
                .thenThrow(new LoginIdentityConflictException());

        assertThatThrownBy(() -> companyAdminPassageiroService.criarPassageiro(66L, request))
                .isInstanceOfSatisfying(LoginIdentityConflictException.class,
                        exception -> assertThat(exception.getApiCode()).isEqualTo("EMAIL_ALREADY_IN_USE"));

        verify(passageiroRepository, never()).findById(any());
        verify(companyRepository, never()).findById(any());
        verify(passageiroService, never()).salvarNovoPassageiro(any(), any());
        verify(serviceEligibilityManagementService, never()).validateServiceIds(any(), any());
        verify(serviceEligibilityManagementService, never()).replaceServiceEligibilities(any(), any(), any());
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
        verify(emailVerificationService, never()).revokeAccountArtifacts(any(), any());
    }

    @Test
    void shouldRejectPendingPassengerFromSameCompanyWithoutAdoption() {
        Passageiro pending = existingPassenger(42L, company(66L), false);

        assertExistingPassengerIsNotAdopted(pending, "Pending@vanrides.dev");
    }

    @Test
    void shouldRejectVerifiedPassengerFromSameCompanyWithoutAdoption() {
        Passageiro verified = existingPassenger(43L, company(66L), true);

        assertExistingPassengerIsNotAdopted(verified, "Pending@vanrides.dev");
    }

    @Test
    void shouldRejectPendingPassengerFromAnotherCompanyWithoutAdoption() {
        Passageiro crossTenant = existingPassenger(44L, company(77L), false);

        assertExistingPassengerIsNotAdopted(crossTenant, "Pending@vanrides.dev");
    }

    private void assertExistingPassengerIsNotAdopted(Passageiro existing, String requestedEmail) {
        String originalName = existing.getNome();
        String originalEmail = existing.getEmail();
        String originalPhone = existing.getTelefone();
        String originalPassword = existing.getSenha();
        Company originalCompany = existing.getCompany();
        boolean originalVerified = existing.isEmailVerified();
        when(loginIdentityService.requireAvailable(requestedEmail))
                .thenThrow(new LoginIdentityConflictException());

        assertThatThrownBy(() -> companyAdminPassageiroService.criarPassageiro(
                66L,
                request(requestedEmail, List.of(10L))))
                .isInstanceOfSatisfying(LoginIdentityConflictException.class,
                        exception -> assertThat(exception.getApiCode()).isEqualTo("EMAIL_ALREADY_IN_USE"));

        assertThat(existing.getNome()).isEqualTo(originalName);
        assertThat(existing.getEmail()).isEqualTo(originalEmail);
        assertThat(existing.getTelefone()).isEqualTo(originalPhone);
        assertThat(existing.getSenha()).isEqualTo(originalPassword);
        assertThat(existing.getCompany()).isSameAs(originalCompany);
        assertThat(existing.isEmailVerified()).isEqualTo(originalVerified);
        verify(passageiroRepository, never()).findById(any());
        verify(companyRepository, never()).findById(any());
        verify(passageiroService, never()).salvarNovoPassageiro(any(), any());
        verify(serviceEligibilityManagementService, never()).validateServiceIds(any(), any());
        verify(serviceEligibilityManagementService, never()).replaceServiceEligibilities(any(), any(), any());
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
        verify(emailVerificationService, never()).revokeAccountArtifacts(any(), any());
    }

    private Passageiro existingPassenger(Long id, Company company, boolean verified) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Perfil Existente");
        passageiro.setEmail("pending@vanrides.dev");
        passageiro.setTelefone("51999990042");
        passageiro.setSenha("$2a$12$existing.hash");
        passageiro.setCompany(company);
        passageiro.setEmailVerified(verified);
        return passageiro;
    }

    private AdminPassageiroCreateRequest request(String email, List<Long> servicoIds) {
        AdminPassageiroCreateRequest request = new AdminPassageiroCreateRequest();
        request.setNome("Aluno Com Serviço");
        request.setEmail(email);
        request.setTelefone("51999990026");
        request.setEndereco("Rua Nova");
        request.setBairro("Centro");
        request.setCidade("Porto Alegre");
        request.setEstado("RS");
        request.setCep("90000-000");
        request.setSenha("Aluno@123");
        request.setMenorDeIdade(false);
        request.setServicoIds(servicoIds);
        return request;
    }

    private Company company(Long id) {
        Company company = new Company("VanRides QA", "qa");
        company.setId(id);
        return company;
    }
}
