import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

void main() {
  group('WebSocketService URL builder', () {
    test('keeps explicit 443 for https base urls', () {
      final url = buildWebSocketUrlForTest(
        'http://127.0.0.1:8080',
      );

      expect(
        url,
        'http://127.0.0.1:8080:443/ws-location',
      );
    });

    test('preserves custom ports and base paths', () {
      final url = buildWebSocketUrlForTest('http://localhost:8080/api');

      expect(url, 'http://localhost:8080/api/ws-location');
    });
  });
}
