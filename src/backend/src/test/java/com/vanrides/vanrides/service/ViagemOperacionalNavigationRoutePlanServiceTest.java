package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.NavigationRoutePlanResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.PresencaRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.vanrides.vanrides.testsupport.NavigationRoutePlanTestFixture.navigationRoutePlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViagemOperacionalNavigationRoutePlanServiceTest {

    @Mock
    private ViagemRepository viagemRepository;
    @Mock
    private ViagemParadaRepository viagemParadaRepository;
    @Mock
    private PresencaRepository presencaRepository;
    @Mock
    private PresencaServicoRepository presencaServicoRepository;
    @Mock
    private ViagemEtaCalculator viagemEtaCalculator;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ViagemProvisioningService viagemProvisioningService;
    @Mock
    private ViagemRotaGeograficaService viagemRotaGeograficaService;
    @Mock
    private ViagemRealtimePublisherService viagemRealtimePublisherService;
    @Mock
    private ViagemTrackingStateService viagemTrackingStateService;

    @InjectMocks
    private ViagemOperacionalService viagemOperacionalService;

    @Test
    void shouldExposePromotedNavigationRoutePlanOnDriverTripPayload() {
        Viagem viagem = viagem();
        ViagemParada parada = parada(viagem, passageiro());
        NavigationRoutePlanResponse routePlan = navigationRoutePlan();
        ViagemRotaGeograficaResponse rotaGeografica = new ViagemRotaGeograficaResponse(
                "MAPS_PLATFORM_VALHALLA", "encoded-polyline", null, List.of(), List.of(), 1, null, null
        ).withNavigationRoutePlan(routePlan);

        when(viagemProvisioningService.buscarOuCriarViagemAutomaticaDoMotorista(66L, 7L, LocalDate.of(2026, 3, 12)))
                .thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L)).thenReturn(List.of(parada));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(List.of(21L), 66L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(viagem, List.of(parada), null))
                .thenReturn(rotaGeografica);

        var response = viagemOperacionalService.buscarViagemDoMotorista(7L, LocalDate.of(2026, 3, 12), 66L);

        assertThat(response).isPresent();
        assertThat(response.get().getNavigationRoutePlan()).isSameAs(routePlan);
        assertThat(response.get().getNavigationRoutePlan().getCoverageStatus()).isEqualTo("validada");
        assertThat(response.get().getNavigationRoutePlan().getOfflinePackStatus()).isEqualTo("ready");
        assertThat(response.get().getNavigationRoutePlan().getInstructions())
                .extracting("voiceText")
                .containsExactly("Em 200 metros, siga pela Avenida Ipiranga.");
    }

    private Viagem viagem() {
        Company company = new Company("VanRides Default", "default");
        company.setId(66L);
        Motorista motorista = new Motorista();
        motorista.setId(7L);
        motorista.setNome("Motorista");
        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(10L);
        rotaBase.setNome("Rota Escolar");
        Viagem viagem = new Viagem();
        viagem.setId(30L);
        viagem.setCompany(company);
        viagem.setMotorista(motorista);
        viagem.setRotaBase(rotaBase);
        viagem.setDataOperacao(LocalDate.of(2026, 3, 12));
        viagem.setStatus(ViagemStatus.PLANNED);
        return viagem;
    }

    private ViagemParada parada(Viagem viagem, Passageiro passageiro) {
        ViagemParada parada = new ViagemParada();
        parada.setId(300L);
        parada.setViagem(viagem);
        parada.setOrdem(1);
        parada.setPassageiro(passageiro);
        parada.setStatus(ViagemParadaStatus.PENDING);
        return parada;
    }

    private Passageiro passageiro() {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(21L);
        passageiro.setNome("Passageiro");
        return passageiro;
    }
}
