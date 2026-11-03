import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/driver_trip_service.dart';
import 'motorista_session_presenter.dart';

class DriverSessionCredentials {
  const DriverSessionCredentials({
    required this.email,
    required this.senha,
    this.accessToken,
  });

  final String email;
  final String senha;
  final String? accessToken;

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        other is DriverSessionCredentials &&
            other.email == email &&
            other.senha == senha &&
            other.accessToken == accessToken;
  }

  @override
  int get hashCode => Object.hash(email, senha, accessToken);
}

final driverTripGatewayProvider = Provider.autoDispose
    .family<DriverTripGateway, DriverSessionCredentials>((ref, credentials) {
      return DriverTripService(
        email: credentials.email,
        senha: credentials.senha,
        accessToken: credentials.accessToken,
      );
    });

final motoristaSessionPresenterProvider = ChangeNotifierProvider.autoDispose
    .family<MotoristaSessionPresenter, DriverSessionCredentials>((
      ref,
      credentials,
    ) {
      final gateway = ref.watch(driverTripGatewayProvider(credentials));
      return MotoristaSessionPresenter(tripGateway: gateway);
    });
