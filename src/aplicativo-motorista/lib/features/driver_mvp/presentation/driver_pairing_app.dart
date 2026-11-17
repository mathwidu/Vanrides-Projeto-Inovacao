import 'dart:async';

import 'package:flutter/material.dart';

import '../data/driver_session_links.dart';
import '../data/driver_session_platform.dart';
import '../data/driver_tracking_platform.dart';
import '../data/driver_finish_platform.dart';
import '../data/driver_pairing_link_platform.dart';
import 'driver_finish_panel.dart';
import 'driver_collection_panel.dart';
import 'driver_pairing_controller.dart';

class DriverPairingApp extends StatelessWidget {
  const DriverPairingApp({
    this.session = const NativeDriverSessionPlatform(),
    this.tracking = const NativeDriverTrackingPlatform(),
    this.links,
    this.finishing = const NativeDriverFinishPlatform(),
    this.pairing = const NativeDriverPairingLinkPlatform(),
    super.key,
  });
  final DriverSessionPlatform session;
  final DriverTrackingPlatform tracking;
  final DriverSessionLinks? links;
  final DriverFinishPlatform finishing;
  final DriverPairingLinkPlatform pairing;

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'VanRides Motorista',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF176B52)),
    ),
    home: _PairingPage(
      session: session,
      tracking: tracking,
      links: links,
      finishing: finishing,
      pairing: pairing,
    ),
  );
}

class _PairingPage extends StatefulWidget {
  const _PairingPage({
    required this.session,
    required this.tracking,
    required this.links,
    required this.finishing,
    required this.pairing,
  });
  final DriverSessionPlatform session;
  final DriverTrackingPlatform tracking;
  final DriverSessionLinks? links;
  final DriverFinishPlatform finishing;
  final DriverPairingLinkPlatform pairing;

  @override
  State<_PairingPage> createState() => _PairingPageState();
}

class _PairingPageState extends State<_PairingPage>
    with WidgetsBindingObserver {
  late final DriverPairingController controller;

  @override
  void initState() {
    super.initState();
    controller = DriverPairingController(
      widget.session,
      widget.links ?? DeviceSessionLinks(),
      pairing: widget.pairing,
    );
    WidgetsBinding.instance.addObserver(this);
    unawaited(_initialize());
  }

  Future<void> _initialize() async {
    await controller.initialize();
    if (!mounted ||
        controller.runIntent != null ||
        controller.finishIntent != null ||
        controller.pairingIntent != null) {
      return;
    }
    try {
      final pending = await widget.finishing.pendingFinish();
      if (mounted && pending != null) {
        controller.restoreFinish(pending.intentId, pending.version);
      }
    } catch (_) {
      /* The collector and its local stop remain independently available. */
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) =>
      controller.foreground(state == AppLifecycleState.resumed);

  Future<void> _copyCode() async {
    if (await controller.copy() && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Código copiado. Cole-o na conversa do VanRides.'),
        ),
      );
    }
  }

  Future<void> _confirmRevoke() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder:
          (context) => AlertDialog(
            title: const Text('Desvincular este celular?'),
            content: const Text(
              'Para autorizar novos percursos neste aparelho, será preciso vinculá-lo novamente pelo WhatsApp.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Manter vínculo'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Desvincular'),
              ),
            ],
          ),
    );
    if (mounted && confirmed == true) {
      await controller.act(DriverSessionAction.revokeSession);
    }
  }

  @override
  void dispose() {
    controller.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) => _content(context),
  );

  Widget _content(BuildContext context) {
    final state = controller.snapshot?.state;
    final paired =
        state == DriverSessionState.ready ||
        state == DriverSessionState.setupRequired;
    final mayBegin =
        state == DriverSessionState.unpaired ||
        state == DriverSessionState.expired ||
        state == DriverSessionState.revoked;
    final (heading, instruction) = switch (state) {
      null => (
        'Verificando o aparelho',
        'Aguarde enquanto o VanRides lê o vínculo salvo.',
      ),
      DriverSessionState.unconfigured => (
        'Ambiente não configurado',
        'Peça à equipe VanRides a versão do motorista para o seu ambiente de teste.',
      ),
      DriverSessionState.unpaired => (
        'Vincule seu celular',
        'No WhatsApp, envie Conectar meu celular. Abra o link recebido para conectar este aparelho.',
      ),
      DriverSessionState.requesting => (
        'Concluindo seu pedido',
        'Toque em atualizar para recuperar o pedido deste aparelho.',
      ),
      DriverSessionState.pending => (
        'Confirme pelo WhatsApp',
        controller.handoff
            ? 'No WhatsApp, confirme com Conectar este celular. Depois abra Concluir no app para terminar.'
            : 'Seu pedido antigo está salvo. Você pode continuá-lo nos detalhes ou pedir Conectar meu celular no WhatsApp.',
      ),
      DriverSessionState.ready when controller.error != null => (
        'Vínculo salvo neste celular',
        'Atualize para confirmar a autorização com o VanRides.',
      ),
      DriverSessionState.ready => (
        'Aparelho vinculado',
        'Este celular está autorizado para o motorista cadastrado no WhatsApp.',
      ),
      DriverSessionState.setupRequired => (
        'Verifique o vínculo',
        'Atualize o estado e confira no WhatsApp se o cadastro do motorista está completo.',
      ),
      DriverSessionState.expired => (
        'Renove o vínculo',
        'No WhatsApp, envie Conectar meu celular para receber um novo link.',
      ),
      DriverSessionState.revoking => (
        'Desvinculação em andamento',
        'Atualize para confirmar a retirada deste aparelho.',
      ),
      DriverSessionState.revoked => (
        'Aparelho desvinculado',
        'Para voltar a usar este celular, faça um novo vínculo.',
      ),
    };
    return Scaffold(
      appBar: AppBar(title: const Text('VanRides Motorista')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Semantics(
              header: true,
              child: Text(
                heading,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              mayBegin &&
                      controller.fromLink &&
                      controller.pairingIntent == null
                  ? 'No WhatsApp, envie Conectar meu celular para receber o link deste aparelho.'
                  : instruction,
            ),
            const SizedBox(height: 12),
            if (controller.pairingIntent != null) ...[
              const Text(
                'Conectar este celular ao VanRides não liga a localização. A confirmação do vínculo será feita na conversa do WhatsApp.',
              ),
              if (controller.requiresReplacement)
                const Text(
                  'Outro celular está conectado. Usar este aparelho substituirá o vínculo anterior depois da sua aprovação no WhatsApp.',
                ),
              FilledButton(
                onPressed:
                    controller.busy
                        ? null
                        : () => controller.connect(
                          replace: controller.requiresReplacement,
                        ),
                child: Text(
                  controller.requiresReplacement
                      ? 'Usar este celular'
                      : 'Conectar este celular',
                ),
              ),
            ],
            if ((mayBegin || state == DriverSessionState.pending) &&
                controller.links.hasConversation)
              TextButton(
                onPressed: controller.busy ? null : controller.openConversation,
                child: const Text('Abrir conversa no WhatsApp'),
              ),
            if (controller.snapshot?.handoff == true &&
                controller.pairingIntent == null &&
                controller.canReconcile)
              FilledButton(
                onPressed:
                    controller.busy ? null : controller.continueConnection,
                child: const Text('Continuar conexão'),
              ),
            if (!controller.handoff &&
                ((mayBegin &&
                        controller.launchChecked &&
                        !controller.fromLink) ||
                    state == DriverSessionState.pending))
              ExpansionTile(
                title: const Text('Detalhes e conexão antiga'),
                children: [
                  if (mayBegin)
                    TextButton(
                      onPressed:
                          controller.busy
                              ? null
                              : () => controller.act(
                                DriverSessionAction.beginPairing,
                              ),
                      child: const Text('Gerar código de conexão antiga'),
                    ),
                  if (state == DriverSessionState.pending &&
                      controller.code == null)
                    TextButton(
                      onPressed: controller.busy ? null : controller.reveal,
                      child: const Text('Mostrar código'),
                    ),
                  if (state == DriverSessionState.pending &&
                      controller.code != null) ...[
                    Text(
                      controller.code!,
                      key: const Key('driver-pairing-code'),
                    ),
                    TextButton(
                      onPressed: controller.busy ? null : _copyCode,
                      child: const Text('Copiar código'),
                    ),
                  ],
                ],
              ),
            if (controller.error != null)
              Semantics(liveRegion: true, child: Text(controller.error!)),
            if (controller.canReconcile || controller.error != null)
              TextButton(
                onPressed:
                    controller.busy
                        ? null
                        : () => controller.act(
                          controller.canReconcile
                              ? DriverSessionAction.syncSession
                              : DriverSessionAction.getState,
                        ),
                child: const Text('Atualizar vínculo'),
              ),
            if (paired)
              TextButton(
                onPressed: controller.busy ? null : _confirmRevoke,
                child: const Text('Desvincular este celular'),
              ),
            const SizedBox(height: 16),
            if (controller.finishIntent != null)
              DriverFinishPanel(
                intent: controller.finishIntent!,
                platform: widget.finishing,
                onFinished: controller.runFinished,
              ),
            DriverCollectionPanel(
              key: ValueKey(controller.collectionRevision),
              platform: widget.tracking,
              paired: paired,
              intent: controller.runIntent,
              onStarted: controller.runStarted,
            ),
            if (controller.conversationReturnPending) ...[
              Semantics(
                liveRegion: true,
                child: const Text(
                  'Para voltar à conversa, abra o WhatsApp e procure o VanRides.',
                ),
              ),
              if (controller.links.hasConversation)
                TextButton(
                  onPressed:
                      controller.openingConversation
                          ? null
                          : controller.returnToConversation,
                  child: const Text('Voltar à conversa no WhatsApp'),
                ),
            ],
            const SizedBox(height: 12),
            const Text(
              'Precisa de ajuda? Envie AJUDA na sua conversa com o VanRides.',
            ),
          ],
        ),
      ),
    );
  }
}
