import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/driver_home_providers.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/motorista_session_presenter.dart';

void main() {
  test('driver session provider cria presenter no estado inicial', () {
    final container = ProviderContainer();
    addTearDown(container.dispose);

    final presenter = container.read(
      motoristaSessionPresenterProvider(
        const DriverSessionCredentials(
          email: 'motorista@vanrides.test',
          senha: 'senha-teste',
          accessToken: 'token-teste',
        ),
      ),
    );

    expect(presenter, isA<MotoristaSessionPresenter>());
    expect(presenter.state.status, MotoristaSessionStatus.loading);
  });
}
