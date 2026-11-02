import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_viagens_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_exception.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_bulk_create_result.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_detail_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_manifest_preview_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_viagens_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';

void main() {
  const session = AdminPortalSession(
    adminId: 1,
    adminName: 'Admin Portal',
    companyId: 1,
    companyName: 'Portal Empresa',
    companySlug: 'vanrides-default',
    email: 'admin@vanrides.dev',
    accessToken: 'token.admin',
  );

  testWidgets(
    'duplicidade recarrega data do input antes de abrir viagem existente',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1440, 1400));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final today = DateTime.now();
      final operationDate = DateTime(today.year, today.month, today.day);
      final gateway = _ConflictForUnloadedDateGateway(operationDate);

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: AdminViagensScreen(
              session: session,
              gateway: gateway,
              mapRenderMode: TripLiveMapRenderMode.placeholder,
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();
      await tester.tap(find.text('Exceção do dia').first);
      await tester.pumpAndSettle();

      await tester.tap(find.byType(DropdownButtonFormField<int>).first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Rota Escolar Zona Sul • Ida').last);
      await tester.pumpAndSettle();

      await tester.tap(find.text('Criar exceção').last);
      await tester.pumpAndSettle();

      expect(gateway.createdInput?.dataOperacao, operationDate);
      expect(gateway.requestedDates, contains(operationDate));
      expect(gateway.detailRequestedTripId, 31);
      expect(find.text('Lista da viagem'), findsOneWidget);
      expect(find.text('Sincronizar lista'), findsOneWidget);
    },
  );
}

class _ConflictForUnloadedDateGateway implements AdminViagensGateway {
  _ConflictForUnloadedDateGateway(this.conflictDate);

  final DateTime conflictDate;
  final requestedDates = <DateTime?>[];
  AdminViagemCreateInput? createdInput;
  int? detailRequestedTripId;

  @override
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) async {
    requestedDates.add(data);
    final normalized =
        data == null ? null : DateTime(data.year, data.month, data.day);
    if (normalized == conflictDate) {
      return [_viagemForDate(conflictDate)];
    }
    return const [];
  }

  @override
  Future<AdminViagemRecord> createViagem(AdminViagemCreateInput input) async {
    createdInput = input;
    throw const AdminPortalException(
      'Já existe viagem criada para este serviço e data.',
      code: 'CONFLICT',
    );
  }

  @override
  Future<AdminViagemDetailRecord> fetchViagemDetail(int viagemId) async {
    detailRequestedTripId = viagemId;
    return _viagemDetail();
  }

  @override
  Future<List<AdminRotaBaseRecord>> fetchRotas() async => const [_rota];

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() async => const [
    _servico,
  ];

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async => const [
    _motorista,
  ];

  @override
  Future<AdminViagemBulkCreateResult> generateAutomaticViagens(
    DateTime dataOperacao,
  ) async {
    return AdminViagemBulkCreateResult(
      dataOperacao: dataOperacao,
      criadas: 0,
      existentes: 1,
      ignoradas: 0,
      viagens: [_viagemForDate(dataOperacao)],
    );
  }

  @override
  Future<AdminViagemDetailRecord> updateViagemMotorista(
    int viagemId,
    int motoristaId,
  ) async => _viagemDetail();

  @override
  Future<AdminViagemDetailRecord> cancelViagem(int viagemId) async =>
      _viagemDetail(status: 'CANCELED');

  @override
  Future<AdminViagemManifestPreviewRecord> fetchManifestPreview(
    int viagemId,
  ) async {
    return AdminViagemManifestPreviewRecord(
      viagemId: viagemId,
      servicoId: _servico.id,
      servicoNome: _servico.nome,
      dataOperacao: conflictDate,
      paradasAtuais: 1,
      paradasAposSincronizacao: 1,
      adicionados: 0,
      removidos: 0,
      mantidos: 1,
      mudancas: 0,
      semMudancas: true,
      passageirosAdicionados: const [],
      passageirosRemovidos: const [],
      passageirosMantidos: const [],
    );
  }

  @override
  Future<AdminViagemDetailRecord> syncManifesto(int viagemId) async =>
      _viagemDetail();

  @override
  Future<AdminViagemDetailRecord> updateManifestPresence({
    required int viagemId,
    required int servicoId,
    required int passageiroId,
    required DateTime dataOperacao,
    required int? presencaId,
    required String status,
  }) async => _viagemDetail();
}

const _motorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

const _rota = AdminRotaBaseRecord(
  id: 21,
  nome: 'Rota Escolar Zona Sul',
  motorista: _motorista,
  quantidadeParadas: 1,
  passageiroIds: [99],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  diasOperacao: ['SEG', 'TER', 'QUA', 'QUI', 'SEX'],
  horarioPartida: '07:30',
  destinoFinalNome: 'Escola Central',
);

const _servico = AdminServicoRecorrenteRecord(
  id: 41,
  rotaBaseLegadaId: 21,
  nome: 'Rota Escolar Zona Sul',
  tipoOperacao: 'IDA',
  quantidadePassageiros: 1,
  passageiroIds: [99],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['SEG', 'TER', 'QUA', 'QUI', 'SEX'],
  horarioPartida: '07:30',
  destinoFinalNome: 'Escola Central',
  motoristaPadrao: _motorista,
);

AdminViagemRecord _viagemForDate(DateTime date) {
  return AdminViagemRecord(
    id: 31,
    rotaBaseId: _rota.id,
    servicoId: _servico.id,
    rotaNome: _rota.nome,
    servicoNome: _servico.nome,
    tipoOperacao: _servico.tipoOperacao,
    dataOperacao: DateTime(date.year, date.month, date.day),
    status: 'PLANNED',
    motorista: _motorista,
    quantidadeParadas: 1,
    horarioPartida: '07:30',
  );
}

AdminViagemDetailRecord _viagemDetail({String status = 'PLANNED'}) {
  return AdminViagemDetailRecord(
    id: 31,
    rotaBaseId: _rota.id,
    servicoId: _servico.id,
    rotaNome: _rota.nome,
    servicoNome: _servico.nome,
    tipoOperacao: _servico.tipoOperacao,
    dataOperacao: DateTime.now(),
    status: status,
    motorista: _motorista,
    quantidadeParadas: 1,
    horarioPartida: '07:30',
    rotaVersao: 1,
    etaMinutosAteProximaParada: 12,
    paradas: const [
      AdminViagemParadaRecord(
        id: 51,
        ordem: 1,
        status: 'PENDING',
        etaMinutos: 12,
        confirmadoPresenca: true,
        presencaId: 501,
        presencaStatus: 'CONFIRMED',
        proximaParada: true,
        passageiro: AdminViagemPassageiroRecord(
          id: 99,
          nome: 'Passageiro Detalhe',
          menorDeIdade: false,
        ),
      ),
    ],
    proximaParada: const AdminViagemParadaRecord(
      id: 51,
      ordem: 1,
      status: 'PENDING',
      etaMinutos: 12,
      confirmadoPresenca: true,
      presencaId: 501,
      presencaStatus: 'CONFIRMED',
      proximaParada: true,
      passageiro: AdminViagemPassageiroRecord(
        id: 99,
        nome: 'Passageiro Detalhe',
        menorDeIdade: false,
      ),
    ),
    fontePlanejamento: 'SERVICO_RECORRENTE',
    snapshotOperacional: true,
  );
}
