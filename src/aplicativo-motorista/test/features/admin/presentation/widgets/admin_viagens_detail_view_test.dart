import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_detail_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_viagens_detail_view.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';

void main() {
  testWidgets('detalhe administrativo preserva acoes principais', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1600));
    var wentBack = false;
    var refreshed = false;
    var syncedManifest = false;
    var cancelled = false;
    int? changedMotoristaId;
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
      _wrap(
        AdminViagemDetailView(
          detail: _detail,
          session: _session,
          motoristas: const [_motorista, _motoristaReserva],
          isUpdatingMotorista: false,
          isSyncingManifest: false,
          isCancellingTrip: false,
          updatingPresenceParadaId: null,
          mapRenderMode: TripLiveMapRenderMode.placeholder,
          onBack: () {
            wentBack = true;
          },
          onRefresh: () async {
            refreshed = true;
          },
          onChangeMotorista: (motoristaId) {
            changedMotoristaId = motoristaId;
          },
          onSyncManifest: () async {
            syncedManifest = true;
          },
          onCancelTrip: () async {
            cancelled = true;
          },
          onUpdatePresence: (_, _) async {},
        ),
      ),
    );

    expect(find.text('Rota Escolar Zona Sul'), findsWidgets);
    expect(find.text('Motorista desta viagem'), findsOneWidget);
    expect(find.text('Mapa operacional'), findsOneWidget);
    expect(find.text('Lista da viagem'), findsOneWidget);
    expect(find.text('Estado publicado'), findsOneWidget);

    await tester.tap(find.text('Voltar'));
    await tester.tap(find.text('Atualizar'));
    expect(find.text('Sincronizar lista'), findsOneWidget);
    expect(
      find.text(
        'A lista vem do serviço recorrente. Sincronize aqui quando passageiro ou presença mudar antes do início.',
      ),
      findsOneWidget,
    );

    await tester.tap(find.text('Sincronizar lista'));
    await tester.tap(find.text('Cancelar viagem'));
    await tester.tap(find.text('Copiar link'));
    await tester.pumpAndSettle();

    expect(wentBack, isTrue);
    expect(refreshed, isTrue);
    expect(syncedManifest, isTrue);
    expect(cancelled, isTrue);
    expect(copiedText, 'vanrides://viagem/31');

    await tester.tap(find.byType(DropdownButtonFormField<int>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Motorista Reserva').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Trocar motorista'));
    await tester.pumpAndSettle();

    expect(changedMotoristaId, _motoristaReserva.id);
  });
}

Widget _wrap(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(child: SizedBox(width: 1440, child: child)),
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

const _motoristaReserva = AdminMotoristaRecord(
  id: 8,
  nome: 'Motorista Reserva',
  email: 'reserva@vanrides.dev',
  telefone: '51888888888',
);

final _detail = AdminViagemDetailRecord(
  id: 31,
  rotaBaseId: 21,
  servicoId: 41,
  rotaNome: 'Rota Escolar Zona Sul',
  servicoNome: 'Rota Escolar Zona Sul',
  tipoOperacao: 'IDA',
  dataOperacao: DateTime(2026, 4, 15),
  status: 'PLANNED',
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
        nome: 'Passageiro Confirmado',
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
      nome: 'Passageiro Confirmado',
      menorDeIdade: false,
    ),
  ),
  fontePlanejamento: 'SERVICO_RECORRENTE',
  snapshotOperacional: true,
  rotaGeografica: _routeGeometry,
);

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
