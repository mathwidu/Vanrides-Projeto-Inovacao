import 'package:flutter/material.dart';

import 'admin_portal_layout.dart';

class AdminViagensMaintenanceGuide extends StatelessWidget {
  const AdminViagensMaintenanceGuide({super.key, required this.viagensCount});

  final int viagensCount;

  @override
  Widget build(BuildContext context) {
    return const AdminPortalSurface(
      radius: 28,
      padding: EdgeInsets.symmetric(horizontal: 22, vertical: 18),
      color: AdminPortalColors.surfaceSoft,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Manutenção rápida',
            style: TextStyle(
              color: AdminPortalColors.text,
              fontSize: 18,
              fontWeight: FontWeight.w800,
            ),
          ),
          SizedBox(height: 6),
          Text(
            'Mudou passageiro, motorista ou presença? Atualize o dia e revise só as listas marcadas.',
            style: TextStyle(
              color: AdminPortalColors.text,
              fontSize: 14,
              fontWeight: FontWeight.w700,
            ),
          ),
          SizedBox(height: 4),
          Text(
            'Abra a viagem existente e use Sincronizar lista quando o serviço mudar. Exceção do dia é só para algo fora do plano recorrente.',
            style: TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 12,
              height: 1.35,
            ),
          ),
        ],
      ),
    );
  }
}
