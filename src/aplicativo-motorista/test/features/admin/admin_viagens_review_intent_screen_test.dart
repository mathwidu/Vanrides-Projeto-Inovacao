import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_viagens_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_bulk_create_result.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_detail_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_manifest_preview_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_viagens_review_intent.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_viagens_screen.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_viagens_generation_result_widgets.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_viagens_review_intent_banner.dart';
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

  testWidgets('revisao guiada atualiza o dia e abre viagem retornada', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final gateway = _ReviewIntentViagensGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminViagensScreen(
            session: session,
            gateway: gateway,
            reviewIntent: AdminViagensReviewIntent.passageiroServicos(
              'Aluno Portal',
            ),
            mapRenderMode: TripLiveMapRenderMode.placeholder,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('REVISÃO GUIADA • PASSAGEIRO'), findsOneWidget);
    expect(find.text('Revise as viagens de Aluno Portal'), findsOneWidget);
    expect(
      find.text(
        'Não delete viagens para incluir passageiros. Atualize o dia e revise as listas pendentes.',
      ),
      findsOneWidget,
    );

    await tester.tap(
      find.byKey(AdminViagensReviewIntentBanner.generateButtonKey),
    );
    await tester.pumpAndSettle();

    expect(gateway.generated, isTrue);
    expect(find.text('Dia operacional montado'), findsOneWidget);
    expect(
      find.byKey(AdminViagensGenerationResultPanel.openFirstTripButtonKey),
      findsOneWidget,
    );

    await tester.tap(
      find.byKey(AdminViagensGenerationResultPanel.openFirstTripButtonKey),
    );
    await tester.pumpAndSettle();

    expect(gateway.detailRequestedTripId, 31);
    expect(find.text('Lista da viagem'), findsOneWidget);
    expect(find.text('Passageiro Detalhe'), findsWidgets);
  });
}

class _ReviewIntentViagensGateway implements AdminViagensGateway {
  bool generated = false;
  int? detailRequestedTripId;

  @override
  Future<AdminViagemRecord> createViagem(AdminViagemCreateInput input) async =>
      _viagem;

  @override
  Future<AdminViagemBulkCreateResult> generateAutomaticViagens(
    DateTime dataOperacao,
  ) async {
    generated = true;
    return AdminViagemBulkCreateResult(
      dataOperacao: dataOperacao,
      criadas: 0,
      existentes: 1,
      atualizadas: 1,
      ignoradas: 0,
      viagens: [_viagem],
    );
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
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) async =>
      generated ? [_viagem] : const [];

  @override
  Future<AdminViagemDetailRecord> fetchViagemDetail(int viagemId) async {
    detailRequestedTripId = viagemId;
    return _viagemDetail();
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
      dataOperacao: DateTime(2026, 6, 18),
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

final _viagem = AdminViagemRecord(
  id: 31,
  rotaBaseId: _rota.id,
  servicoId: _servico.id,
  rotaNome: _rota.nome,
  servicoNome: _servico.nome,
  tipoOperacao: _servico.tipoOperacao,
  dataOperacao: DateTime(2026, 6, 18),
  status: 'PLANNED',
  motorista: _motorista,
  quantidadeParadas: 1,
  horarioPartida: '07:30',
);

AdminViagemDetailRecord _viagemDetail({String status = 'PLANNED'}) {
  return AdminViagemDetailRecord(
    id: 31,
    rotaBaseId: _rota.id,
    servicoId: _servico.id,
    rotaNome: _rota.nome,
    servicoNome: _servico.nome,
    tipoOperacao: _servico.tipoOperacao,
    dataOperacao: DateTime(2026, 6, 18),
    status: status,
    motorista: _motorista,
    quantidadeParadas: 1,
    horarioPartida: '07:30',
    rotaVersao: 1,
    etaMinutosAteProximaParada: 12,
    fontePlanejamento: 'SERVICO_RECORRENTE',
    snapshotOperacional: true,
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
  );
}
