import 'package:flutter/material.dart';

import '../../../l10n/app_strings.dart';
import '../domain/trip_route_geometry_record.dart';
import '../domain/trip_tracking_record.dart';
import '../domain/trip_tracking_state_record.dart';

enum TripSignalRole { passenger, guardian, driver }

class TripTrackingPresentation {
  const TripTrackingPresentation({
    required this.label,
    required this.helper,
    required this.icon,
    required this.color,
    this.compactLabel,
    this.actionLabel = 'Atualizar',
  });

  final String label;
  final String helper;
  final IconData icon;
  final Color color;
  final String? compactLabel;
  final String actionLabel;
}

TripTrackingPresentation presentTripStatus(TripTrackingStatus status) {
  switch (status) {
    case TripTrackingStatus.planned:
      return TripTrackingPresentation(
        label: AppStrings.passageiro.aguardandoSaida,
        helper: AppStrings.passageiro.aguardandoSaidaHelper,
        icon: Icons.schedule_send_outlined,
        color: const Color(0xFF2D6CDF),
      );
    case TripTrackingStatus.inProgress:
      return TripTrackingPresentation(
        label: AppStrings.passageiro.emAndamento,
        helper: AppStrings.passageiro.emAndamentoHelper,
        icon: Icons.near_me_outlined,
        color: const Color(0xFF14866D),
      );
    case TripTrackingStatus.completed:
      return TripTrackingPresentation(
        label: AppStrings.passageiro.finalizada,
        helper: AppStrings.passageiro.finalizadaHelper,
        icon: Icons.flag_outlined,
        color: const Color(0xFF14866D),
      );
    case TripTrackingStatus.canceled:
      return TripTrackingPresentation(
        label: AppStrings.passageiro.cancelada,
        helper: AppStrings.passageiro.canceladaHelper,
        icon: Icons.cancel_outlined,
        color: const Color(0xFFB23A48),
      );
    case TripTrackingStatus.unknown:
      return TripTrackingPresentation(
        label: AppStrings.passageiro.statusIndisponivel,
        helper: AppStrings.passageiro.statusIndisponivelHelper,
        icon: Icons.help_outline,
        color: const Color(0xFF6D6A78),
      );
  }
}

String presentStopStatus(TripTrackingStopStatus status) {
  switch (status) {
    case TripTrackingStopStatus.pending:
      return AppStrings.passageiro.pendente;
    case TripTrackingStopStatus.completed:
      return AppStrings.passageiro.concluida;
    case TripTrackingStopStatus.skipped:
      return AppStrings.passageiro.pulada;
    case TripTrackingStopStatus.noShow:
      return AppStrings.passageiro.naoEmbarcou;
    case TripTrackingStopStatus.unknown:
      return AppStrings.passageiro.indisponivel;
  }
}

String presentEtaLabel(int? etaMinutos) {
  if (etaMinutos == null) {
    return AppStrings.passageiro.etaEmCalculo;
  }
  if (etaMinutos <= 0) {
    return AppStrings.passageiro.chegadaIminente;
  }
  if (etaMinutos == 1) {
    return AppStrings.passageiro.umMinuto;
  }
  return AppStrings.passageiro.minutos(etaMinutos);
}

String presentEtaHelper(int? etaMinutos) {
  if (etaMinutos == null) {
    return AppStrings.passageiro.primeiraAtualizacaoEtaHelper;
  }
  if (etaMinutos <= 0) {
    return AppStrings.passageiro.paradaProntaHelper;
  }
  return AppStrings.passageiro.previsaoSimplesHelper;
}

int? resolveStopsBeforePassenger(TripTrackingRecord trip) {
  final routeValue = trip.rotaGeografica?.paradasAntesDoPassageiro;
  if (routeValue != null) {
    return routeValue < 0 ? 0 : routeValue;
  }

  final stateValue = trip.trackingState.stopsBeforePassenger;
  if (stateValue != null) {
    return stateValue < 0 ? 0 : stateValue;
  }

  if (trip.trackingState.stopsRemaining > 0) {
    return trip.trackingState.stopsRemaining;
  }

  return null;
}

String presentVanStopsAway(int? stopsBefore) {
  if (stopsBefore == null) {
    return AppStrings.passageiro.viagemEmAcompanhamento;
  }
  if (stopsBefore <= 0) {
    return AppStrings.passageiro.vanChegando;
  }
  if (stopsBefore == 1) {
    return AppStrings.passageiro.vanAUmaParada;
  }
  return AppStrings.passageiro.vanAParadas(stopsBefore);
}

TripTrackingPresentation presentLocationFreshness(
  TripLocationFreshness freshness, {
  TripSignalRole role = TripSignalRole.passenger,
}) {
  return switch (role) {
    TripSignalRole.passenger => _presentPassengerLocationFreshness(freshness),
    TripSignalRole.guardian => _presentGuardianLocationFreshness(freshness),
    TripSignalRole.driver => _presentDriverLocationFreshness(freshness),
  };
}

TripTrackingPresentation _presentPassengerLocationFreshness(
  TripLocationFreshness freshness,
) {
  switch (freshness) {
    case TripLocationFreshness.live:
      return TripTrackingPresentation(
        label: AppStrings.passageiro.sinalAoVivo,
        helper: AppStrings.passageiro.sinalAoVivoHelper,
        icon: Icons.radar_rounded,
        color: const Color(0xFF45C77A),
        compactLabel: 'Ao vivo',
      );
    case TripLocationFreshness.delayed:
      return const TripTrackingPresentation(
        label: 'Posição recente',
        helper: 'A última posição pode estar alguns instantes atrasada.',
        icon: Icons.schedule_rounded,
        color: Color(0xFFE7AF43),
        compactLabel: 'Recente',
        actionLabel: 'Atualizar sinal',
      );
    case TripLocationFreshness.stale:
      return const TripTrackingPresentation(
        label: 'Posição antiga',
        helper: 'A van pode ter mudado de lugar.',
        icon: Icons.history_rounded,
        color: Color(0xFFE7AF43),
        compactLabel: 'Antiga',
        actionLabel: 'Atualizar sinal',
      );
    case TripLocationFreshness.paused:
      return const TripTrackingPresentation(
        label: 'Envio pausado',
        helper: 'Acompanhe pela última posição até o envio voltar.',
        icon: Icons.pause_circle_outline_rounded,
        color: Color(0xFFE7AF43),
        compactLabel: 'Pausado',
        actionLabel: 'Atualizar sinal',
      );
    case TripLocationFreshness.unavailable:
    case TripLocationFreshness.unknown:
      return TripTrackingPresentation(
        label: 'Aguardando posição da van',
        helper: AppStrings.passageiro.aguardandoSinalHelper,
        icon: Icons.signal_wifi_statusbar_connected_no_internet_4_rounded,
        color: const Color(0xFF92A1B2),
        compactLabel: 'Sem sinal',
        actionLabel: 'Atualizar sinal',
      );
  }
}

TripTrackingPresentation _presentGuardianLocationFreshness(
  TripLocationFreshness freshness,
) {
  switch (freshness) {
    case TripLocationFreshness.live:
      return const TripTrackingPresentation(
        label: 'Van ao vivo',
        helper: 'A localização da van está atualizando para a família.',
        icon: Icons.radar_rounded,
        color: Color(0xFF45C77A),
        compactLabel: 'Ao vivo',
      );
    case TripLocationFreshness.delayed:
      return const TripTrackingPresentation(
        label: 'Posição recente da van',
        helper: 'Acompanhe pela última posição enquanto o sinal volta.',
        icon: Icons.schedule_rounded,
        color: Color(0xFFE7AF43),
        compactLabel: 'Recente',
        actionLabel: 'Atualizar sinal',
      );
    case TripLocationFreshness.stale:
      return const TripTrackingPresentation(
        label: 'Posição antiga da van',
        helper: 'Confira o horário da última posição antes de decidir.',
        icon: Icons.history_rounded,
        color: Color(0xFFE7AF43),
        compactLabel: 'Antiga',
        actionLabel: 'Atualizar sinal',
      );
    case TripLocationFreshness.paused:
      return const TripTrackingPresentation(
        label: 'Envio da van pausado',
        helper: 'A última posição continua visível até o motorista retomar.',
        icon: Icons.pause_circle_outline_rounded,
        color: Color(0xFFE7AF43),
        compactLabel: 'Pausado',
        actionLabel: 'Atualizar sinal',
      );
    case TripLocationFreshness.unavailable:
    case TripLocationFreshness.unknown:
      return TripTrackingPresentation(
        label: AppStrings.guardian.aguardandoSinalDaVan,
        helper: 'Atualize ou acompanhe pelo último estado da viagem.',
        icon: Icons.signal_wifi_statusbar_connected_no_internet_4_rounded,
        color: const Color(0xFF92A1B2),
        compactLabel: 'Sem sinal',
        actionLabel: 'Atualizar sinal',
      );
  }
}

TripTrackingPresentation _presentDriverLocationFreshness(
  TripLocationFreshness freshness,
) {
  switch (freshness) {
    case TripLocationFreshness.live:
      return const TripTrackingPresentation(
        label: 'Transmitindo ao vivo',
        helper: 'Continue com o app aberto durante a corrida.',
        icon: Icons.radar_rounded,
        color: Color(0xFF45C77A),
        compactLabel: 'Ao vivo',
      );
    case TripLocationFreshness.delayed:
      return const TripTrackingPresentation(
        label: 'Transmissão atrasada',
        helper: 'Mantenha o app aberto; os passageiros veem a última posição.',
        icon: Icons.schedule_rounded,
        color: Color(0xFFE7AF43),
        compactLabel: 'Atrasada',
        actionLabel: 'Verificar sinal',
      );
    case TripLocationFreshness.stale:
      return const TripTrackingPresentation(
        label: 'Sinal antigo',
        helper: 'Verifique conexão e localização quando estiver seguro.',
        icon: Icons.history_rounded,
        color: Color(0xFFE7AF43),
        compactLabel: 'Antigo',
        actionLabel: 'Verificar sinal',
      );
    case TripLocationFreshness.paused:
      return const TripTrackingPresentation(
        label: 'Transmissão pausada',
        helper: 'Ative a localização para retomar o sinal ao vivo.',
        icon: Icons.pause_circle_outline_rounded,
        color: Color(0xFFE7AF43),
        compactLabel: 'Pausada',
        actionLabel: 'Ativar localização',
      );
    case TripLocationFreshness.unavailable:
    case TripLocationFreshness.unknown:
      return const TripTrackingPresentation(
        label: 'Sinal não ativo',
        helper: 'Ative a localização antes de seguir a rota.',
        icon: Icons.signal_wifi_statusbar_connected_no_internet_4_rounded,
        color: Color(0xFF92A1B2),
        compactLabel: 'Sem sinal',
        actionLabel: 'Ativar localização',
      );
  }
}

String presentRouteVersionLabel(int rotaVersao) =>
    AppStrings.passageiro.rotaVersao(rotaVersao);

bool usesRealRoadRouting(TripRouteGeometryRecord? geometry) {
  final provider = geometry?.provider.trim().toUpperCase() ?? '';
  return provider.startsWith('GOOGLE_DIRECTIONS');
}

String presentRouteGeometryLabel(TripRouteGeometryRecord? geometry) {
  if (geometry == null) {
    return AppStrings.passageiro.rotaDoDia;
  }
  if (usesRealRoadRouting(geometry)) {
    return AppStrings.passageiro.rotaDeRuas;
  }
  if (geometry.isDrawable) {
    return AppStrings.passageiro.trajetoAproximado;
  }
  return AppStrings.passageiro.rotaDoDia;
}

String presentRouteGeometryHelper(TripRouteGeometryRecord? geometry) {
  if (geometry == null) {
    return AppStrings.passageiro.rotaNaoPublicadaHelper;
  }
  if (usesRealRoadRouting(geometry)) {
    return AppStrings.passageiro.rotaDeRuasHelper;
  }
  if (geometry.isDrawable) {
    return AppStrings.passageiro.trajetoAproximadoHelper;
  }
  return AppStrings.passageiro.rotaNaoPublicadaHelper;
}

String presentNavigationSummary(TripNavigationRecord? navigation) {
  if (navigation == null || !navigation.hasUrl) {
    return AppStrings.passageiro.handoffExternoNaoPublicado;
  }
  if (navigation.quantidadeWaypoints <= 0) {
    return AppStrings.passageiro.mapsProntoRotaDoDia;
  }
  if (navigation.quantidadeWaypoints == 1) {
    return AppStrings.passageiro.mapsProntoUmaParada;
  }
  return AppStrings.passageiro.mapsProntoComParadas(
    navigation.quantidadeWaypoints,
  );
}
