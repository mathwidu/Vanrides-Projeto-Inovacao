package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminServicoPassageirosUpdateRequest;
import com.vanrides.vanrides.dto.AdminServicoRecorrenteRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.TipoOperacaoServico;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminServicoRecorrenteServiceTest {

    @Mock
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Mock
    private ServicoPassageiroRepository servicoPassageiroRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private ViagemRepository viagemRepository;

    @InjectMocks
    private CompanyAdminServicoRecorrenteService service;

    @Test
    void shouldCreateRecurringServiceWithoutDriverOrPassengers() {
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company(66L)));
        when(servicoRecorrenteRepository.save(any(ServicoRecorrente.class))).thenAnswer(invocation -> {
            ServicoRecorrente servico = invocation.getArgument(0);
            servico.setId(10L);
            return servico;
        });

        AdminServicoRecorrenteRequest request = new AdminServicoRecorrenteRequest();
        request.setNome("Volta tarde");
        request.setTipoOperacao("VOLTA");
        request.setPoliticaPresenca("EXIGE_CONFIRMACAO");
        request.setDiasOperacao(List.of("MONDAY", "WEDNESDAY"));
        request.setHorarioPartida(LocalTime.of(17, 30));
        request.setCapacidadePlanejada(12);

        var response = service.criarServico(66L, request);

        ArgumentCaptor<ServicoRecorrente> captor = ArgumentCaptor.forClass(ServicoRecorrente.class);
        verify(servicoRecorrenteRepository).save(captor.capture());
        verify(servicoPassageiroRepository, never()).save(any(ServicoPassageiro.class));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getMotoristaPadrao()).isNull();
        assertThat(response.getQuantidadePassageiros()).isZero();
        assertThat(response.getTipoOperacao()).isEqualTo(TipoOperacaoServico.VOLTA);
        assertThat(response.getPoliticaPresenca()).isEqualTo(PoliticaPresencaServico.EXIGE_CONFIRMACAO);
        assertThat(response.getDiasOperacao()).containsExactly("MONDAY", "WEDNESDAY");
        assertThat(captor.getValue().getDiasOperacaoAsSet()).isEqualTo(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
    }

    @Test
    void shouldAllowEligiblePassengersThatAreNotLinkedToDefaultDriver() {
        Company company = company(66L);
        Motorista motoristaPadrao = motorista(7L);
        Motorista motoristaLegadoDiferente = motorista(8L);
        Passageiro alunoA = passageiro(21L, motoristaLegadoDiferente);
        Passageiro alunoB = passageiro(22L, null);

        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(motoristaRepository.findByIdAndCompanyId(7L, 66L)).thenReturn(Optional.of(motoristaPadrao));
        when(passageiroRepository.findAllByIdInAndCompanyId(List.of(21L, 22L), 66L))
                .thenReturn(List.of(alunoA, alunoB));
        when(servicoPassageiroRepository.findAllByServicoRecorrenteIdAndCompanyId(10L, 66L))
                .thenReturn(List.of());
        when(servicoRecorrenteRepository.save(any(ServicoRecorrente.class))).thenAnswer(invocation -> {
            ServicoRecorrente servico = invocation.getArgument(0);
            servico.setId(10L);
            return servico;
        });
        when(servicoPassageiroRepository.save(any(ServicoPassageiro.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminServicoRecorrenteRequest request = new AdminServicoRecorrenteRequest();
        request.setNome("Ida manha");
        request.setMotoristaPadraoId(7L);
        request.setPassageiroIds(List.of(21L, 22L));

        var response = service.criarServico(66L, request);

        ArgumentCaptor<ServicoPassageiro> captor = ArgumentCaptor.forClass(ServicoPassageiro.class);
        verify(servicoPassageiroRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        assertThat(response.getMotoristaPadrao().getId()).isEqualTo(7L);
        assertThat(response.getPassageiroIds()).containsExactly(21L, 22L);
        assertThat(captor.getAllValues()).extracting(ServicoPassageiro::getOrdemPadrao).containsExactly(1, 2);
        assertThat(captor.getAllValues()).extracting(item -> item.getPassageiro().getId()).containsExactly(21L, 22L);
    }

    @Test
    void shouldPreserveEligibilityRowsWhenReplacingPassengersForService() {
        Company company = company(66L);
        ServicoRecorrente servico = servico(10L, company);
        Passageiro alunoA = passageiro(21L, null);
        Passageiro alunoB = passageiro(22L, null);
        ServicoPassageiro removed = servicoPassageiro(servico, passageiro(20L, null), 1);
        ServicoPassageiro reactivated = servicoPassageiro(servico, alunoA, 3);
        reactivated.setAtivo(false);
        reactivated.setDataFim(java.time.LocalDate.parse("2026-05-20"));

        when(servicoRecorrenteRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(servico));
        when(servicoPassageiroRepository.findAllByServicoRecorrenteIdAndCompanyId(10L, 66L))
                .thenReturn(List.of(removed, reactivated));
        when(passageiroRepository.findAllByIdInAndCompanyId(List.of(21L, 22L), 66L))
                .thenReturn(List.of(alunoA, alunoB));
        when(servicoPassageiroRepository.save(any(ServicoPassageiro.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(10L, 66L))
                .thenReturn(List.of(reactivated, servicoPassageiro(servico, alunoB, 2)));

        AdminServicoPassageirosUpdateRequest request = new AdminServicoPassageirosUpdateRequest();
        request.setPassageiroIds(List.of(21L, 22L));

        var response = service.substituirPassageiros(66L, 10L, request);

        ArgumentCaptor<ServicoPassageiro> captor = ArgumentCaptor.forClass(ServicoPassageiro.class);
        verify(servicoPassageiroRepository, never()).deleteAll(any());
        verify(servicoPassageiroRepository).save(captor.capture());
        assertThat(response).hasSize(2);
        assertThat(response).extracting(item -> item.getPassageiro().getId()).containsExactly(21L, 22L);
        assertThat(removed.isAtivo()).isFalse();
        assertThat(removed.getDataFim()).isNotNull();
        assertThat(reactivated.isAtivo()).isTrue();
        assertThat(reactivated.getDataInicio()).isNotNull();
        assertThat(reactivated.getDataFim()).isNull();
        assertThat(reactivated.getOrdemPadrao()).isEqualTo(1);
        assertThat(captor.getValue().getPassageiro().getId()).isEqualTo(22L);
        assertThat(captor.getValue().getDataInicio()).isNotNull();
        assertThat(captor.getValue().getOrdemPadrao()).isEqualTo(2);
    }

    @Test
    void shouldUpdateRecurringServiceWithoutReplacingPassengersWhenPassengerIdsAreOmitted() {
        Company company = company(66L);
        ServicoRecorrente servico = servico(10L, company);
        servico.setMotoristaPadrao(motorista(7L));
        when(servicoRecorrenteRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(servico));
        when(servicoRecorrenteRepository.save(any(ServicoRecorrente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(servicoPassageiroRepository.findPassageiroIdsByServicoIdAndCompanyIdOrderByOrdem(10L, 66L))
                .thenReturn(List.of(21L, 22L));

        AdminServicoRecorrenteRequest request = new AdminServicoRecorrenteRequest();
        request.setNome("Circular almoco");
        request.setTipoOperacao("CIRCULAR");
        request.setMotoristaPadraoId(null);
        request.setDiasOperacao(List.of("TUESDAY"));

        var response = service.editarServico(66L, 10L, request);

        assertThat(response.getNome()).isEqualTo("Circular almoco");
        assertThat(response.getTipoOperacao()).isEqualTo(TipoOperacaoServico.CIRCULAR);
        assertThat(response.getMotoristaPadrao()).isNull();
        assertThat(response.getPassageiroIds()).containsExactly(21L, 22L);
        verify(servicoPassageiroRepository, never()).deleteAll(any());
    }

    @Test
    void shouldRejectDuplicateEligiblePassengers() {
        when(servicoRecorrenteRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(servico(10L, company(66L))));
        AdminServicoPassageirosUpdateRequest request = new AdminServicoPassageirosUpdateRequest();
        request.setPassageiroIds(List.of(21L, 21L));

        assertThatThrownBy(() -> service.substituirPassageiros(66L, 10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("sem duplicidade");

        verify(passageiroRepository, never()).findAllByIdInAndCompanyId(any(), any());
    }

    @Test
    void shouldRejectEligiblePassengerFromAnotherCompany() {
        when(servicoRecorrenteRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(servico(10L, company(66L))));
        when(passageiroRepository.findAllByIdInAndCompanyId(List.of(21L), 66L)).thenReturn(List.of());

        AdminServicoPassageirosUpdateRequest request = new AdminServicoPassageirosUpdateRequest();
        request.setPassageiroIds(List.of(21L));

        assertThatThrownBy(() -> service.substituirPassageiros(66L, 10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("não existem na empresa atual");
    }

    @Test
    void shouldInactivateService() {
        ServicoRecorrente servico = servico(10L, company(66L));
        servico.setAtivo(true);
        when(servicoRecorrenteRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(servico));
        when(viagemRepository.existsByServicoRecorrenteIdAndStatusAndCompanyId(10L, ViagemStatus.IN_PROGRESS, 66L))
                .thenReturn(false);

        service.inativarServico(66L, 10L);

        assertThat(servico.isAtivo()).isFalse();
        verify(servicoRecorrenteRepository).save(servico);
    }

    @Test
    void shouldRejectInactivationWhenServiceTripIsInProgress() {
        ServicoRecorrente servico = servico(10L, company(66L));
        servico.setAtivo(true);
        when(servicoRecorrenteRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(servico));
        when(viagemRepository.existsByServicoRecorrenteIdAndStatusAndCompanyId(10L, ViagemStatus.IN_PROGRESS, 66L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.inativarServico(66L, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("viagem em andamento");

        verify(servicoRecorrenteRepository, never()).save(any(ServicoRecorrente.class));
    }

    private Company company(Long id) {
        Company company = new Company("VanRides Default", "default");
        company.setId(id);
        return company;
    }

    private Motorista motorista(Long id) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome("Motorista " + id);
        motorista.setEmail("motorista" + id + "@vanrides.dev");
        motorista.setTelefone("51999990000");
        return motorista;
    }

    private Passageiro passageiro(Long id, Motorista motorista) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Passageiro " + id);
        passageiro.setEmail("passageiro" + id + "@vanrides.dev");
        passageiro.setTelefone("51999990001");
        passageiro.setMotorista(motorista);
        return passageiro;
    }

    private ServicoRecorrente servico(Long id, Company company) {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(id);
        servico.setCompany(company);
        servico.setNome("Ida manha");
        return servico;
    }

    private ServicoPassageiro servicoPassageiro(ServicoRecorrente servico, Passageiro passageiro, int ordem) {
        ServicoPassageiro servicoPassageiro = new ServicoPassageiro();
        servicoPassageiro.setId((long) ordem);
        servicoPassageiro.setCompany(servico.getCompany());
        servicoPassageiro.setServicoRecorrente(servico);
        servicoPassageiro.setPassageiro(passageiro);
        servicoPassageiro.setOrdemPadrao(ordem);
        servicoPassageiro.setAtivo(true);
        return servicoPassageiro;
    }
}
