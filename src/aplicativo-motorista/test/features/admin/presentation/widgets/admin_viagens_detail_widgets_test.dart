import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_detail_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_viagens_detail_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';

void main() {
  testWidgets('renderiza lista da viagem e aciona correcao de presenca', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 900));
    String? selectedStatus;
    int? selectedStopId;

    await tester.pumpWidget(
      _wrap(
        AdminViagensManifestSurface(
          paradas: _detail.paradas,
          canEditPresence: true,
          updatingPresenceParadaId: null,
          onUpdatePresence: (parada, status) async {
            selectedStopId = parada.id;
            selectedStatus = status;
          },
        ),
      ),
    );

    expect(find.text('Lista da viagem'), findsOneWidget);
    expect(find.text('Passageiro Confirmado'), findsOneWidget);
    expect(find.text('Passageiro Ausente'), findsOneWidget);
    expect(find.text('VAI'), findsOneWidget);
    expect(find.text('AUSENTE'), findsOneWidget);

    await tester.tap(find.byTooltip('Corrigir presença').first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ausente'));
    await tester.pumpAndSettle();

    expect(selectedStopId, 51);
    expect(selectedStatus, 'ABSENT');
  });

  testWidgets('resume estado publicado da lista mista', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));

    await tester.pumpWidget(
      _wrap(AdminViagensSnapshotSurface(detail: _detail)),
    );

    expect(find.text('Estado publicado'), findsOneWidget);
    expect(find.text('3 paradas'), findsOneWidget);
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
    expect(find.text('#41 • Rota Escolar Zona Sul'), findsOneWidget);
  });

  testWidgets('renderiza resumo do mapa operacional', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1200, 900));

    await tester.pumpWidget(
      _wrap(
        AdminViagensTripMapSurface(
          detail: _detail,
          session: _session,
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    expect(find.text('Mapa operacional'), findsOneWidget);
    expect(find.text('Rota de ruas'), findsOneWidget);
    expect(find.text('2 pontos publicados'), findsWidgets);
    expect(find.text('Passageiro Confirmado'), findsWidgets);
    expect(find.text('Garagem para Escola Central'), findsOneWidget);
  });
}

Widget _wrap(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(child: SizedBox(width: 1200, child: child)),
    ),
  );
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

const _motorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

final _detail = AdminViagemDetailRecord(
  id: 31,
  rotaBaseId: 21,
  servicoId: 41,
  rotaNome: 'Rota Escolar Zona Sul',
  servicoNome: 'Rota Escolar Zona Sul',
  tipoOperacao: 'IDA',
  dataOperacao: _operationDate,
  status: 'PLANNED',
  motorista: _motorista,
  quantidadeParadas: 3,
  horarioPartida: '07:30',
  rotaVersao: 1,
  etaMinutosAteProximaParada: 12,
  destinoFinal: const AdminViagemDestinoFinalRecord(nome: 'Escola Central'),
  paradas: [
    const AdminViagemParadaRecord(
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
    const AdminViagemParadaRecord(
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
    const AdminViagemParadaRecord(
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
  rotaGeografica: _routeGeometry,
);

final _operationDate = DateTime(2026, 4, 15);

const _routeGeometry = TripRouteGeometryRecord(
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
      nome: 'Passageiro Confirmado',
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
      destinoLabel: 'Passageiro Confirmado',
      distanciaMetros: 1200,
      duracaoSegundos: 480,
    ),
  ],
  filaTotalParadas: 2,
  posicaoPassageiroNaFila: null,
  paradasAntesDoPassageiro: null,
);
