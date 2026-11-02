package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerServiceEligibilityManagementServiceTest {

    @Mock
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Mock
    private ServicoPassageiroRepository servicoPassageiroRepository;

    @InjectMocks
    private PassengerServiceEligibilityManagementService service;

    @Test
    void shouldCreateEligibilityForEachRequestedService() {
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L, company);
        ServicoRecorrente ida = servico(10L, company);
        ServicoRecorrente volta = servico(11L, company);

        when(servicoRecorrenteRepository.findAllAtivosByIdInAndCompanyId(List.of(10L, 11L), 66L))
                .thenReturn(List.of(ida, volta));
        when(servicoPassageiroRepository
                .findByServicoRecorrenteIdAndPassageiroIdAndCompanyId(10L, 21L, 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository
                .findByServicoRecorrenteIdAndPassageiroIdAndCompanyId(11L, 21L, 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository.countByServicoRecorrenteIdAndCompanyIdAndAtivoTrue(10L, 66L)).thenReturn(2L);
        when(servicoPassageiroRepository.countByServicoRecorrenteIdAndCompanyIdAndAtivoTrue(11L, 66L)).thenReturn(0L);

        service.assignServiceEligibilities(passageiro, company, 66L, List.of(10L, 11L));

        ArgumentCaptor<ServicoPassageiro> captor = ArgumentCaptor.forClass(ServicoPassageiro.class);
        verify(servicoPassageiroRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(item -> item.getServicoRecorrente().getId())
                .containsExactly(10L, 11L);
        assertThat(captor.getAllValues()).extracting(ServicoPassageiro::getOrdemPadrao)
                .containsExactly(3, 1);
    }

    @Test
    void shouldReplaceEligibilityAndReactivateInactiveLink() {
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L, company);
        ServicoRecorrente ida = servico(10L, company);
        ServicoRecorrente volta = servico(11L, company);
        ServicoRecorrente extra = servico(12L, company);
        ServicoPassageiro activeOldLink = servicoPassageiro(company, ida, passageiro, true, 1);
        ServicoPassageiro inactiveReturnLink = servicoPassageiro(company, volta, passageiro, false, 4);

        when(servicoRecorrenteRepository.findAllAtivosByIdInAndCompanyId(List.of(11L, 12L), 66L))
                .thenReturn(List.of(volta, extra));
        when(servicoPassageiroRepository.findAllWithServicoByPassageiroIdAndCompanyId(21L, 66L))
                .thenReturn(List.of(activeOldLink, inactiveReturnLink));
        when(servicoPassageiroRepository.countByServicoRecorrenteIdAndCompanyIdAndAtivoTrue(12L, 66L))
                .thenReturn(2L);

        service.replaceServiceEligibilities(passageiro, 66L, List.of(11L, 12L));

        assertThat(activeOldLink.isAtivo()).isFalse();
        assertThat(activeOldLink.getDataFim()).isNotNull();
        assertThat(inactiveReturnLink.isAtivo()).isTrue();
        assertThat(inactiveReturnLink.getDataInicio()).isNotNull();
        assertThat(inactiveReturnLink.getDataFim()).isNull();
        ArgumentCaptor<ServicoPassageiro> captor = ArgumentCaptor.forClass(ServicoPassageiro.class);
        verify(servicoPassageiroRepository).save(captor.capture());
        assertThat(captor.getValue().getServicoRecorrente().getId()).isEqualTo(12L);
        assertThat(captor.getValue().getOrdemPadrao()).isEqualTo(3);
    }

    @Test
    void shouldRejectInvalidServiceListBeforeMutation() {
        assertThatThrownBy(() -> service.replaceServiceEligibilities(passageiro(21L, company(66L)), 66L, List.of(10L, 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ids válidos");

        verify(servicoPassageiroRepository, never()).save(any());
    }

    private Company company(Long id) {
        Company company = new Company("Empresa " + id, "empresa-" + id);
        company.setId(id);
        return company;
    }

    private Passageiro passageiro(Long id, Company company) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setCompany(company);
        passageiro.setNome("Passageiro " + id);
        passageiro.setEmail("passageiro" + id + "@vanrides.dev");
        return passageiro;
    }

    private ServicoRecorrente servico(Long id, Company company) {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(id);
        servico.setCompany(company);
        servico.setNome("Servico " + id);
        return servico;
    }

    private ServicoPassageiro servicoPassageiro(Company company,
                                                ServicoRecorrente servico,
                                                Passageiro passageiro,
                                                boolean ativo,
                                                Integer ordemPadrao) {
        ServicoPassageiro servicoPassageiro = new ServicoPassageiro();
        servicoPassageiro.setCompany(company);
        servicoPassageiro.setServicoRecorrente(servico);
        servicoPassageiro.setPassageiro(passageiro);
        servicoPassageiro.setAtivo(ativo);
        servicoPassageiro.setOrdemPadrao(ordemPadrao);
        return servicoPassageiro;
    }
}
