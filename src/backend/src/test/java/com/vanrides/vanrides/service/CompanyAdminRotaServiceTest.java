package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminRotaBaseCreateRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.RotaBaseParada;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.RotaBaseParadaRepository;
import com.vanrides.vanrides.repository.RotaBaseRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminRotaServiceTest {

    @Mock
    private RotaBaseRepository rotaBaseRepository;

    @Mock
    private RotaBaseParadaRepository rotaBaseParadaRepository;

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private ViagemRepository viagemRepository;

    @InjectMocks
    private CompanyAdminRotaService companyAdminRotaService;

    @Test
    void shouldCreateRouteTemplateWithOrderedStops() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        Passageiro alunoA = passageiro(21L, motorista);
        Passageiro alunoB = passageiro(22L, motorista);

        when(motoristaRepository.findByIdAndCompanyId(7L, 66L)).thenReturn(Optional.of(motorista));
        when(passageiroRepository.findAllByIdInAndCompanyId(List.of(21L, 22L), 66L))
                .thenReturn(List.of(alunoA, alunoB));
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(rotaBaseRepository.save(any(RotaBase.class))).thenAnswer(invocation -> {
            RotaBase rotaBase = invocation.getArgument(0);
            rotaBase.setId(10L);
            return rotaBase;
        });
        when(rotaBaseParadaRepository.save(any(RotaBaseParada.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminRotaBaseCreateRequest request = new AdminRotaBaseCreateRequest();
        request.setNome("Rota Escolar");
        request.setMotoristaId(7L);
        request.setPassageiroIds(List.of(21L, 22L));

        var response = companyAdminRotaService.criarRotaBase(66L, request);

        ArgumentCaptor<RotaBaseParada> captor = ArgumentCaptor.forClass(RotaBaseParada.class);
        ArgumentCaptor<RotaBase> rotaCaptor = ArgumentCaptor.forClass(RotaBase.class);
        verify(rotaBaseParadaRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        verify(rotaBaseRepository).save(rotaCaptor.capture());

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getQuantidadeParadas()).isEqualTo(2);
        assertThat(response.isGeracaoAutomaticaAtiva()).isTrue();
        assertThat(response.getDiasOperacao()).containsExactly(
                "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"
        );
        assertThat(captor.getAllValues()).extracting(RotaBaseParada::getOrdem).containsExactly(1, 2);
        assertThat(rotaCaptor.getValue().isGeracaoAutomaticaAtiva()).isTrue();
        assertThat(rotaCaptor.getValue().getDiasOperacaoAsSet()).isEqualTo(Set.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        ));
    }

    @Test
    void shouldRejectRouteTemplateWhenPassengerBelongsToDifferentDriver() {
        Motorista motoristaA = motorista(7L);
        Motorista motoristaB = motorista(8L);
        Passageiro aluno = passageiro(21L, motoristaB);

        when(companyRepository.findById(66L)).thenReturn(Optional.of(company(66L)));
        when(motoristaRepository.findByIdAndCompanyId(7L, 66L)).thenReturn(Optional.of(motoristaA));
        when(passageiroRepository.findAllByIdInAndCompanyId(List.of(21L), 66L)).thenReturn(List.of(aluno));

        AdminRotaBaseCreateRequest request = new AdminRotaBaseCreateRequest();
        request.setNome("Rota Escolar");
        request.setMotoristaId(7L);
        request.setPassageiroIds(List.of(21L));

        assertThatThrownBy(() -> companyAdminRotaService.criarRotaBase(66L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("precisam estar vinculados");
    }

    @Test
    void shouldRejectInvalidOperationDayWhenCreatingRouteTemplate() {
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company(66L)));
        Motorista motorista = motorista(7L);
        Passageiro aluno = passageiro(21L, motorista);

        when(motoristaRepository.findByIdAndCompanyId(7L, 66L)).thenReturn(Optional.of(motorista));
        when(passageiroRepository.findAllByIdInAndCompanyId(List.of(21L), 66L)).thenReturn(List.of(aluno));

        AdminRotaBaseCreateRequest request = new AdminRotaBaseCreateRequest();
        request.setNome("Rota Escolar");
        request.setMotoristaId(7L);
        request.setPassageiroIds(List.of(21L));
        request.setDiasOperacao(List.of("MONDAY", "FUNDAY"));

        assertThatThrownBy(() -> companyAdminRotaService.criarRotaBase(66L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Dia de operação inválido");
    }

    @Test
    void shouldUpdateRouteTemplateAndReplaceStops() {
        Motorista motoristaAtual = motorista(7L);
        Motorista novoMotorista = motorista(8L);
        Passageiro alunoA = passageiro(21L, novoMotorista);
        Passageiro alunoB = passageiro(22L, novoMotorista);
        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(10L);
        rotaBase.setCompany(company(66L));
        rotaBase.setMotorista(motoristaAtual);
        rotaBase.setNome("Rota Antiga");
        rotaBase.setAtivo(true);
        rotaBase.setAtiva(true);

        RotaBaseParada paradaExistente = new RotaBaseParada();
        paradaExistente.setRotaBase(rotaBase);
        paradaExistente.setPassageiro(passageiro(20L, motoristaAtual));
        paradaExistente.setOrdem(1);

        when(rotaBaseRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(rotaBase));
        when(motoristaRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(novoMotorista));
        when(passageiroRepository.findAllByIdInAndCompanyId(List.of(21L, 22L), 66L))
                .thenReturn(List.of(alunoA, alunoB));
        when(rotaBaseParadaRepository.findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(10L))
                .thenReturn(List.of(paradaExistente));
        when(rotaBaseRepository.save(any(RotaBase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rotaBaseParadaRepository.save(any(RotaBaseParada.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminRotaBaseCreateRequest request = new AdminRotaBaseCreateRequest();
        request.setNome("Rota Atualizada");
        request.setMotoristaId(8L);
        request.setPassageiroIds(List.of(21L, 22L));
        request.setDiasOperacao(List.of("MONDAY", "FRIDAY"));
        request.setDestinoFinalNome("Escola Central");
        request.setDestinoFinalEndereco("Rua Principal, 100");
        request.setDestinoFinalLatitude(-30.01);
        request.setDestinoFinalLongitude(-51.22);

        var response = companyAdminRotaService.editarRotaBase(66L, 10L, request);

        ArgumentCaptor<RotaBaseParada> paradaCaptor = ArgumentCaptor.forClass(RotaBaseParada.class);
        verify(rotaBaseParadaRepository).deleteAll(List.of(paradaExistente));
        verify(rotaBaseParadaRepository, org.mockito.Mockito.times(2)).save(paradaCaptor.capture());
        verify(rotaBaseRepository).save(rotaBase);

        assertThat(response.getNome()).isEqualTo("Rota Atualizada");
        assertThat(response.getQuantidadeParadas()).isEqualTo(2);
        assertThat(rotaBase.getMotorista()).isEqualTo(novoMotorista);
        assertThat(rotaBase.getDiasOperacaoAsSet()).isEqualTo(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        assertThat(rotaBase.getDestinoFinalNome()).isEqualTo("Escola Central");
        assertThat(paradaCaptor.getAllValues()).extracting(RotaBaseParada::getOrdem).containsExactly(1, 2);
        assertThat(paradaCaptor.getAllValues()).extracting(item -> item.getPassageiro().getId()).containsExactly(21L, 22L);
    }

    @Test
    void shouldRejectInactivationWhenTripIsInProgress() {
        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(10L);
        rotaBase.setCompany(company(66L));
        rotaBase.setAtivo(true);
        rotaBase.setAtiva(true);

        when(rotaBaseRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(rotaBase));
        when(viagemRepository.existsByRotaBaseIdAndStatusAndCompanyId(10L, ViagemStatus.IN_PROGRESS, 66L))
                .thenReturn(true);

        assertThatThrownBy(() -> companyAdminRotaService.inativarRotaBase(66L, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("viagem em andamento");

        verify(rotaBaseRepository, never()).save(any(RotaBase.class));
    }

    @Test
    void shouldInactivateRouteTemplateWhenNoTripIsInProgress() {
        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(10L);
        rotaBase.setCompany(company(66L));
        rotaBase.setAtivo(true);
        rotaBase.setAtiva(true);

        when(rotaBaseRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(rotaBase));
        when(viagemRepository.existsByRotaBaseIdAndStatusAndCompanyId(10L, ViagemStatus.IN_PROGRESS, 66L))
                .thenReturn(false);

        companyAdminRotaService.inativarRotaBase(66L, 10L);

        assertThat(rotaBase.isAtivo()).isFalse();
        assertThat(rotaBase.isAtiva()).isFalse();
        verify(rotaBaseRepository).save(rotaBase);
        verify(viagemRepository).existsByRotaBaseIdAndStatusAndCompanyId(10L, ViagemStatus.IN_PROGRESS, 66L);
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
}
