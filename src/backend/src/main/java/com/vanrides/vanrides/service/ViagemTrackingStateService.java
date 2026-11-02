package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.TrackingStateResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ViagemTrackingStateService {

    private final LocalizacaoMotoristaService localizacaoMotoristaService;
    private final ViagemTrackingStateResolver viagemTrackingStateResolver;

    public ViagemTrackingStateService(LocalizacaoMotoristaService localizacaoMotoristaService,
                                      ViagemTrackingStateResolver viagemTrackingStateResolver) {
        this.localizacaoMotoristaService = localizacaoMotoristaService;
        this.viagemTrackingStateResolver = viagemTrackingStateResolver;
    }

    public TrackingStateResponse resolve(Viagem viagem,
                                         List<ViagemParada> paradas,
                                         ViagemRotaGeograficaResponse rotaGeografica,
                                         Long passengerId) {
        LocalizacaoMotorista latestLocation = buscarUltimaLocalizacao(viagem);
        return viagemTrackingStateResolver.resolve(viagem, paradas, rotaGeografica, latestLocation, passengerId);
    }

    private LocalizacaoMotorista buscarUltimaLocalizacao(Viagem viagem) {
        if (viagem.getMotorista() == null || viagem.getMotorista().getId() == null || viagem.getCompany() == null) {
            return null;
        }
        return localizacaoMotoristaService.buscarUltimaPorMotorista(
                viagem.getMotorista().getId(),
                viagem.getCompany().getId()
        );
    }
}
