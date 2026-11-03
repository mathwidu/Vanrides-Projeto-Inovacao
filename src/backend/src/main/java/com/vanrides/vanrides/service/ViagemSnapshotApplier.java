package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.Viagem;
import org.springframework.stereotype.Component;

@Component
public class ViagemSnapshotApplier {

    public void apply(Viagem viagem, RotaBase rotaBase, ServicoRecorrente servico) {
        if (servico != null) {
            viagem.setServicoNomeSnapshot(servico.getNome());
            viagem.setTipoOperacaoSnapshot(servico.getTipoOperacao());
            viagem.setDestinoFinalNomeSnapshot(servico.getDestinoFinalNome());
            viagem.setDestinoFinalEnderecoSnapshot(servico.getDestinoFinalEndereco());
            viagem.setDestinoFinalLatitudeSnapshot(servico.getDestinoFinalLatitude());
            viagem.setDestinoFinalLongitudeSnapshot(servico.getDestinoFinalLongitude());
            return;
        }
        if (rotaBase != null) {
            viagem.setServicoNomeSnapshot(rotaBase.getNome());
            viagem.setDestinoFinalNomeSnapshot(rotaBase.getDestinoFinalNome());
            viagem.setDestinoFinalEnderecoSnapshot(rotaBase.getDestinoFinalEndereco());
            viagem.setDestinoFinalLatitudeSnapshot(rotaBase.getDestinoFinalLatitude());
            viagem.setDestinoFinalLongitudeSnapshot(rotaBase.getDestinoFinalLongitude());
        }
    }
}
