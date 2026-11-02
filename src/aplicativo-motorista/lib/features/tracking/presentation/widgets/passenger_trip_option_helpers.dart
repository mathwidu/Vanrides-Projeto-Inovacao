import 'package:flutter/material.dart';

import '../../domain/trip_tracking_record.dart';

typedef PassengerTripKeyBuilder = Key Function(int tripId);

String passengerTripOptionLabel(TripTrackingRecord trip) {
  final horario = trip.horarioPartida ?? '--:--';
  final tipo = trip.tipoOperacaoLabel;
  final nome = trip.nomeOperacional.trim();
  final suffix = tipo.isEmpty ? nome : '$tipo • $nome';
  return '$horario • $suffix';
}

String passengerTripCompactOptionLabel(TripTrackingRecord trip) {
  final horario = trip.horarioPartida ?? '--:--';
  final tipo = trip.tipoOperacaoLabel;
  if (tipo.isEmpty) {
    return horario;
  }
  return '$horario • $tipo';
}

IconData passengerTripStatusIcon(TripTrackingStatus status) {
  return switch (status) {
    TripTrackingStatus.inProgress => Icons.directions_bus_filled_rounded,
    TripTrackingStatus.completed => Icons.check_circle_outline_rounded,
    TripTrackingStatus.canceled => Icons.block_rounded,
    _ => Icons.schedule_outlined,
  };
}
