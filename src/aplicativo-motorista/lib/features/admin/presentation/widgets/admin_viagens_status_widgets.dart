import 'package:flutter/material.dart';

import '../admin_viagens_status_rules.dart';
import 'admin_portal_layout.dart';

export '../admin_viagens_status_rules.dart';

class AdminViagensStatusTag extends StatelessWidget {
  const AdminViagensStatusTag({super.key, required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final config = switch (status) {
      String s when adminViagensIsTripCompletedStatus(s) => (
        bg: const Color(0xFFD1FAE5),
        fg: const Color(0xFF047857),
        dot: const Color(0xFF10B981),
        label: 'CONCLUÍDA',
      ),
      String s when adminViagensIsTripInProgressStatus(s) => (
        bg: AdminPortalColors.orangeSoft,
        fg: const Color(0xFF884800),
        dot: const Color(0xFF884800),
        label: 'EM TRÂNSITO',
      ),
      String s when adminViagensIsTripCanceledStatus(s) => (
        bg: const Color(0xFFFEE2E2),
        fg: const Color(0xFFB91C1C),
        dot: const Color(0xFFB91C1C),
        label: 'CANCELADA',
      ),
      _ => (
        bg: AdminPortalColors.blueSoft,
        fg: AdminPortalColors.blue,
        dot: AdminPortalColors.blue,
        label: 'AGENDADA',
      ),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: config.bg,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: BoxDecoration(
              color: config.dot,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            config.label,
            style: TextStyle(
              color: config.fg,
              fontSize: 11,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}
