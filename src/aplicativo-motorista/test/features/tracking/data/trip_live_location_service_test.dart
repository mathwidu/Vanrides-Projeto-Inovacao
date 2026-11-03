import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';

void main() {
  test('retorna localizacao quando backend responde 200', () async {
    final service = TripLiveLocationService(
      email: 'ana@vanrides.dev',
      senha: 'Secret123',
      client: MockClient((request) async {
        expect(request.url.path, contains('/api/localizacao/7'));
        return http.Response('{"latitude":-23.1,"longitude":-46.2}', 200);
      }),
    );

    final location = await service.fetchLatestLocation(7);

    expect(location, isNotNull);
    expect(location!.latitude, -23.1);
    expect(location.longitude, -46.2);
  });

  test('retorna null quando backend responde 204', () async {
    final service = TripLiveLocationService(
      email: 'ana@vanrides.dev',
      senha: 'Secret123',
      client: MockClient((request) async {
        expect(request.url.path, contains('/api/localizacao/7'));
        return http.Response('', 204);
      }),
    );

    final location = await service.fetchLatestLocation(7);

    expect(location, isNull);
  });
}
