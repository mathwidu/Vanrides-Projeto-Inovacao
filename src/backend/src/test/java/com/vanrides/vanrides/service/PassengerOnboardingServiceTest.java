package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyAdmin;
import com.vanrides.vanrides.model.CompanyLinkRequest;
import com.vanrides.vanrides.model.CompanyLinkRequestSource;
import com.vanrides.vanrides.model.CompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.CompanyInviteCodeRepository;
import com.vanrides.vanrides.repository.CompanyInviteCodeUsageRepository;
import com.vanrides.vanrides.repository.CompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerOnboardingServiceTest {

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanyLinkRequestRepository companyLinkRequestRepository;

    @Mock
    private CompanyInviteCodeRepository companyInviteCodeRepository;

    @Mock
    private CompanyInviteCodeUsageRepository companyInviteCodeUsageRepository;

    @Mock
    private CompanyAdminRepository companyAdminRepository;

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Mock
    private ServicoPassageiroRepository servicoPassageiroRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private PassengerOnboardingService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "passengerCompanyLinker", new PassengerCompanyLinker(passageiroRepository));
        ReflectionTestUtils.setField(service, "responseMapper", new PassengerOnboardingResponseMapper(companyInviteCodeUsageRepository));
        PassengerServiceEligibilityManagementService managementService = new PassengerServiceEligibilityManagementService();
        ReflectionTestUtils.setField(managementService, "servicoRecorrenteRepository", servicoRecorrenteRepository);
        ReflectionTestUtils.setField(managementService, "servicoPassageiroRepository", servicoPassageiroRepository);
        PassengerServiceEligibilityAssignmentService assignmentService = new PassengerServiceEligibilityAssignmentService();
        ReflectionTestUtils.setField(assignmentService, "managementService", managementService);
        ReflectionTestUtils.setField(service, "serviceEligibilityAssignmentService", assignmentService);
    }

    @Test
    void shouldAssignServiceEligibilityWhenApprovingAccessRequest() {
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L);
        CompanyLinkRequest request = request(8L, company, passageiro);
        ServicoRecorrente ida = servico(10L, company);
        ServicoRecorrente volta = servico(11L, company);

        when(companyLinkRequestRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(request));
        when(companyAdminRepository.findById(90L)).thenReturn(Optional.of(admin(90L, company)));
        when(servicoRecorrenteRepository.findAllAtivosByIdInAndCompanyId(List.of(10L, 11L), 66L))
                .thenReturn(List.of(ida, volta));
        when(servicoPassageiroRepository.countByServicoRecorrenteIdAndCompanyIdAndAtivoTrue(10L, 66L)).thenReturn(2L);
        when(servicoPassageiroRepository.countByServicoRecorrenteIdAndCompanyIdAndAtivoTrue(11L, 66L)).thenReturn(0L);
        when(servicoPassageiroRepository.save(any(ServicoPassageiro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.approveRequest(8L, 66L, 90L, null, "Aprovado com servicos", List.of(10L, 11L));

        ArgumentCaptor<ServicoPassageiro> captor = ArgumentCaptor.forClass(ServicoPassageiro.class);
        verify(servicoPassageiroRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(passageiro.getCompany()).isEqualTo(company);
        assertThat(request.getStatus()).isEqualTo(CompanyLinkRequestStatus.APPROVED);
        assertThat(captor.getAllValues()).extracting(item -> item.getServicoRecorrente().getId()).containsExactly(10L, 11L);
        assertThat(captor.getAllValues()).extracting(ServicoPassageiro::getOrdemPadrao).containsExactly(3, 1);
    }

    @Test
    void shouldRejectServiceEligibilityFromAnotherCompanyWhenApprovingAccessRequest() {
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L);
        CompanyLinkRequest request = request(8L, company, passageiro);

        when(companyLinkRequestRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(request));
        when(companyAdminRepository.findById(90L)).thenReturn(Optional.of(admin(90L, company)));
        when(servicoRecorrenteRepository.findAllAtivosByIdInAndCompanyId(List.of(10L), 66L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.approveRequest(8L, 66L, 90L, null, "Aprovado", List.of(10L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Serviço recorrente não encontrado");

        verify(servicoPassageiroRepository, never()).save(any(ServicoPassageiro.class));
    }

    @Test
    void shouldReactivateInactiveServiceEligibilityWhenApprovingAccessRequest() {
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L);
        CompanyLinkRequest request = request(8L, company, passageiro);
        ServicoRecorrente ida = servico(10L, company);
        ServicoPassageiro inactive = new ServicoPassageiro();
        inactive.setCompany(company);
        inactive.setServicoRecorrente(ida);
        inactive.setPassageiro(passageiro);
        inactive.setAtivo(false);
        inactive.setOrdemPadrao(4);
        inactive.setDataFim(java.time.LocalDate.parse("2026-05-20"));

        when(companyLinkRequestRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(request));
        when(companyAdminRepository.findById(90L)).thenReturn(Optional.of(admin(90L, company)));
        when(servicoRecorrenteRepository.findAllAtivosByIdInAndCompanyId(List.of(10L), 66L))
                .thenReturn(List.of(ida));
        when(servicoPassageiroRepository.findByServicoRecorrenteIdAndPassageiroIdAndCompanyId(10L, 21L, 66L))
                .thenReturn(Optional.of(inactive));
        when(servicoPassageiroRepository.countByServicoRecorrenteIdAndCompanyIdAndAtivoTrue(10L, 66L)).thenReturn(2L);
        when(servicoPassageiroRepository.save(any(ServicoPassageiro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.approveRequest(8L, 66L, 90L, null, "Reativado", List.of(10L));

        ArgumentCaptor<ServicoPassageiro> captor = ArgumentCaptor.forClass(ServicoPassageiro.class);
        verify(servicoPassageiroRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(inactive);
        assertThat(inactive.isAtivo()).isTrue();
        assertThat(inactive.getDataFim()).isNull();
        assertThat(inactive.getOrdemPadrao()).isEqualTo(3);
    }

    private Company company(Long id) {
        Company company = new Company("Empresa " + id, "empresa-" + id);
        company.setId(id);
        return company;
    }

    private CompanyAdmin admin(Long id, Company company) {
        CompanyAdmin admin = new CompanyAdmin();
        admin.setCompany(company);
        admin.setNome("Admin " + id);
        admin.setEmail("admin" + id + "@vanrides.dev");
        admin.setTelefone("51999990000");
        return admin;
    }

    private Passageiro passageiro(Long id) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Passageiro " + id);
        passageiro.setEmail("passageiro" + id + "@vanrides.dev");
        passageiro.setTelefone("51999990001");
        return passageiro;
    }

    private ServicoRecorrente servico(Long id, Company company) {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(id);
        servico.setCompany(company);
        servico.setNome("Servico " + id);
        return servico;
    }

    private CompanyLinkRequest request(Long id, Company company, Passageiro passageiro) {
        CompanyLinkRequest request = new CompanyLinkRequest();
        request.setPassageiro(passageiro);
        request.setCompany(company);
        request.setSource(CompanyLinkRequestSource.SEARCH_REQUEST);
        request.setStatus(CompanyLinkRequestStatus.PENDING);
        return request;
    }
}
