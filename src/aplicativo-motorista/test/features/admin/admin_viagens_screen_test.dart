import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
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

  testWidgets('renderiza a listagem operacional de viagens', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminViagensScreen(
            session: session,
            gateway: _FakeAdminViagensGateway(),
            mapRenderMode: TripLiveMapRenderMode.placeholder,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Viagens do dia'), findsWidgets);
    expect(find.text('Atualizar dia'), findsOneWidget);
    expect(find.text('Exceção do dia'), findsOneWidget);
    expect(find.text('Criar viagem extra'), findsNothing);
    expect(find.text('Rota Escolar Zona Sul'), findsWidgets);
    expect(find.textContaining('Exibindo 1-1 de 1 viagens'), findsOneWidget);
  });

  testWidgets('monta viagens automaticas do dia a partir da listagem vazia', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    final gateway = _GeneratingAdminViagensGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminViagensScreen(session: session, gateway: gateway),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(
      find.text('Nenhuma viagem encontrada com o filtro atual.'),
      findsOneWidget,
    );
    expect(find.text('Montar dia'), findsWidgets);

    await tester.tap(find.text('Montar dia').first);
    await tester.pumpAndSettle();

    expect(gateway.generated, isTrue);
    expect(find.text('Rota Escolar Zona Sul'), findsWidgets);
    expect(
      find.text(
        'Dia montado: 1 criada, 0 atualizadas, 0 já existiam, 0 bloqueadas, 0 ignoradas.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('mostra motivos acionaveis quando servicos sao ignorados', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    final gateway = _IgnoredGenerationAdminViagensGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminViagensScreen(session: session, gateway: gateway),
        ),
      ),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text('Montar dia').first);
    await tester.pumpAndSettle();

    expect(gateway.generated, isTrue);
    expect(
      find.text(
        'Dia montado: 0 criadas, 0 atualizadas, 0 já existiam, 0 bloqueadas, 1 ignorada. Revise: Linha Lotada - capacidade excedida.',
      ),
      findsOneWidget,
    );
    expect(find.text('Serviços que precisam de revisão'), findsOneWidget);
    expect(find.text('Linha Lotada'), findsOneWidget);
    expect(find.text('capacidade excedida'), findsWidgets);
    expect(
      find.text('Lista da viagem acima da capacidade planejada.'),
      findsOneWidget,
    );
  });

  testWidgets(
    'abre detalhe com lista da viagem e troca motorista operacional',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1440, 1400));

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: AdminViagensScreen(
              session: session,
              gateway: _FakeAdminViagensGateway(),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.visibility_outlined));
      await tester.pumpAndSettle();

      expect(find.text('Lista da viagem'), findsOneWidget);
      expect(find.text('Passageiro Detalhe'), findsWidgets);
      expect(find.text('Mapa operacional'), findsOneWidget);
      expect(find.text('Rota de ruas'), findsOneWidget);
      expect(find.text('2 pontos publicados'), findsWidgets);
      expect(find.text('Trocar motorista'), findsOneWidget);

      await tester.tap(find.byType(DropdownButtonFormField<int>).last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Motorista Reserva').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Trocar motorista'));
      await tester.pumpAndSettle();

      expect(
        find.text('Motorista operacional alterado para Motorista Reserva.'),
        findsOneWidget,
      );
    },
  );

  testWidgets('copia link operacional da viagem no detalhe administrativo', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    String? copiedText;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          final data = call.arguments as Map<dynamic, dynamic>;
          copiedText = data['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(() {
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      );
    });

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminViagensScreen(
            session: session,
            gateway: _FakeAdminViagensGateway(),
            mapRenderMode: TripLiveMapRenderMode.placeholder,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.visibility_outlined));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Copiar link'));
    await tester.pumpAndSettle();

    expect(copiedText, 'vanrides://viagem/31');
    expect(find.text('Link da viagem copiado.'), findsOneWidget);
  });

  testWidgets('interpreta status reais do backend na listagem operacional', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminViagensScreen(
            session: session,
            gateway: _StatusAdminViagensGateway(),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('4 montadas'), findsOneWidget);
    expect(find.text('1 em operação'), findsOneWidget);
    expect(find.text('1 concluída'), findsOneWidget);
    expect(find.text('AGENDADA'), findsOneWidget);
    expect(find.text('EM TRÂNSITO'), findsOneWidget);
    expect(find.text('CONCLUÍDA'), findsOneWidget);
    expect(find.text('CANCELADA'), findsOneWidget);
  });

  testWidgets('cancela viagem planejada no detalhe administrativo', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    final gateway = _CancelingAdminViagensGateway();

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
    await tester.tap(find.byIcon(Icons.visibility_outlined));
    await tester.pumpAndSettle();

    expect(find.text('Cancelar viagem'), findsOneWidget);
    await tester.tap(find.text('Cancelar viagem'));
    await tester.pumpAndSettle();
    expect(find.text('Cancelar viagem planejada?'), findsOneWidget);

    await tester.tap(find.text('Cancelar viagem').last);
    await tester.pumpAndSettle();

    expect(gateway.cancelledTripId, 31);
    expect(find.text('CANCELADA'), findsOneWidget);
    expect(find.text('Viagem cancelada com sucesso.'), findsOneWidget);
  });

  testWidgets('corrige presença do passageiro pela lista da viagem', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    final gateway = _PresenceEditingAdminViagensGateway();

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
    await tester.tap(find.byIcon(Icons.visibility_outlined));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Corrigir presença'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ausente'));
    await tester.pumpAndSettle();

    expect(gateway.updatedViagemId, 31);
    expect(gateway.updatedServicoId, 41);
    expect(gateway.updatedPassageiroId, 99);
    expect(gateway.updatedPresencaId, 501);
    expect(gateway.updatedStatus, 'ABSENT');
    expect(gateway.updatedDataOperacao, DateTime(2026, 4, 15));
    expect(find.text('AUSENTE'), findsOneWidget);
    expect(find.text('Presença marcada como ausente.'), findsOneWidget);
  });

  testWidgets('cria viagem extra por serviço e mantém rota-base como legado', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    final gateway = _ManualCreateAdminViagensGateway();

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

    expect(find.text('Exceção do dia'), findsWidgets);
    expect(find.text('Fallback legado por rota-base'), findsNothing);
    expect(find.text('Observações Operacionais (Opcional)'), findsNothing);

    await tester.tap(find.byType(DropdownButtonFormField<int>).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Rota Escolar Zona Sul • Ida').last);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Criar exceção').last);
    await tester.pumpAndSettle();

    expect(gateway.createdInput?.servicoId, 41);
    expect(gateway.createdInput?.rotaBaseId, isNull);
    expect(gateway.createdInput?.motoristaId, isNull);
    expect(
      find.text('Exceção do dia Rota Escolar Zona Sul criada.'),
      findsOneWidget,
    );
  });

  testWidgets(
    'duplicidade de viagem extra abre viagem existente para manutenção',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1440, 1400));
      final gateway = _ConflictCreateAdminViagensGateway();

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

      expect(gateway.detailRequestedTripId, 31);
      expect(find.text('Lista da viagem'), findsOneWidget);
      expect(find.text('Sincronizar lista'), findsOneWidget);
      expect(find.textContaining('Conflict'), findsNothing);
      expect(find.textContaining('Abra a viagem existente'), findsOneWidget);
    },
  );
}

class _FakeAdminViagensGateway implements AdminViagensGateway {
  const _FakeAdminViagensGateway();

  @override
  Future<AdminViagemRecord> createViagem(AdminViagemCreateInput input) async =>
      _viagem;

  @override
  Future<AdminViagemBulkCreateResult> generateAutomaticViagens(
    DateTime dataOperacao,
  ) async {
    return AdminViagemBulkCreateResult(
      dataOperacao: dataOperacao,
      criadas: 1,
      existentes: 0,
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
    _motoristaReserva,
  ];

  @override
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) async => [
    _viagem,
  ];

  @override
  Future<AdminViagemDetailRecord> fetchViagemDetail(int viagemId) async =>
      _viagemDetail(_motorista);

  @override
  Future<AdminViagemDetailRecord> updateViagemMotorista(
    int viagemId,
    int motoristaId,
  ) async => _viagemDetail(
    motoristaId == _motoristaReserva.id ? _motoristaReserva : _motorista,
  );

  @override
  Future<AdminViagemDetailRecord> cancelViagem(int viagemId) async =>
      _viagemDetail(_motorista, status: 'CANCELED');

  @override
  Future<AdminViagemManifestPreviewRecord> fetchManifestPreview(
    int viagemId,
  ) async => _manifestPreview(viagemId);

  @override
  Future<AdminViagemDetailRecord> syncManifesto(int viagemId) async =>
      _viagemDetail(_motorista);

  @override
  Future<AdminViagemDetailRecord> updateManifestPresence({
    required int viagemId,
    required int servicoId,
    required int passageiroId,
    required DateTime dataOperacao,
    required int? presencaId,
    required String status,
  }) async => _viagemDetail(_motorista, presenceStatus: status);
}

class _GeneratingAdminViagensGateway implements AdminViagensGateway {
  bool generated = false;

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
      criadas: 1,
      existentes: 0,
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
    _motoristaReserva,
  ];

  @override
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) async =>
      generated ? [_viagem] : const [];

  @override
  Future<AdminViagemDetailRecord> fetchViagemDetail(int viagemId) async =>
      _viagemDetail(_motorista);

  @override
  Future<AdminViagemDetailRecord> updateViagemMotorista(
    int viagemId,
    int motoristaId,
  ) async => _viagemDetail(
    motoristaId == _motoristaReserva.id ? _motoristaReserva : _motorista,
  );

  @override
  Future<AdminViagemDetailRecord> cancelViagem(int viagemId) async =>
      _viagemDetail(_motorista, status: 'CANCELED');

  @override
  Future<AdminViagemManifestPreviewRecord> fetchManifestPreview(
    int viagemId,
  ) async => _manifestPreview(viagemId);

  @override
  Future<AdminViagemDetailRecord> syncManifesto(int viagemId) async =>
      _viagemDetail(_motorista);

  @override
  Future<AdminViagemDetailRecord> updateManifestPresence({
    required int viagemId,
    required int servicoId,
    required int passageiroId,
    required DateTime dataOperacao,
    required int? presencaId,
    required String status,
  }) async => _viagemDetail(_motorista, presenceStatus: status);
}

class _StatusAdminViagensGateway extends _FakeAdminViagensGateway {
  const _StatusAdminViagensGateway();

  @override
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) async => [
    _viagemWithStatus(31, 'PLANNED'),
    _viagemWithStatus(32, 'IN_PROGRESS'),
    _viagemWithStatus(33, 'COMPLETED'),
    _viagemWithStatus(34, 'CANCELED'),
  ];
}

class _IgnoredGenerationAdminViagensGateway
    extends _GeneratingAdminViagensGateway {
  @override
  Future<AdminViagemBulkCreateResult> generateAutomaticViagens(
    DateTime dataOperacao,
  ) async {
    generated = true;
    return AdminViagemBulkCreateResult(
      dataOperacao: dataOperacao,
      criadas: 0,
      existentes: 0,
      ignoradas: 1,
      servicosIgnorados: const [
        AdminViagemIgnoradaRecord(
          servicoId: 18,
          servicoNome: 'Linha Lotada',
          motivo: 'CAPACIDADE_EXCEDIDA',
          mensagem: 'Manifesto operacional acima da capacidade planejada.',
        ),
      ],
      viagens: const [],
    );
  }

  @override
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) async =>
      const [];
}

class _CancelingAdminViagensGateway extends _FakeAdminViagensGateway {
  int? cancelledTripId;

  @override
  Future<AdminViagemDetailRecord> cancelViagem(int viagemId) async {
    cancelledTripId = viagemId;
    return _viagemDetail(_motorista, status: 'CANCELED');
  }
}

class _PresenceEditingAdminViagensGateway extends _FakeAdminViagensGateway {
  int? updatedViagemId;
  int? updatedServicoId;
  int? updatedPassageiroId;
  int? updatedPresencaId;
  DateTime? updatedDataOperacao;
  String? updatedStatus;

  @override
  Future<AdminViagemDetailRecord> updateManifestPresence({
    required int viagemId,
    required int servicoId,
    required int passageiroId,
    required DateTime dataOperacao,
    required int? presencaId,
    required String status,
  }) async {
    updatedViagemId = viagemId;
    updatedServicoId = servicoId;
    updatedPassageiroId = passageiroId;
    updatedPresencaId = presencaId;
    updatedDataOperacao = dataOperacao;
    updatedStatus = status;
    return _viagemDetail(_motorista, presenceStatus: status);
  }
}

class _ManualCreateAdminViagensGateway extends _FakeAdminViagensGateway {
  AdminViagemCreateInput? createdInput;

  @override
  Future<AdminViagemRecord> createViagem(AdminViagemCreateInput input) async {
    createdInput = input;
    return _viagem;
  }
}

class _ConflictCreateAdminViagensGateway extends _FakeAdminViagensGateway {
  int? detailRequestedTripId;

  @override
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) async => [
    _viagemForDate(DateTime.now()),
  ];

  @override
  Future<AdminViagemRecord> createViagem(AdminViagemCreateInput input) async {
    throw const AdminPortalException('Conflict', code: 'CONFLICT');
  }

  @override
  Future<AdminViagemDetailRecord> fetchViagemDetail(int viagemId) async {
    detailRequestedTripId = viagemId;
    return _viagemDetail(_motorista);
  }
}

const _motorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

const _motoristaReserva = AdminMotoristaRecord(
  id: 8,
  nome: 'Motorista Reserva',
  email: 'reserva@vanrides.dev',
  telefone: '51999998888',
);

const _rota = AdminRotaBaseRecord(
  id: 21,
  nome: 'Rota Escolar Zona Sul',
  motorista: _motorista,
  quantidadeParadas: 12,
  passageiroIds: [],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  diasOperacao: ['SEG', 'TER', 'QUA', 'QUI', 'SEX'],
);

const _servico = AdminServicoRecorrenteRecord(
  id: 41,
  rotaBaseLegadaId: 21,
  nome: 'Rota Escolar Zona Sul',
  tipoOperacao: 'IDA',
  motoristaPadrao: _motorista,
  quantidadePassageiros: 12,
  passageiroIds: [11],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['SEG', 'TER', 'QUA', 'QUI', 'SEX'],
);

AdminViagemManifestPreviewRecord _manifestPreview(int viagemId) {
  return AdminViagemManifestPreviewRecord(
    viagemId: viagemId,
    servicoId: 41,
    servicoNome: 'Rota Escolar Zona Sul',
    dataOperacao: DateTime(2026, 4, 15),
    paradasAtuais: 1,
    paradasAposSincronizacao: 1,
    adicionados: 0,
    removidos: 0,
    mantidos: 1,
    mudancas: 0,
    semMudancas: true,
    passageirosAdicionados: const [],
    passageirosRemovidos: const [],
    passageirosMantidos: const [
      AdminViagemManifestPassengerRecord(
        passageiroId: 11,
        nome: 'Matheus Passageiro',
        menorDeIdade: false,
        ordemAtual: 1,
        ordemNova: 1,
      ),
    ],
  );
}

final _viagem = AdminViagemRecord(
  id: 31,
  rotaBaseId: 21,
  servicoId: 41,
  rotaNome: 'Rota Escolar Zona Sul',
  servicoNome: 'Rota Escolar Zona Sul',
  tipoOperacao: 'IDA',
  dataOperacao: DateTime(2026, 4, 15),
  status: 'PLANEJADA',
  motorista: _motorista,
  quantidadeParadas: 12,
);

AdminViagemRecord _viagemForDate(DateTime date) {
  return AdminViagemRecord(
    id: 31,
    rotaBaseId: 21,
    servicoId: 41,
    rotaNome: 'Rota Escolar Zona Sul',
    servicoNome: 'Rota Escolar Zona Sul',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(date.year, date.month, date.day),
    status: 'PLANEJADA',
    motorista: _motorista,
    quantidadeParadas: 12,
  );
}

AdminViagemRecord _viagemWithStatus(int id, String status) {
  return AdminViagemRecord(
    id: id,
    rotaBaseId: 21,
    servicoId: 41,
    rotaNome: 'Rota Escolar Zona Sul',
    servicoNome: 'Rota Escolar Zona Sul $id',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 4, 15),
    status: status,
    motorista: _motorista,
    quantidadeParadas: 12,
  );
}

AdminViagemDetailRecord _viagemDetail(
  AdminMotoristaRecord motorista, {
  String status = 'PLANNED',
  String presenceStatus = 'CONFIRMED',
}) {
  final confirmedPresence = presenceStatus == 'CONFIRMED';
  return AdminViagemDetailRecord(
    id: 31,
    rotaBaseId: 21,
    servicoId: 41,
    rotaNome: 'Rota Escolar Zona Sul',
    servicoNome: 'Rota Escolar Zona Sul',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 4, 15),
    status: status,
    motorista: motorista,
    quantidadeParadas: 1,
    horarioPartida: '07:30',
    rotaVersao: motorista.id == 8 ? 2 : 1,
    etaMinutosAteProximaParada: 12,
    destinoFinal: const AdminViagemDestinoFinalRecord(nome: 'Escola Central'),
    paradas: [
      AdminViagemParadaRecord(
        id: 51,
        ordem: 1,
        status: 'PENDING',
        etaMinutos: 12,
        confirmadoPresenca: confirmedPresence,
        presencaId: 501,
        presencaStatus: presenceStatus,
        proximaParada: true,
        passageiro: const AdminViagemPassageiroRecord(
          id: 99,
          nome: 'Passageiro Detalhe',
          menorDeIdade: false,
        ),
      ),
    ],
    proximaParada: AdminViagemParadaRecord(
      id: 51,
      ordem: 1,
      status: 'PENDING',
      etaMinutos: 12,
      confirmadoPresenca: confirmedPresence,
      presencaId: 501,
      presencaStatus: presenceStatus,
      proximaParada: true,
      passageiro: const AdminViagemPassageiroRecord(
        id: 99,
        nome: 'Passageiro Detalhe',
        menorDeIdade: false,
      ),
    ),
    fontePlanejamento: 'SERVICO_RECORRENTE',
    snapshotOperacional: true,
    rotaGeografica: _adminRouteGeometry,
  );
}

const _adminRouteGeometry = TripRouteGeometryRecord(
  provider: 'GOOGLE_DIRECTIONS_V1',
  polyline: '',
  navegacao: TripNavigationRecord(
    provider: 'GOOGLE_MAPS_HANDOFF',
    url: 'https://maps.google.com/?q=escola',
    origem: 'Garagem',
    destino: 'Escola Central',
    quantidadeWaypoints: 2,
  ),
  waypoints: [
    TripRouteWaypointRecord(
      tipo: 'STOP',
      paradaId: 51,
      passageiroId: 99,
      nome: 'Passageiro Detalhe',
      latitude: -30.02,
      longitude: -51.02,
      ordem: 1,
      proximaParada: true,
      paradaDoPassageiro: false,
    ),
    TripRouteWaypointRecord(
      tipo: 'DESTINATION',
      paradaId: null,
      passageiroId: null,
      nome: 'Escola Central',
      latitude: -30.01,
      longitude: -51.01,
      ordem: 2,
      proximaParada: false,
      paradaDoPassageiro: false,
    ),
  ],
  legs: [
    TripRouteLegRecord(
      indice: 0,
      origemLabel: 'Garagem',
      destinoLabel: 'Passageiro Detalhe',
      distanciaMetros: 1200,
      duracaoSegundos: 480,
    ),
  ],
  filaTotalParadas: 2,
  posicaoPassageiroNaFila: null,
  paradasAntesDoPassageiro: null,
);
