package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PresencaRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminViagemManifestServiceTest {

    @Mock
    private ViagemRepository viagemRepository;
    @Mock
    private ViagemParadaRepository viagemParadaRepository;
    @Mock
    private ViagemProvisioningService viagemProvisioningService;
    @Mock
    private ViagemManifestSyncService viagemManifestSyncService;
    @Mock
    private MotoristaRepository motoristaRepository;
    @Mock
    private PresencaRepository presencaRepository;
    @Mock
    private PresencaServicoRepository presencaServicoRepository;
    @Mock
    private ViagemEtaCalculator viagemEtaCalculator;
    @Mock
    private ViagemRealtimePublisherService viagemRealtimePublisherService;
    @Mock
    private ViagemRotaGeograficaService viagemRotaGeograficaService;
    @Mock
    private ViagemTrackingStateService viagemTrackingStateService;

    @InjectMocks
    private CompanyAdminViagemService companyAdminViagemService;

    @Test
    void shouldSyncServiceTripManifestAndPublishRealtimeSnapshot() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        RotaBase rotaBase = rotaBase(10L, company, motorista);
        ServicoRecorrente servico = servico(15L, company, rotaBase, motorista);
        Passageiro passageiro1 = passageiro(21L, company, motorista);
        Passageiro passageiro2 = passageiro(22L, company, motorista);
        Viagem viagem = viagem(company, motorista, rotaBase);
        viagem.setId(31L);
        viagem.setServicoRecorrente(servico);
        viagem.setServicoNomeSnapshot("Linha Manhã");
        viagem.setDataOperacao(LocalDate.of(2026, 3, 12));
        viagem.setStatus(ViagemStatus.PLANNED);
        viagem.setRotaVersao(4);
        ViagemParada parada1 = parada(51L, viagem, passageiro1, 1, 12);
        ViagemParada parada2 = parada(52L, viagem, passageiro2, 2, 18);

        when(viagemManifestSyncService.sincronizarManifesto(66L, 31L)).thenReturn(viagem);
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(31L))
                .thenReturn(List.of(parada1, parada2));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L, 22L), 66L, 15L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of());

        var detail = companyAdminViagemService.sincronizarManifesto(66L, 31L);

        assertThat(detail.getId()).isEqualTo(31L);
        assertThat(detail.getRotaVersao()).isEqualTo(4);
        assertThat(detail.getQuantidadeParadas()).isEqualTo(2);
        assertThat(detail.getParadas()).extracting(paradaResponse -> paradaResponse.getPassageiro().getId())
                .containsExactly(21L, 22L);
        verify(viagemManifestSyncService).sincronizarManifesto(66L, 31L);
        verify(viagemRealtimePublisherService).publishTripSnapshot(viagem, List.of(parada1, parada2));
    }

    @Test
    void shouldPreviewServiceTripManifestSync() {
        when(viagemManifestSyncService.previewSincronizacaoManifesto(66L, 31L))
                .thenReturn(new ViagemManifestSyncService.ManifestSyncPreview(
                        31L,
                        15L,
                        "Linha Manhã",
                        LocalDate.of(2026, 3, 12),
                        2,
                        3,
                        2,
                        1,
                        1,
                        List.of(
                                new ViagemManifestSyncService.ManifestSyncPassenger(23L, "Novo Passageiro", false, null, 2),
                                new ViagemManifestSyncService.ManifestSyncPassenger(24L, "Menor Passageiro", true, null, 3)
                        ),
                        List.of(new ViagemManifestSyncService.ManifestSyncPassenger(21L, "Sai da rota", false, 1, null)),
                        List.of(new ViagemManifestSyncService.ManifestSyncPassenger(22L, "Permanece", false, 2, 1))
                ));

        var preview = companyAdminViagemService.previewSincronizacaoManifesto(66L, 31L);

        assertThat(preview.viagemId()).isEqualTo(31L);
        assertThat(preview.servicoId()).isEqualTo(15L);
        assertThat(preview.paradasAtuais()).isEqualTo(2);
        assertThat(preview.paradasAposSincronizacao()).isEqualTo(3);
        assertThat(preview.mudancas()).isEqualTo(3);
        assertThat(preview.semMudancas()).isFalse();
        assertThat(preview.passageirosAdicionados()).extracting(passenger -> passenger.passageiroId())
                .containsExactly(23L, 24L);
        assertThat(preview.passageirosAdicionados().get(1).menorDeIdade()).isTrue();
        assertThat(preview.passageirosRemovidos().get(0).ordemAtual()).isEqualTo(1);
        assertThat(preview.passageirosMantidos().get(0).ordemNova()).isEqualTo(1);
        verify(viagemManifestSyncService).previewSincronizacaoManifesto(66L, 31L);
        verifyNoInteractions(viagemRealtimePublisherService);
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

    private RotaBase rotaBase(Long id, Company company, Motorista motorista) {
        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(id);
        rotaBase.setCompany(company);
        rotaBase.setMotorista(motorista);
        rotaBase.setNome("Rota Escolar");
        return rotaBase;
    }

    private ServicoRecorrente servico(Long id, Company company, RotaBase rotaBase, Motorista motorista) {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(id);
        servico.setCompany(company);
        servico.setRotaBaseLegada(rotaBase);
        servico.setMotoristaPadrao(motorista);
        servico.setNome("Linha Manhã");
        servico.setPoliticaPresenca(PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        return servico;
    }

    private Viagem viagem(Company company, Motorista motorista, RotaBase rotaBase) {
        Viagem viagem = new Viagem();
        viagem.setCompany(company);
        viagem.setMotorista(motorista);
        viagem.setRotaBase(rotaBase);
        viagem.setStatus(ViagemStatus.PLANNED);
        return viagem;
    }

    private Passageiro passageiro(Long id, Company company, Motorista motorista) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Passageiro " + id);
        passageiro.setCompany(company);
        passageiro.setMotorista(motorista);
        return passageiro;
    }

    private ViagemParada parada(Long id, Viagem viagem, Passageiro passageiro, int ordem, int etaMinutos) {
        ViagemParada parada = new ViagemParada();
        parada.setId(id);
        parada.setViagem(viagem);
        parada.setPassageiro(passageiro);
        parada.setOrdem(ordem);
        parada.setEtaMinutos(etaMinutos);
        return parada;
    }
}
