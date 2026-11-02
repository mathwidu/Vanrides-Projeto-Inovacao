package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ViagemRotaStopPointResolverTest {

    @Test
    void shouldFilterGeocodedStopsAndSortByStopOrder() {
        ViagemParada terceira = parada(303L, 3, passageiro(23L, "Aluno Tres", -29.7090, -51.1290), ViagemParadaStatus.PENDING);
        ViagemParada semCoordenada = parada(302L, 2, passageiro(22L, "Aluno Dois", null, -51.1230), ViagemParadaStatus.PENDING);
        ViagemParada primeira = parada(301L, 1, passageiro(21L, "Aluno Um", -29.7030, -51.1170), ViagemParadaStatus.COMPLETED);

        List<ViagemRotaStopPoint> stopPoints = ViagemRotaStopPointResolver.resolve(
                List.of(terceira, semCoordenada, primeira),
                21L
        );

        assertThat(stopPoints).extracting(ViagemRotaStopPoint::paradaId).containsExactly(301L, 303L);
        assertThat(stopPoints).extracting(ViagemRotaStopPoint::ordem).containsExactly(1, 3);
        assertThat(stopPoints).extracting(ViagemRotaStopPoint::nome).containsExactly("Aluno Um", "Aluno Tres");
        assertThat(stopPoints.get(0).paradaDoPassageiro()).isTrue();
        assertThat(stopPoints.get(1).proximaParada()).isTrue();
    }

    @Test
    void shouldMarkTargetPassengerOnlyWhenPassengerIdMatches() {
        ViagemParada primeira = parada(301L, 1, passageiro(21L, "Aluno Um", -29.7030, -51.1170), ViagemParadaStatus.PENDING);
        ViagemParada segunda = parada(302L, 2, passageiro(22L, "Aluno Dois", -29.7060, -51.1230), ViagemParadaStatus.PENDING);

        List<ViagemRotaStopPoint> stopPoints = ViagemRotaStopPointResolver.resolve(
                List.of(primeira, segunda),
                22L
        );

        assertThat(stopPoints).extracting(ViagemRotaStopPoint::proximaParada).containsExactly(true, false);
        assertThat(stopPoints).extracting(ViagemRotaStopPoint::paradaDoPassageiro).containsExactly(false, true);
    }

    private ViagemParada parada(Long id, int ordem, Passageiro passageiro, ViagemParadaStatus status) {
        ViagemParada parada = new ViagemParada();
        parada.setId(id);
        parada.setOrdem(ordem);
        parada.setPassageiro(passageiro);
        parada.setStatus(status);
        return parada;
    }

    private Passageiro passageiro(Long id, String nome, Double latitude, Double longitude) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome(nome);
        passageiro.setLatitude(latitude);
        passageiro.setLongitude(longitude);
        return passageiro;
    }
}
