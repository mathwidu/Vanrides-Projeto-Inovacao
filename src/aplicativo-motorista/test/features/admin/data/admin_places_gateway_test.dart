import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_places_gateway.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';

void main() {
  test('autocomplete envia token admin e interpreta sugestoes', () async {
    final service = AdminPlacesPortalService(
      session: _session,
      client: MockClient((request) async {
        expect(request.url.path, '/company-admins/places/autocomplete');
        expect(request.url.queryParameters['input'], 'Escola Central');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response(
          jsonEncode([
            {
              'placeId': 'place-1',
              'mainText': 'Escola Central',
              'secondaryText': 'Rua Principal, 100',
            },
          ]),
          200,
        );
      }),
    );

    final suggestions = await service.autocomplete(' Escola Central ');

    expect(suggestions, hasLength(1));
    expect(suggestions.single.placeId, 'place-1');
    expect(suggestions.single.mainText, 'Escola Central');
    expect(suggestions.single.secondaryText, 'Rua Principal, 100');
  });

  test('detail interpreta latitude, longitude e endereco', () async {
    final service = AdminPlacesPortalService(
      session: _session,
      client: MockClient((request) async {
        expect(request.url.path, '/company-admins/places/details');
        expect(request.url.queryParameters['placeId'], 'place-1');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response(
          jsonEncode({
            'latitude': -30.01,
            'longitude': -51.22,
            'endereco': 'Rua Principal, 100',
          }),
          200,
        );
      }),
    );

    final details = await service.fetchDetails('place-1');

    expect(details.latitude, -30.01);
    expect(details.longitude, -51.22);
    expect(details.endereco, 'Rua Principal, 100');
  });
}

const _session = AdminPortalSession(
  adminId: 1,
  adminName: 'Admin Portal',
  companyId: 1,
  companyName: 'Portal Empresa',
  companySlug: 'vanrides-default',
  email: 'admin@vanrides.dev',
  accessToken: 'token.admin',
);
