import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../data/driver_tracking_platform.dart';

/// A local, explicit export of the same sanitized snapshot shown on screen.
class DriverTrackingDiagnosticsPanel extends StatelessWidget {
  const DriverTrackingDiagnosticsPanel({required this.status, super.key});

  final DriverTrackingStatus status;

  Future<void> _copy(BuildContext context, String snapshot) async {
    try {
      await Clipboard.setData(ClipboardData(text: snapshot));
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Diagnóstico sanitizado copiado.')),
      );
    } catch (_) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Não foi possível copiar o diagnóstico.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = const JsonEncoder.withIndent(
      '  ',
    ).convert(status.toSanitizedMap());
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'Diagnóstico sem coordenadas nem credenciais. '
          'Tempos e idades em ms; precisão em metros. '
          'Idades calculadas no snapshot; AtCallback registra a chegada. '
          'Contadores de callbacks por processo, uma entrega por amostra. '
          'Campos ausentes não foram informados. '
          'FUTURE indica tempo futuro; INVALID indica valor inválido.',
        ),
        const SizedBox(height: 8),
        if (status.diagnostics.values.containsKey('energySession')) ...[
          const Text(
            'Energia: percentual e marcos observados pelo Android. '
            'A primeira janela sem alimentação termina ao reconectar energia. '
            'POWER_RETURNED, POWER_UNKNOWN, UNAVAILABLE ou INCOMPLETE exigem '
            'revisão; dois snapshots não comprovam consumo contínuo. '
            'Sem cálculo de autonomia ou aprovação automática.',
          ),
          const SizedBox(height: 8),
        ],
        SelectableText(snapshot),
        OutlinedButton.icon(
          onPressed: () => _copy(context, snapshot),
          icon: const Icon(Icons.copy),
          label: const Text('Copiar diagnóstico sanitizado'),
        ),
      ],
    );
  }
}
