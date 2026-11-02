package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

final class ViagemRouteAuditBuilder {

    private ViagemRouteAuditBuilder() {
    }

    static ViagemRotaGeograficaResponse addAudit(Viagem viagem,
                                                 List<ViagemParada> paradas,
                                                 ViagemRotaGeograficaResponse response) {
        return new ViagemRotaGeograficaResponse(
                response.getProvider(),
                response.getPolyline(),
                response.getNavegacao(),
                response.getWaypoints(),
                response.getLegs(),
                response.getFilaTotalParadas(),
                response.getPosicaoPassageiroNaFila(),
                response.getParadasAntesDoPassageiro(),
                response.getNavegacaoRestante(),
                response.getCalculadaEm(),
                response.isSnapshotOperacional(),
                buildSnapshotKey(viagem),
                buildRouteInputHash(viagem, paradas),
                response.getRouteSnapshotStatus(),
                response.getRouteRecomputeReason(),
                response.getRouteRecomputeAllowedAfter()
        );
    }

    private static String buildSnapshotKey(Viagem viagem) {
        return "company:" + value(viagem.getCompany() == null ? null : viagem.getCompany().getId())
                + ":viagem:" + value(viagem.getId())
                + ":rota:" + value(viagem.getRotaVersao());
    }

    private static String buildRouteInputHash(Viagem viagem, List<ViagemParada> paradas) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalizeRouteInput(viagem, paradas).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 indisponivel para auditoria de rota", error);
        }
    }

    private static String normalizeRouteInput(Viagem viagem, List<ViagemParada> paradas) {
        StringBuilder normalized = new StringBuilder();
        append(normalized, "companyId", viagem.getCompany() == null ? null : viagem.getCompany().getId());
        append(normalized, "viagemId", viagem.getId());
        append(normalized, "rotaVersao", viagem.getRotaVersao());
        append(normalized, "dataOperacao", viagem.getDataOperacao());
        append(normalized, "motoristaId", viagem.getMotorista() == null ? null : viagem.getMotorista().getId());
        append(normalized, "rotaBaseId", viagem.getRotaBaseIdOrNull());
        append(normalized, "servicoId", viagem.getServicoRecorrenteIdOrNull());
        append(normalized, "destinoLatitude", resolveDestinationLatitude(viagem));
        append(normalized, "destinoLongitude", resolveDestinationLongitude(viagem));

        List<ViagemParada> orderedStops = paradas == null
                ? List.of()
                : paradas.stream()
                        .sorted(Comparator
                                .comparing(ViagemParada::getOrdem, Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(ViagemParada::getId, Comparator.nullsLast(Long::compareTo)))
                        .toList();
        for (ViagemParada parada : orderedStops) {
            append(normalized, "paradaId", parada.getId());
            append(normalized, "ordem", parada.getOrdem());
            append(normalized, "passageiroId", parada.getPassageiro() == null ? null : parada.getPassageiro().getId());
            append(normalized, "passageiroLatitude", parada.getPassageiro() == null ? null : parada.getPassageiro().getLatitude());
            append(normalized, "passageiroLongitude", parada.getPassageiro() == null ? null : parada.getPassageiro().getLongitude());
        }
        return normalized.toString();
    }

    private static Double resolveDestinationLatitude(Viagem viagem) {
        if (viagem.getDestinoFinalLatitudeSnapshot() != null) {
            return viagem.getDestinoFinalLatitudeSnapshot();
        }
        return viagem.getRotaBase() == null ? null : viagem.getRotaBase().getDestinoFinalLatitude();
    }

    private static Double resolveDestinationLongitude(Viagem viagem) {
        if (viagem.getDestinoFinalLongitudeSnapshot() != null) {
            return viagem.getDestinoFinalLongitudeSnapshot();
        }
        return viagem.getRotaBase() == null ? null : viagem.getRotaBase().getDestinoFinalLongitude();
    }

    private static void append(StringBuilder normalized, String label, Object rawValue) {
        normalized.append(label)
                .append('=')
                .append(value(rawValue))
                .append('|');
    }

    private static String value(Object rawValue) {
        if (rawValue instanceof Double coordinate) {
            return String.format(Locale.ROOT, "%.6f", coordinate);
        }
        return rawValue == null ? "" : rawValue.toString();
    }
}
