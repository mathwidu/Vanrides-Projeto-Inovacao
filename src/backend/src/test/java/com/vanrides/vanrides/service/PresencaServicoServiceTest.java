package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.PresencaServicoRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.PresencaServico;
import com.vanrides.vanrides.model.PresencaServicoOrigem;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresencaServicoServiceTest {

    @Mock
    private PresencaServicoRepository presencaServicoRepository;

    @Mock
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Mock
    private ServicoPassageiroRepository servicoPassageiroRepository;

    @Mock
    private PassageiroRepository passageiroRepository;

    @InjectMocks
    private PresencaServicoService service;

    @Test
    void shouldListAvailableServicesWithCurrentPresenceStatus() {
        LocalDate monday = LocalDate.of(2026, 5, 25);
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L);
        ServicoRecorrente servico = servico(10L, company);
        PresencaServico presenca = presenca(servico, passageiro, monday, PresencaServicoStatus.CONFIRMED);
        presenca.setId(40L);

        when(passageiroRepository.findByIdAndCompanyId(21L, 66L)).thenReturn(Optional.of(passageiro));
        when(presencaServicoRepository.findByPassageiroIdAndCompanyIdAndDataOperacao(21L, 66L, monday))
                .thenReturn(List.of(presenca));
        when(servicoPassageiroRepository.findAllAtivosWithServicoByPassageiroIdAndCompanyId(21L, 66L))
                .thenReturn(List.of(servicoPassageiro(servico, passageiro)));

        var response = service.listarServicosDisponiveis(66L, 21L, monday);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getServicoId()).isEqualTo(10L);
        assertThat(response.get(0).getPresencaId()).isEqualTo(40L);
        assertThat(response.get(0).getPresencaStatus()).isEqualTo(PresencaServicoStatus.CONFIRMED);
    }

    @Test
    void shouldCreateConfirmedPresenceForEligiblePassengerAndServiceDate() {
        LocalDate monday = LocalDate.of(2026, 5, 25);
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L);
        ServicoRecorrente servico = servico(10L, company);

        when(servicoRecorrenteRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(servico));
        when(passageiroRepository.findByIdAndCompanyId(21L, 66L)).thenReturn(Optional.of(passageiro));
        when(servicoPassageiroRepository.findByServicoRecorrenteIdAndPassageiroIdAndCompanyIdAndAtivoTrue(10L, 21L, 66L))
                .thenReturn(Optional.of(servicoPassageiro(servico, passageiro)));
        when(presencaServicoRepository.findByServicoRecorrenteIdAndPassageiroIdAndCompanyIdAndDataOperacao(10L, 21L, 66L, monday))
                .thenReturn(Optional.empty());
        when(presencaServicoRepository.save(any(PresencaServico.class))).thenAnswer(invocation -> {
            PresencaServico presenca = invocation.getArgument(0);
            presenca.setId(40L);
            return presenca;
        });

        PresencaServicoRequest request = new PresencaServicoRequest();
        request.setServicoId(10L);
        request.setPassageiroId(21L);
        request.setDataOperacao(monday);
        request.setStatus("CONFIRMED");

        var response = service.criarPresenca(66L, request, PresencaServicoOrigem.PASSENGER);

        ArgumentCaptor<PresencaServico> captor = ArgumentCaptor.forClass(PresencaServico.class);
        verify(presencaServicoRepository).save(captor.capture());
        assertThat(response.getId()).isEqualTo(40L);
        assertThat(response.getStatus()).isEqualTo(PresencaServicoStatus.CONFIRMED);
        assertThat(response.getOrigem()).isEqualTo(PresencaServicoOrigem.PASSENGER);
        assertThat(captor.getValue().getServicoRecorrente()).isEqualTo(servico);
        assertThat(captor.getValue().getPassageiro()).isEqualTo(passageiro);
    }

    @Test
    void shouldRejectDuplicatePresenceForSameServicePassengerAndDate() {
        LocalDate monday = LocalDate.of(2026, 5, 25);
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L);
        ServicoRecorrente servico = servico(10L, company);

        when(servicoRecorrenteRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(servico));
        when(passageiroRepository.findByIdAndCompanyId(21L, 66L)).thenReturn(Optional.of(passageiro));
        when(servicoPassageiroRepository.findByServicoRecorrenteIdAndPassageiroIdAndCompanyIdAndAtivoTrue(10L, 21L, 66L))
                .thenReturn(Optional.of(servicoPassageiro(servico, passageiro)));
        when(presencaServicoRepository.findByServicoRecorrenteIdAndPassageiroIdAndCompanyIdAndDataOperacao(10L, 21L, 66L, monday))
                .thenReturn(Optional.of(presenca(servico, passageiro, monday, PresencaServicoStatus.CONFIRMED)));

        PresencaServicoRequest request = new PresencaServicoRequest();
        request.setServicoId(10L);
        request.setPassageiroId(21L);
        request.setDataOperacao(monday);
        request.setStatus("ABSENT");

        assertThatThrownBy(() -> service.criarPresenca(66L, request, PresencaServicoOrigem.GUARDIAN))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Já existe presença");
    }

    @Test
    void shouldRejectPresenceWhenServiceDoesNotOperateOnDate() {
        LocalDate sunday = LocalDate.of(2026, 5, 24);
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L);
        ServicoRecorrente servico = servico(10L, company);

        when(servicoRecorrenteRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(servico));
        when(passageiroRepository.findByIdAndCompanyId(21L, 66L)).thenReturn(Optional.of(passageiro));
        when(servicoPassageiroRepository.findByServicoRecorrenteIdAndPassageiroIdAndCompanyIdAndAtivoTrue(10L, 21L, 66L))
                .thenReturn(Optional.of(servicoPassageiro(servico, passageiro)));

        PresencaServicoRequest request = new PresencaServicoRequest();
        request.setServicoId(10L);
        request.setPassageiroId(21L);
        request.setDataOperacao(sunday);
        request.setStatus("CONFIRMED");

        assertThatThrownBy(() -> service.criarPresenca(66L, request, PresencaServicoOrigem.PASSENGER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("não opera");
    }

    @Test
    void shouldUpdatePresenceStatusToAbsent() {
        LocalDate monday = LocalDate.of(2026, 5, 25);
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L);
        ServicoRecorrente servico = servico(10L, company);
        PresencaServico presenca = presenca(servico, passageiro, monday, PresencaServicoStatus.CONFIRMED);
        presenca.setId(40L);

        when(presencaServicoRepository.findByIdAndCompanyIdWithDetails(40L, 66L)).thenReturn(Optional.of(presenca));
        when(presencaServicoRepository.save(presenca)).thenReturn(presenca);

        var response = service.atualizarStatus(66L, 40L, "ABSENT", PresencaServicoOrigem.PASSENGER);

        assertThat(response.getStatus()).isEqualTo(PresencaServicoStatus.ABSENT);
        assertThat(response.getOrigem()).isEqualTo(PresencaServicoOrigem.PASSENGER);
    }

    @Test
    void shouldRejectCompanyCancelFromPassengerOrigin() {
        LocalDate monday = LocalDate.of(2026, 5, 25);
        PresencaServico presenca = presenca(servico(10L, company(66L)), passageiro(21L), monday, PresencaServicoStatus.CONFIRMED);
        presenca.setId(40L);
        when(presencaServicoRepository.findByIdAndCompanyIdWithDetails(40L, 66L)).thenReturn(Optional.of(presenca));

        assertThatThrownBy(() -> service.atualizarStatus(66L, 40L, "CANCELED_BY_COMPANY", PresencaServicoOrigem.PASSENGER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Apenas a empresa");
    }

    private Company company(Long id) {
        Company company = new Company("VanRides Default", "default");
        company.setId(id);
        return company;
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
        servico.setNome("Ida manha");
        return servico;
    }

    private ServicoPassageiro servicoPassageiro(ServicoRecorrente servico, Passageiro passageiro) {
        ServicoPassageiro servicoPassageiro = new ServicoPassageiro();
        servicoPassageiro.setCompany(servico.getCompany());
        servicoPassageiro.setServicoRecorrente(servico);
        servicoPassageiro.setPassageiro(passageiro);
        servicoPassageiro.setAtivo(true);
        return servicoPassageiro;
    }

    private PresencaServico presenca(ServicoRecorrente servico,
                                     Passageiro passageiro,
                                     LocalDate data,
                                     PresencaServicoStatus status) {
        PresencaServico presenca = new PresencaServico();
        presenca.setCompany(servico.getCompany());
        presenca.setServicoRecorrente(servico);
        presenca.setPassageiro(passageiro);
        presenca.setDataOperacao(data);
        presenca.setStatus(status);
        presenca.setOrigem(PresencaServicoOrigem.PASSENGER);
        return presenca;
    }
}
