import 'package:flutter/material.dart';

import 'admin_portal_layout.dart';

class AdminViagensErrorState extends StatelessWidget {
  const AdminViagensErrorState({
    super.key,
    required this.message,
    required this.onRetry,
  });

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return AdminPortalPage(
      title: 'Viagens do dia',
      description: 'Não foi possível carregar a área de viagens.',
      child: AdminPortalSurface(
        radius: 40,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Falha ao carregar viagens',
              style: TextStyle(
                color: AdminPortalColors.text,
                fontSize: 24,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              message,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 15,
              ),
            ),
            const SizedBox(height: 24),
            AdminPortalPrimaryButton(
              label: 'Tentar novamente',
              icon: Icons.refresh_rounded,
              onPressed: onRetry,
            ),
          ],
        ),
      ),
    );
  }
}
