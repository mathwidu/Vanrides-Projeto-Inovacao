import '../../domain/trip_route_event.dart';
import '../../domain/trip_tracking_record.dart';
import '../../../../l10n/app_strings.dart';
import '../passenger_trip_readiness_presenter.dart';
import '../trip_tracking_presenter.dart';
import 'passenger_trip_glass_sheet.dart';

class PassengerTripFooterSheetModel {
  const PassengerTripFooterSheetModel({
    required this.statusTitle,
    required this.statusSubtitle,
    required this.todayTitle,
    required this.todaySubtitle,
    required this.signal,
    this.readiness,
    required this.stopTitle,
    required this.stopSubtitle,
    required this.fullStopTitle,
    required this.serviceName,
    required this.driverName,
    required this.operationLabel,
    required this.presenceActionLabel,
    this.operationalAlert,
  });

  final String statusTitle;
  final String statusSubtitle;
  final String todayTitle;
  final String todaySubtitle;
  final TripTrackingPresentation signal;
  final PassengerTripReadinessPresentation? readiness;
  final String stopTitle;
  final String stopSubtitle;
  final String fullStopTitle;
  final String serviceName;
  final String driverName;
  final String operationLabel;
  final String presenceActionLabel;
  final TripOperationalAlertRecord? operationalAlert;

  factory PassengerTripFooterSheetModel.from(
    TripTrackingRecord trip, {
    int dayTripCount = 1,
    required int pendingPresenceCount,
    TripOperationalAlertRecord? operationalAlert,
    TripSignalRole signalRole = TripSignalRole.passenger,
  }) {
    final display = PassengerTripGlassDisplay.from(
      trip,
      signalRole: signalRole,
    );
    final stop = trip.paradaDoPassageiro;
    return PassengerTripFooterSheetModel(
      statusTitle: display.primary,
      statusSubtitle: _compactStatusSubtitle(display),
      todayTitle: _todayTitle(trip),
      todaySubtitle:
          '${_todayTripCountLabel(dayTripCount)} • ${display.primary}',
      signal: display.signal,
      readiness: _readinessFor(trip),
      stopTitle:
          stop == null
              ? AppStrings.passageiro.semParadaPublicada
              : 'Parada ${stop.ordem}',
      stopSubtitle: display.stopHelper,
      fullStopTitle: display.myStopTitle,
      serviceName: display.serviceName,
      driverName: display.driverName,
      operationLabel: display.operationLabel,
      presenceActionLabel: _presenceLabel(pendingPresenceCount),
      operationalAlert: operationalAlert,
    );
  }
}

PassengerTripReadinessPresentation? _readinessFor(TripTrackingRecord trip) {
  return switch (trip.status) {
    TripTrackingStatus.completed || TripTrackingStatus.canceled => null,
    TripTrackingStatus.planned ||
    TripTrackingStatus.inProgress ||
    TripTrackingStatus.unknown => presentPassengerTripReadiness(trip),
  };
}

String _todayTitle(TripTrackingRecord trip) {
  return switch (trip.status) {
    TripTrackingStatus.planned => 'Hoje em preparo',
    TripTrackingStatus.inProgress => 'Hoje em acompanhamento',
    TripTrackingStatus.completed => 'Hoje concluído',
    TripTrackingStatus.canceled => 'Hoje cancelado',
    TripTrackingStatus.unknown => 'Hoje operacional',
  };
}

String _todayTripCountLabel(int dayTripCount) {
  final total = dayTripCount <= 0 ? 1 : dayTripCount;
  return total == 1 ? '1 viagem no dia' : '$total viagens no dia';
}

String _compactStatusSubtitle(PassengerTripGlassDisplay display) {
  if (display.primary == AppStrings.passageiro.vanAindaNaoIniciou) {
    return display.secondary;
  }
  if (display.primary == display.vanDistanceLabel) {
    return display.secondary;
  }
  return display.vanDistanceLabel;
}

String _presenceLabel(int count) {
  if (count <= 0) {
    return '';
  }
  return count == 1 ? '1 presença pendente' : '$count presenças pendentes';
}
