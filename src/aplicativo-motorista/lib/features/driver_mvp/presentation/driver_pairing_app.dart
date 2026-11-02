import 'dart:async';

import 'package:flutter/material.dart';

import '../data/driver_session_links.dart';
import '../data/driver_session_platform.dart';
import '../data/driver_tracking_platform.dart';
import 'driver_collection_panel.dart';
import 'driver_pairing_controller.dart';

class DriverPairingApp extends StatelessWidget {
  const DriverPairingApp({
    this.session = const NativeDriverSessionPlatform(),
    this.tracking = const NativeDriverTrackingPlatform(),
    this.links,
    super.key,
  });
  final DriverSessionPlatform session;
  final DriverTrackingPlatform tracking;
  final DriverSessionLinks? links;

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'VanRides Motorista',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF176B52)),
    ),
    home: _PairingPage(session: session, tracking: tracking, links: links),
  );
}

class _PairingPage extends StatefulWidget {
  const _PairingPage({
    required this.session,
    required this.tracking,
    required this.links,
  });
  final DriverSessionPlatform session;
  final DriverTrackingPlatform tracking;
  final DriverSessionLinks? links;

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
    );
    WidgetsBinding.instance.addObserver(this);
    unawaited(controller.initialize());
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
        'Com seu cadastro de motorista confirmado no WhatsApp, toque abaixo para gerar o código deste aparelho.',
      ),
      DriverSessionState.requesting => (
        'Concluindo seu pedido',
        'Toque em atualizar para recuperar o pedido deste aparelho.',
      ),
      DriverSessionState.pending => (
        'Confirme pelo WhatsApp',
        'Mostre o código e envie somente as oito letras na sua conversa com o VanRides. Depois volte aqui.',
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
        'Gere um novo código para vincular este aparelho.',
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
              mayBegin && controller.fromLink
                  ? 'Feche o VanRides e abra novamente pelo ícone para criar um vínculo.'
                  : instruction,
            ),
            const SizedBox(height: 12),
            if (mayBegin && controller.launchChecked && !controller.fromLink)
              FilledButton(
                onPressed:
                    controller.busy
                        ? null
                        : () =>
                            controller.act(DriverSessionAction.beginPairing),
                child: const Text('Vincular este celular'),
              ),
            if (state == DriverSessionState.pending) ...[
              if (controller.code == null)
                FilledButton(
                  onPressed: controller.busy ? null : controller.reveal,
                  child: const Text('Mostrar código'),
                )
              else ...[
                Text(
                  controller.code!,
                  style: Theme.of(context).textTheme.headlineMedium,
                  key: const Key('driver-pairing-code'),
                ),
                OutlinedButton(
                  onPressed: controller.busy ? null : _copyCode,
                  child: const Text('Copiar código'),
                ),
              ],
              if (controller.links.hasConversation)
                TextButton(
                  onPressed:
                      controller.busy ? null : controller.openConversation,
                  child: const Text('Abrir conversa no WhatsApp'),
                ),
            ],
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
            DriverCollectionPanel(
              platform: widget.tracking,
              paired: paired,
              intent: controller.runIntent,
              onStarted: controller.runStarted,
            ),
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
