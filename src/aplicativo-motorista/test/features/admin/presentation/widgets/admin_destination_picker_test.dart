import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_places_gateway.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_destination_picker.dart';

void main() {
  testWidgets(
    'AdminDestinationPicker renderiza sugestoes e seleciona destino',
    (tester) async {
      final gateway = _FakePlacesGateway();
      _SelectedDestination? selected;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: AdminDestinationPicker(
              placesGateway: gateway,
              debounceDuration: Duration.zero,
              onSelected:
                  (nome, endereco, latitude, longitude) =>
                      selected = _SelectedDestination(
                        nome: nome,
                        endereco: endereco,
                        latitude: latitude,
                        longitude: longitude,
                      ),
              onCleared: () {},
            ),
          ),
        ),
      );

      await tester.enterText(find.byType(TextFormField), 'Escola');
      await tester.pump();
      await tester.pump();

      expect(gateway.lastAutocompleteInput, 'Escola');
      expect(find.text('Escola Central'), findsOneWidget);
      expect(find.text('Rua Principal, 100'), findsOneWidget);

      await tester.tap(find.text('Escola Central'));
      await tester.pumpAndSettle();

      expect(gateway.lastDetailsPlaceId, 'place-1');
      expect(selected?.nome, 'Escola Central');
      expect(selected?.endereco, 'Rua Principal, 100');
      expect(selected?.latitude, -30.01);
      expect(selected?.longitude, -51.22);
    },
  );

  testWidgets('AdminDestinationPicker limpa destino confirmado', (
    tester,
  ) async {
    var cleared = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminDestinationPicker(
            placesGateway: _FakePlacesGateway(),
            nome: 'Escola Central',
            endereco: 'Rua Principal, 100',
            onSelected: (_, _, _, _) {},
            onCleared: () => cleared = true,
          ),
        ),
      ),
    );

    expect(find.text('Escola Central'), findsOneWidget);
    expect(find.text('Rua Principal, 100'), findsOneWidget);

    await tester.tap(find.byTooltip('Remover destino'));
    await tester.pump();

    expect(cleared, isTrue);
  });
}

class _FakePlacesGateway implements AdminPlacesGateway {
  String? lastAutocompleteInput;
  String? lastDetailsPlaceId;

  @override
  Future<List<AdminPlacePrediction>> autocomplete(String input) async {
    lastAutocompleteInput = input;
    return const [
      AdminPlacePrediction(
        placeId: 'place-1',
        mainText: 'Escola Central',
        secondaryText: 'Rua Principal, 100',
      ),
    ];
  }

  @override
  Future<AdminPlaceDetails> fetchDetails(String placeId) async {
    lastDetailsPlaceId = placeId;
    return const AdminPlaceDetails(
      latitude: -30.01,
      longitude: -51.22,
      endereco: 'Rua Principal, 100',
    );
  }
}

class _SelectedDestination {
  const _SelectedDestination({
    required this.nome,
    required this.endereco,
    required this.latitude,
    required this.longitude,
  });

  final String nome;
  final String? endereco;
  final double latitude;
  final double longitude;
}
