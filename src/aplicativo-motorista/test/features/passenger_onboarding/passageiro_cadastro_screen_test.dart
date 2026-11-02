import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/presentation/cadastro_passageiro_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/email_verification_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/data/passageiro_cadastro_gateway.dart';

void main() {
  testWidgets(
    'cadastro de passageiro seleciona endereco e abre verificacao de email',
    (tester) async {
      final cadastroGateway = _FakePassageiroCadastroGateway(
        result: const PassageiroCadastroOk(),
      );
      final placesGateway = _FakePlacesAutocompleteGateway(
        predictions: const [
          PassageiroPlacePrediction(
            placeId: 'place-1',
            description: 'Rua Teste, 123 - Centro',
          ),
        ],
        details: const PassageiroPlaceDetails(
          latitude: -29.684,
          longitude: -51.133,
          endereco: 'Rua Teste, 123',
          bairro: 'Centro',
          cidade: 'Novo Hamburgo',
          estado: 'RS',
          cep: '93510-000',
        ),
      );

      await _pumpScreen(tester, cadastroGateway, placesGateway);
      await _fillIdentityFields(tester);
      await _selectAddress(tester);
      await _fillPasswordFields(tester);

      await tester.tap(find.byKey(CadastroPassageiroScreen.cadastrarButtonKey));
      await tester.pumpAndSettle();

      expect(cadastroGateway.payloads, hasLength(1));
      final payload = cadastroGateway.payloads.single;
      expect(payload.nome, 'Matheus Duarte');
      expect(payload.email, 'passageiro@vanrides.dev');
      expect(payload.dataNascimento, '1995-02-20');
      expect(payload.endereco, 'Rua Teste, 123');
      expect(payload.bairro, 'Centro');
      expect(payload.cidade, 'Novo Hamburgo');
      expect(payload.latitude, -29.684);
      expect(payload.longitude, -51.133);
      expect(
        find.byKey(EmailVerificationPendingScreen.screenKey),
        findsOneWidget,
      );
      expect(find.text('passageiro@vanrides.dev'), findsOneWidget);
    },
  );

  testWidgets('cadastro de passageiro exige endereco selecionado', (
    tester,
  ) async {
    final cadastroGateway = _FakePassageiroCadastroGateway(
      result: const PassageiroCadastroOk(),
    );

    await _pumpScreen(
      tester,
      cadastroGateway,
      _FakePlacesAutocompleteGateway(),
    );
    await _fillIdentityFields(tester);
    await _fillPasswordFields(tester);

    await tester.tap(find.byKey(CadastroPassageiroScreen.cadastrarButtonKey));
    await tester.pump();

    expect(cadastroGateway.payloads, isEmpty);
    expect(
      find.text('Preencha todos os campos obrigatórios do cadastro.'),
      findsOneWidget,
    );
  });

  testWidgets('cadastro de passageiro trata erro de rede do places', (
    tester,
  ) async {
    await _pumpScreen(
      tester,
      _FakePassageiroCadastroGateway(result: const PassageiroCadastroOk()),
      _FakePlacesAutocompleteGateway(throwOnAutocomplete: true),
    );

    await tester.enterText(
      find.byKey(CadastroPassageiroScreen.buscarEnderecoFieldKey),
      'Rua Teste',
    );
    await tester.pump();

    expect(
      find.text('Não foi possível buscar endereços agora.'),
      findsOneWidget,
    );
  });

  testWidgets('cadastro de passageiro trata erro de rede do cadastro', (
    tester,
  ) async {
    final cadastroGateway = _FakePassageiroCadastroGateway(
      result: const PassageiroCadastroNetworkError(),
    );
    final placesGateway = _FakePlacesAutocompleteGateway(
      predictions: const [
        PassageiroPlacePrediction(
          placeId: 'place-1',
          description: 'Rua Teste, 123 - Centro',
        ),
      ],
      details: const PassageiroPlaceDetails(
        latitude: -29.684,
        longitude: -51.133,
        endereco: 'Rua Teste, 123',
        bairro: 'Centro',
        cidade: 'Novo Hamburgo',
        estado: 'RS',
        cep: '93510-000',
      ),
    );

    await _pumpScreen(tester, cadastroGateway, placesGateway);
    await _fillIdentityFields(tester);
    await _selectAddress(tester);
    await _fillPasswordFields(tester);

    await tester.tap(find.byKey(CadastroPassageiroScreen.cadastrarButtonKey));
    await tester.pump();

    expect(find.text('Erro de conexão com o servidor.'), findsOneWidget);
    expect(find.text('Matheus'), findsOneWidget);
    expect(find.text('passageiro@vanrides.dev'), findsOneWidget);
    expect(find.byKey(EmailVerificationPendingScreen.screenKey), findsNothing);
  });
}

Future<void> _pumpScreen(
  WidgetTester tester,
  PassageiroCadastroGateway cadastroGateway,
  PlacesAutocompleteGateway placesGateway,
) async {
  await tester.pumpWidget(
    MaterialApp(
      home: CadastroPassageiroScreen(
        cadastroGateway: cadastroGateway,
        placesGateway: placesGateway,
        openMapAfterAddressSelection: false,
      ),
    ),
  );
}

Future<void> _fillIdentityFields(WidgetTester tester) async {
  await _enterText(tester, CadastroPassageiroScreen.nomeFieldKey, 'Matheus');
  await _enterText(
    tester,
    CadastroPassageiroScreen.sobrenomeFieldKey,
    'Duarte',
  );
  await _enterText(
    tester,
    CadastroPassageiroScreen.emailFieldKey,
    'passageiro@vanrides.dev',
  );
  await _enterText(
    tester,
    CadastroPassageiroScreen.telefoneFieldKey,
    '51999990000',
  );
  await _enterText(
    tester,
    CadastroPassageiroScreen.dataNascimentoFieldKey,
    '20/02/1995',
  );
}

Future<void> _selectAddress(WidgetTester tester) async {
  await _enterText(
    tester,
    CadastroPassageiroScreen.buscarEnderecoFieldKey,
    'Rua Teste',
  );
  await tester.pumpAndSettle();
  await tester.tap(find.text('Rua Teste, 123 - Centro'));
  await tester.pumpAndSettle();
}

Future<void> _fillPasswordFields(WidgetTester tester) async {
  await _enterText(
    tester,
    CadastroPassageiroScreen.senhaFieldKey,
    'Passenger!123',
  );
  await _enterText(
    tester,
    CadastroPassageiroScreen.confirmarSenhaFieldKey,
    'Passenger!123',
  );
  await tester.ensureVisible(
    find.byKey(CadastroPassageiroScreen.cadastrarButtonKey),
  );
}

Future<void> _enterText(WidgetTester tester, Key key, String value) async {
  final finder = find.byKey(key);
  await tester.ensureVisible(finder);
  await tester.enterText(finder, value);
  await tester.pump();
}

class _FakePassageiroCadastroGateway implements PassageiroCadastroGateway {
  _FakePassageiroCadastroGateway({required this.result});

  final PassageiroCadastroResult result;
  final List<PassageiroCadastroPayload> payloads = [];

  @override
  Future<PassageiroCadastroResult> cadastrar(
    PassageiroCadastroPayload payload,
  ) async {
    payloads.add(payload);
    return result;
  }
}

class _FakePlacesAutocompleteGateway implements PlacesAutocompleteGateway {
  _FakePlacesAutocompleteGateway({
    this.predictions = const [],
    this.details,
    this.throwOnAutocomplete = false,
  });

  final List<PassageiroPlacePrediction> predictions;
  final PassageiroPlaceDetails? details;
  final bool throwOnAutocomplete;

  @override
  bool get isAvailable => true;

  @override
  Future<List<PassageiroPlacePrediction>> autocomplete(String input) async {
    if (throwOnAutocomplete) {
      throw const PlacesAutocompleteException(
        'Não foi possível buscar endereços agora.',
      );
    }
    return predictions;
  }

  @override
  Future<PassageiroPlaceDetails?> fetchDetails(String placeId) async {
    return details;
  }
}
