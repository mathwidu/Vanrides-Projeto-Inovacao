package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminPassageiroCreateRequest;
import com.vanrides.vanrides.dto.AdminPassageiroDetailResponse;
import com.vanrides.vanrides.dto.AdminPassageiroServicosUpdateRequest;
import com.vanrides.vanrides.dto.AdminPassageiroUpdateRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.TipoOperacaoServico;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminPassageiroServiceTest {

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
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Mock
    private ServicoPassageiroRepository servicoPassageiroRepository;

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
    void shouldListPassengerSummariesForCompany() {
        Passageiro passageiro = passenger(21L, "Aluno Portal");
        Motorista motorista = motorista(8L, "Motorista Azul");
        passageiro.setMotorista(motorista);

        when(passageiroRepository.findAllByCompanyIdOrderByNomeAsc(66L)).thenReturn(List.of(passageiro));
        when(guardianPassageiroRepository.countByPassageiroIdAndCompanyId(21L, 66L)).thenReturn(2L);

        var result = companyAdminPassageiroService.listarPassageiros(66L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNome()).isEqualTo("Aluno Portal");
        assertThat(result.get(0).getResponsavelCount()).isEqualTo(2);
        assertThat(result.get(0).getMotorista().getId()).isEqualTo(8L);
    }

    @Test
    void shouldReturnPassengerDetailWithLinkedGuardians() {
        Passageiro passageiro = passenger(22L, "Aluno Detalhe");
        passageiro.setMotorista(motorista(9L, "Motorista Verde"));

        Guardian guardian = new Guardian();
        guardian.setNome("Mae Teste");
        guardian.setEmail("mae@vanrides.dev");
        guardian.setTelefone("51999990001");
        ReflectionTestUtils.setField(guardian, "id", 77L);

        GuardianPassageiro guardianPassageiro = new GuardianPassageiro();
        guardianPassageiro.setGuardian(guardian);
        guardianPassageiro.setPassageiro(passageiro);

        when(passageiroRepository.findByIdAndCompanyId(22L, 66L)).thenReturn(Optional.of(passageiro));
        when(guardianPassageiroRepository.findAllWithGuardianByPassageiroIdAndCompanyId(22L, 66L))
                .thenReturn(List.of(guardianPassageiro));

        AdminPassageiroDetailResponse detail = companyAdminPassageiroService.buscarDetalhe(22L, 66L).orElseThrow();

        assertThat(detail.getMotorista()).isNotNull();
        assertThat(detail.getResponsaveis()).hasSize(1);
        assertThat(detail.getResponsaveis().get(0).getNome()).isEqualTo("Mae Teste");
    }

    @Test
    void shouldCreatePassengerWithinAdminCompany() {
        Company company = new Company("VanRides QA", "qa");
        company.setId(66L);
        Motorista motorista = motorista(8L, "Motorista Azul");

        AdminPassageiroCreateRequest request = new AdminPassageiroCreateRequest();
        request.setNome("Aluno Novo");
        request.setEmail("Aluno.Novo@vanrides.dev");
        request.setTelefone("51999990025");
        request.setEndereco("Rua Nova");
        request.setBairro("Centro");
        request.setCidade("Porto Alegre");
        request.setEstado("RS");
        request.setCep("90000-000");
        request.setSenha("Aluno@123");
        request.setMenorDeIdade(true);
        request.setMotoristaId(8L);
        request.setLatitude(-29.5558622);
        request.setLongitude(-50.7887441);

        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(motoristaRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(motorista));
        when(passageiroService.salvarNovoPassageiro(any(Passageiro.class), anyString())).thenAnswer(invocation -> {
            Passageiro entity = invocation.getArgument(0);
            entity.setId(25L);
            return entity;
        });
        when(guardianPassageiroRepository.findAllWithGuardianByPassageiroIdAndCompanyId(25L, 66L)).thenReturn(List.of());

        AdminPassageiroDetailResponse response = companyAdminPassageiroService.criarPassageiro(66L, request);

        assertThat(response.getId()).isEqualTo(25L);
        assertThat(response.getEmail()).isEqualTo("aluno.novo@vanrides.dev");
        assertThat(response.getMotorista()).isNotNull();
        assertThat(response.getMotorista().getId()).isEqualTo(8L);
        assertThat(response.isMenorDeIdade()).isTrue();
        assertThat(response.getLatitude()).isEqualTo(-29.5558622);
        assertThat(response.getLongitude()).isEqualTo(-50.7887441);
    }

    @Test
    void shouldAutoVerifyPassengerWhenEmailDomainIsConfiguredForQa() {
        ReflectionTestUtils.setField(companyAdminPassageiroService, "qaAutoVerifyEmailDomains", "vanrides.dev");
        Company company = new Company("VanRides QA", "qa");
        company.setId(66L);
        Motorista motorista = motorista(8L, "Motorista Azul");

        AdminPassageiroCreateRequest request = new AdminPassageiroCreateRequest();
        request.setNome("Aluno QA");
        request.setEmail("Aluno.QA@vanrides.dev");
        request.setTelefone("51999990025");
        request.setEndereco("Rua Nova");
        request.setBairro("Centro");
        request.setCidade("Porto Alegre");
        request.setEstado("RS");
        request.setCep("90000-000");
        request.setSenha("Aluno@123");
        request.setMenorDeIdade(true);
        request.setMotoristaId(8L);
        request.setLatitude(-29.5558622);
        request.setLongitude(-50.7887441);

        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(motoristaRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(motorista));
        when(passageiroService.salvarNovoPassageiro(any(Passageiro.class), anyString())).thenAnswer(invocation -> {
            Passageiro entity = invocation.getArgument(0);
            entity.setId(26L);
            return entity;
        });
        when(guardianPassageiroRepository.findAllWithGuardianByPassageiroIdAndCompanyId(26L, 66L)).thenReturn(List.of());

        AdminPassageiroDetailResponse response = companyAdminPassageiroService.criarPassageiro(66L, request);

        assertThat(response.isEmailVerificado()).isTrue();
        ArgumentCaptor<Passageiro> captor = ArgumentCaptor.forClass(Passageiro.class);
        verify(passageiroService).salvarNovoPassageiro(captor.capture(), eq("Aluno@123"));
        assertThat(captor.getValue().isEmailVerified()).isTrue();
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
    }

    @Test
    void shouldUpdatePassengerAndReassignDriverWithinCompany() {
        Passageiro passageiro = passenger(23L, "Aluno Base");
        passageiro.setMotorista(motorista(10L, "Motorista Antigo"));
        Motorista novoMotorista = motorista(11L, "Motorista Novo");

        AdminPassageiroUpdateRequest request = new AdminPassageiroUpdateRequest();
        request.setNome("Aluno Atualizado");
        request.setEmail("aluno.atualizado@vanrides.dev");
        request.setTelefone("51999990023");
        request.setEndereco("Rua Nova");
        request.setBairro("Centro");
        request.setCidade("Porto Alegre");
        request.setEstado("RS");
        request.setCep("90000-111");
        request.setMenorDeIdade(true);
        request.setMotoristaId(11L);
        request.setLatitude(-29.6660202);
        request.setLongitude(-51.1194247);

        when(passageiroRepository.findByIdAndCompanyId(23L, 66L)).thenReturn(Optional.of(passageiro));
        when(passageiroService.atualizarPassageiroAdministrativo(
                23L,
                66L,
                request
        )).thenAnswer(invocation -> {
            passageiro.setEmail("aluno.atualizado@vanrides.dev");
            passageiro.setNome("Aluno Atualizado");
            passageiro.setMotorista(novoMotorista);
            passageiro.setMenorDeIdade(true);
            passageiro.setLatitude(-29.6660202);
            passageiro.setLongitude(-51.1194247);
            return passageiro;
        });
        when(guardianPassageiroRepository.findAllWithGuardianByPassageiroIdAndCompanyId(23L, 66L)).thenReturn(List.of());

        AdminPassageiroDetailResponse response = companyAdminPassageiroService.atualizarPassageiro(23L, 66L, request);

        assertThat(response.getNome()).isEqualTo("Aluno Atualizado");
        assertThat(response.getMotorista()).isNotNull();
        assertThat(response.getMotorista().getId()).isEqualTo(11L);
        assertThat(response.isMenorDeIdade()).isTrue();
        assertThat(response.getLatitude()).isEqualTo(-29.6660202);
        assertThat(response.getLongitude()).isEqualTo(-51.1194247);
        verify(passageiroService).atualizarPassageiroAdministrativo(23L, 66L, request);
    }

    @Test
    void shouldRejectReassignWhenDriverIsOutsideCompany() {
        when(passageiroService.reatribuirMotorista(24L, 66L, 999L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual."));

        assertThatThrownBy(() -> companyAdminPassageiroService.reatribuirMotorista(24L, 66L, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Motorista não encontrado");
    }

    @Test
    void shouldListPassengerServiceEligibilityWithEnabledFlags() {
        Company company = company(66L);
        Passageiro passageiro = passenger(21L, "Aluno Serviços");
        passageiro.setCompany(company);
        Motorista motorista = motorista(8L, "Motorista Azul");
        ServicoRecorrente ida = servico(10L, company, "Ida manhã", TipoOperacaoServico.IDA);
        ida.setMotoristaPadrao(motorista);
        ida.setHorarioPartida(LocalTime.of(7, 30));
        ServicoRecorrente volta = servico(11L, company, "Volta tarde", TipoOperacaoServico.VOLTA);
        volta.setHorarioPartida(LocalTime.of(17, 0));
        ServicoPassageiro vinculo = servicoPassageiro(101L, company, ida, passageiro, true, 2);

        when(passageiroRepository.findByIdAndCompanyId(21L, 66L)).thenReturn(Optional.of(passageiro));
        when(servicoRecorrenteRepository.findAllAtivosWithMotoristaByCompanyIdOrderByNomeAsc(66L))
                .thenReturn(List.of(ida, volta));
        when(servicoPassageiroRepository.findAllAtivosWithServicoByPassageiroIdAndCompanyId(21L, 66L))
                .thenReturn(List.of(vinculo));

        var result = companyAdminPassageiroService.listarServicosDoPassageiro(21L, 66L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getServicoId()).isEqualTo(10L);
        assertThat(result.get(0).isHabilitado()).isTrue();
        assertThat(result.get(0).getElegibilidadeId()).isEqualTo(101L);
        assertThat(result.get(0).getMotoristaPadrao().getId()).isEqualTo(8L);
        assertThat(result.get(1).getServicoId()).isEqualTo(11L);
        assertThat(result.get(1).isHabilitado()).isFalse();
    }

    @Test
    void shouldReplacePassengerServiceEligibilityAndReturnEnabledFlags() {
        Company company = company(66L);
        Passageiro passageiro = passenger(21L, "Aluno Serviços");
        passageiro.setCompany(company);
        ServicoRecorrente ida = servico(10L, company, "Ida manhã", TipoOperacaoServico.IDA);
        ServicoRecorrente volta = servico(11L, company, "Volta tarde", TipoOperacaoServico.VOLTA);
        ServicoPassageiro activeReturnLink = servicoPassageiro(102L, company, volta, passageiro, true, 4);

        AdminPassageiroServicosUpdateRequest request = new AdminPassageiroServicosUpdateRequest();
        request.setServicoIds(List.of(11L));

        when(passageiroRepository.findByIdAndCompanyId(21L, 66L)).thenReturn(Optional.of(passageiro));
        when(servicoRecorrenteRepository.findAllAtivosWithMotoristaByCompanyIdOrderByNomeAsc(66L))
                .thenReturn(List.of(ida, volta));
        when(servicoPassageiroRepository.findAllAtivosWithServicoByPassageiroIdAndCompanyId(21L, 66L))
                .thenReturn(List.of(activeReturnLink));

        var result = companyAdminPassageiroService.substituirServicosDoPassageiro(21L, 66L, request);

        assertThat(result).extracting("servicoId").containsExactly(10L, 11L);
        assertThat(result.get(0).isHabilitado()).isFalse();
        assertThat(result.get(1).isHabilitado()).isTrue();
        verify(serviceEligibilityManagementService).replaceServiceEligibilities(passageiro, 66L, List.of(11L));
    }

    @Test
    void shouldRejectServiceOutsideCompanyWhenUpdatingPassengerEligibility() {
        Passageiro passageiro = passenger(21L, "Aluno Serviços");
        AdminPassageiroServicosUpdateRequest request = new AdminPassageiroServicosUpdateRequest();
        request.setServicoIds(List.of(999L));

        when(passageiroRepository.findByIdAndCompanyId(21L, 66L)).thenReturn(Optional.of(passageiro));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Serviço recorrente não encontrado na empresa atual."))
                .when(serviceEligibilityManagementService)
                .replaceServiceEligibilities(passageiro, 66L, List.of(999L));

        assertThatThrownBy(() -> companyAdminPassageiroService.substituirServicosDoPassageiro(21L, 66L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Serviço recorrente");

        verify(servicoPassageiroRepository, never()).save(any(ServicoPassageiro.class));
    }

    private Passageiro passenger(Long id, String nome) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome(nome);
        passageiro.setEmail(nome.toLowerCase().replace(" ", ".") + "@vanrides.dev");
        passageiro.setTelefone("51999990000");
        passageiro.setEndereco("Rua Teste");
        passageiro.setBairro("Centro");
        passageiro.setCidade("Porto Alegre");
        passageiro.setEstado("RS");
        passageiro.setCep("90000-000");
        return passageiro;
    }

    private Motorista motorista(Long id, String nome) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome(nome);
        motorista.setEmail(nome.toLowerCase().replace(" ", ".") + "@vanrides.dev");
        motorista.setTelefone("51999990099");
        return motorista;
    }

    private Company company(Long id) {
        Company company = new Company("VanRides QA", "qa");
        company.setId(id);
        return company;
    }

    private ServicoRecorrente servico(Long id,
                                      Company company,
                                      String nome,
                                      TipoOperacaoServico tipoOperacao) {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(id);
        servico.setCompany(company);
        servico.setNome(nome);
        servico.setTipoOperacao(tipoOperacao);
        servico.setAtivo(true);
        return servico;
    }

    private ServicoPassageiro servicoPassageiro(Long id,
                                                Company company,
                                                ServicoRecorrente servico,
                                                Passageiro passageiro,
                                                boolean ativo,
                                                Integer ordemPadrao) {
        ServicoPassageiro servicoPassageiro = new ServicoPassageiro();
        servicoPassageiro.setId(id);
        servicoPassageiro.setCompany(company);
        servicoPassageiro.setServicoRecorrente(servico);
        servicoPassageiro.setPassageiro(passageiro);
        servicoPassageiro.setAtivo(ativo);
        servicoPassageiro.setOrdemPadrao(ordemPadrao);
        return servicoPassageiro;
    }

}
