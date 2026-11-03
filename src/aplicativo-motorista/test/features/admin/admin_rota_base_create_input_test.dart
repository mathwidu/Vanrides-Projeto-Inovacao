import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_record.dart';

void main() {
  test('converte dias da UI para o contrato do backend ao criar rota', () {
    const input = AdminRotaBaseCreateInput(
      nome: 'Rota Escolar Centro',
      motoristaId: 7,
      passageiroIds: [11, 12],
      geracaoAutomaticaAtiva: true,
      diasOperacao: ['SEG', 'TER', 'QUA', 'QUI', 'SEX'],
      destinoFinalNome: ' Escola Central ',
      destinoFinalEndereco: ' Rua Principal, 100 ',
      destinoFinalLatitude: -30.01,
      destinoFinalLongitude: -51.22,
    );

    expect(input.toJson(), {
      'nome': 'Rota Escolar Centro',
      'motoristaId': 7,
      'passageiroIds': [11, 12],
      'geracaoAutomaticaAtiva': true,
      'diasOperacao': ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      'destinoFinalNome': 'Escola Central',
      'destinoFinalEndereco': 'Rua Principal, 100',
      'destinoFinalLatitude': -30.01,
      'destinoFinalLongitude': -51.22,
    });
  });

  test(
    'omite destino final textual vazio para nao sobrescrever rota com branco',
    () {
      const input = AdminRotaBaseCreateInput(
        nome: 'Rota Escolar Centro',
        motoristaId: 7,
        passageiroIds: [11],
        geracaoAutomaticaAtiva: true,
        diasOperacao: ['SEG'],
        destinoFinalNome: '   ',
        destinoFinalEndereco: '',
      );

      expect(input.toJson().containsKey('destinoFinalNome'), isFalse);
      expect(input.toJson().containsKey('destinoFinalEndereco'), isFalse);
    },
  );

  test('converte dias do backend para os rótulos do portal', () {
    final record = AdminRotaBaseRecord.fromJson({
      'id': 21,
      'nome': 'Rota Escolar Centro',
      'motorista': {
        'id': 7,
        'nome': 'Motorista Portal',
        'email': 'motorista@vanrides.dev',
        'telefone': '51999999999',
      },
      'quantidadeParadas': 2,
      'ativo': true,
      'geracaoAutomaticaAtiva': true,
      'diasOperacao': ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      'destinoFinalNome': 'Escola Central',
      'destinoFinalEndereco': 'Rua Principal, 100',
      'destinoFinalLatitude': -30.01,
      'destinoFinalLongitude': -51.22,
    });

    expect(record.diasOperacao, ['SEG', 'TER', 'QUA', 'QUI', 'SEX']);
    expect(record.destinoFinalNome, 'Escola Central');
    expect(record.destinoFinalEndereco, 'Rua Principal, 100');
    expect(record.hasDestinoFinal, isTrue);
  });
}
