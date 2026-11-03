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

  testWidgets('atualiza lista da viagem planejada por servico', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1500));
    final gateway = _ManifestAdminViagensGateway();

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

    expect(find.text('Sincronizar lista'), findsOneWidget);
    await tester.tap(find.text('Sincronizar lista'));
    await tester.pumpAndSettle();

    expect(gateway.previewedTripId, 31);
    expect(gateway.syncedTripId, isNull);
    expect(find.text('Revisar atualização da lista'), findsOneWidget);
    expect(find.text('Entram na lista'), findsOneWidget);
    await tester.tap(find.text('Aplicar atualização'));
    await tester.pumpAndSettle();

    expect(gateway.syncedTripId, 31);
    expect(find.text('Lista atualizada com 3 paradas.'), findsOneWidget);
    expect(find.text('Passageiro Sem Decisão'), findsOneWidget);
  });

  testWidgets('resume presenca da lista no estado publicado', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1600));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminViagensScreen(
            session: session,
            gateway: _ManifestAdminViagensGateway(fetchMixedManifest: true),
            mapRenderMode: TripLiveMapRenderMode.placeholder,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.visibility_outlined));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.text('Estado publicado'),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();

    expect(find.text('3 paradas'), findsWidgets);
    expect(find.text('Vai: 1'), findsOneWidget);
    expect(find.text('Ausente: 1'), findsOneWidget);
    expect(find.text('Sem decisão: 1'), findsOneWidget);
    expect(
      find.text(
        'Lista publicada com 1 confirmados, 1 ausentes, 1 sem decisão.',
      ),
      findsOneWidget,
    );
    expect(find.text('Estado publicado do dia'), findsOneWidget);
  });
}

class _ManifestAdminViagensGateway implements AdminViagensGateway {
  _ManifestAdminViagensGateway({this.fetchMixedManifest = false});

  final bool fetchMixedManifest;
  int? previewedTripId;
  int? syncedTripId;

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
  ];

  @override
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) async => [
    _viagem,
  ];

  @override
  Future<AdminViagemDetailRecord> fetchViagemDetail(int viagemId) async =>
      fetchMixedManifest ? _viagemDetailComManifestoMisto() : _viagemDetail();

  @override
  Future<AdminViagemDetailRecord> updateViagemMotorista(
    int viagemId,
    int motoristaId,
  ) async => _viagemDetail();

  @override
  Future<AdminViagemManifestPreviewRecord> fetchManifestPreview(
    int viagemId,
  ) async {
    previewedTripId = viagemId;
    return AdminViagemManifestPreviewRecord(
      viagemId: viagemId,
      servicoId: 41,
      servicoNome: 'Rota Escolar Zona Sul',
      dataOperacao: DateTime(2026, 4, 15),
      paradasAtuais: 2,
      paradasAposSincronizacao: 3,
      adicionados: 1,
      removidos: 0,
      mantidos: 2,
      mudancas: 1,
      semMudancas: false,
      passageirosAdicionados: const [
        AdminViagemManifestPassengerRecord(
          passageiroId: 13,
          nome: 'Passageiro Sem Decisão',
          menorDeIdade: false,
          ordemNova: 3,
        ),
      ],
      passageirosRemovidos: const [],
      passageirosMantidos: const [],
    );
  }

  @override
  Future<AdminViagemDetailRecord> cancelViagem(int viagemId) async =>
      _viagemDetail(status: 'CANCELED');

  @override
  Future<AdminViagemDetailRecord> syncManifesto(int viagemId) async {
    syncedTripId = viagemId;
    return _viagemDetailComManifestoMisto();
  }

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

AdminViagemDetailRecord _viagemDetail({String status = 'PLANNED'}) {
  return AdminViagemDetailRecord(
    id: 31,
    rotaBaseId: 21,
    servicoId: 41,
    rotaNome: 'Rota Escolar Zona Sul',
    servicoNome: 'Rota Escolar Zona Sul',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 4, 15),
    status: status,
    motorista: _motorista,
    quantidadeParadas: 1,
    horarioPartida: '07:30',
    rotaVersao: 1,
    etaMinutosAteProximaParada: 12,
    destinoFinal: const AdminViagemDestinoFinalRecord(nome: 'Escola Central'),
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
    rotaGeografica: _adminRouteGeometry,
  );
}

AdminViagemDetailRecord _viagemDetailComManifestoMisto() {
  return AdminViagemDetailRecord(
    id: 31,
    rotaBaseId: 21,
    servicoId: 41,
    rotaNome: 'Rota Escolar Zona Sul',
    servicoNome: 'Rota Escolar Zona Sul',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 4, 15),
    status: 'PLANNED',
    motorista: _motorista,
    quantidadeParadas: 3,
    horarioPartida: '07:30',
    rotaVersao: 1,
    etaMinutosAteProximaParada: 12,
    destinoFinal: const AdminViagemDestinoFinalRecord(nome: 'Escola Central'),
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
          nome: 'Passageiro Confirmado',
          menorDeIdade: false,
        ),
      ),
      AdminViagemParadaRecord(
        id: 52,
        ordem: 2,
        status: 'PENDING',
        etaMinutos: 18,
        confirmadoPresenca: false,
        presencaId: 502,
        presencaStatus: 'ABSENT',
        proximaParada: false,
        passageiro: AdminViagemPassageiroRecord(
          id: 100,
          nome: 'Passageiro Ausente',
          menorDeIdade: false,
        ),
      ),
      AdminViagemParadaRecord(
        id: 53,
        ordem: 3,
        status: 'PENDING',
        etaMinutos: 24,
        confirmadoPresenca: false,
        presencaId: null,
        presencaStatus: 'UNKNOWN',
        proximaParada: false,
        passageiro: AdminViagemPassageiroRecord(
          id: 101,
          nome: 'Passageiro Sem Decisão',
          menorDeIdade: true,
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
        nome: 'Passageiro Confirmado',
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
