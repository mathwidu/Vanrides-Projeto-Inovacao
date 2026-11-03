import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings_admin.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings_deep_link.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings_guardian.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings_motorista.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings_network.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings_passageiro.dart';

void main() {
  group('AppStrings', () {
    test('expõe todos os namespaces esperados', () {
      expect(AppStrings.admin, isA<AdminStrings>());
      expect(AppStrings.motorista, isA<MotoristaStrings>());
      expect(AppStrings.passageiro, isA<PassageiroStrings>());
      expect(AppStrings.guardian, isA<GuardianStrings>());
      expect(AppStrings.network, isA<NetworkStrings>());
      expect(AppStrings.deepLink, isA<DeepLinkStrings>());
    });

    test('mantém amostras úteis de cada namespace preenchidas e limpas', () {
      _expectCleanStrings([
        AppStrings.admin.erroCarregarMotoristas,
        AppStrings.admin.erroCarregarDashboard,
        AppStrings.admin.rotaExcluida,
      ]);

      _expectCleanStrings([
        AppStrings.motorista.appName,
        AppStrings.motorista.iniciarCorrida,
        AppStrings.motorista.cadastroMotoristaBadge,
        AppStrings.motorista.routeSavedAs('rota-v2'),
      ]);

      _expectCleanStrings([
        AppStrings.passageiro.appName,
        AppStrings.passageiro.crieSuaConta,
        AppStrings.passageiro.mapaCompletoDaViagem,
        AppStrings.passageiro.coordenadasAoVivo(10.0, 20.0),
      ]);

      _expectCleanStrings([
        AppStrings.guardian.dashboardBadge,
        AppStrings.guardian.passageirosVinculados,
        AppStrings.guardian.mapaCompleto,
        AppStrings.guardian.bemVindo('Ana'),
      ]);

      _expectCleanStrings([
        AppStrings.network.semConexao,
        AppStrings.network.timeout,
        AppStrings.network.erro5xx,
      ]);

      _expectCleanStrings([
        AppStrings.deepLink.linkDesconhecido,
        AppStrings.deepLink.papelInvalido,
        AppStrings.deepLink.rastreioDisponivelParaPassageiroOuResponsavel,
      ]);
    });

    test('copy operacional primaria evita termos tecnicos', () {
      _expectOperationalCopy([
        AppStrings.motorista.manifestoDeHoje,
        AppStrings.motorista.manifestoOperacional,
        AppStrings.motorista.manifestoResumo(3, 2, 1),
        AppStrings.passageiro.trechoOperacionalPublicado,
        AppStrings.passageiro.statusIndisponivelHelper,
        AppStrings.passageiro.previsaoSimplesHelper,
        AppStrings.passageiro.rotaDeRuasHelper,
        AppStrings.passageiro.trajetoAproximadoHelper,
        AppStrings.passageiro.trajetoAproximadoAguardandoPrimeiraPosicao,
        AppStrings.guardian.perfilBackend,
      ]);
    });

    test('copydeck operacional registra papeis, estados e excecoes', () {
      final copydeck = File('backlog/VanRides-Copydeck-Operacional.md');

      expect(copydeck.existsSync(), isTrue);

      final content = copydeck.readAsStringSync();
      for (final section in [
        'Passageiro',
        'Motorista',
        'Responsavel',
        'Empresa',
        'Excecoes tecnicas',
      ]) {
        expect(content, contains(section));
      }
      expect(content, contains('UX-COPY-001'));
    });
  });
}

void _expectCleanStrings(List<String> values) {
  for (final value in values) {
    expect(value.trim(), isNotEmpty);
    expect(value, isNot(contains('TODO')));
    expect(value, isNot(contains('XXX')));
  }
}

void _expectOperationalCopy(List<String> values) {
  final forbidden = RegExp(
    r'\b(backend|polling|fixture|websocket|unsupported|bad request|conflict|snapshot|manifesto)\b',
    caseSensitive: false,
  );

  for (final value in values) {
    expect(value.trim(), isNotEmpty);
    expect(value, isNot(matches(forbidden)));
  }
}
