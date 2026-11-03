import 'package:flutter/material.dart';

import '../admin_viagens_review_intent.dart';
import 'admin_portal_layout.dart';

class AdminViagensReviewIntentBanner extends StatelessWidget {
  const AdminViagensReviewIntentBanner({
    super.key,
    required this.intent,
    required this.isGeneratingAutomatic,
    required this.onGenerateAutomatic,
  });

  static const generateButtonKey = Key('admin.viagens.reviewIntent.generate');

  final AdminViagensReviewIntent intent;
  final bool isGeneratingAutomatic;
  final VoidCallback onGenerateAutomatic;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 32,
      color: AdminPortalColors.surfaceSoft,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 840;
          final copy = Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              AdminPortalTag(
                label: 'REVISÃO GUIADA • ${intent.sourceLabel}',
                background: AdminPortalColors.blue.withValues(alpha: 0.16),
                foreground: AdminPortalColors.blue,
              ),
              const SizedBox(height: 14),
              Text(
                intent.title,
                style: const TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                intent.description,
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 13,
                  height: 1.45,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'Não delete viagens para incluir passageiros. Atualize o dia e revise as listas pendentes.',
                style: TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  height: 1.4,
                ),
              ),
            ],
          );
          final actions = Wrap(
            spacing: 12,
            runSpacing: 12,
            alignment: compact ? WrapAlignment.start : WrapAlignment.end,
            children: [
              AdminPortalPrimaryButton(
                key: generateButtonKey,
                label:
                    isGeneratingAutomatic ? 'Atualizando...' : 'Atualizar dia',
                icon: Icons.auto_awesome_motion_outlined,
                onPressed: isGeneratingAutomatic ? null : onGenerateAutomatic,
              ),
            ],
          );

          if (compact) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [copy, const SizedBox(height: 18), actions],
            );
          }

          return Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Expanded(child: copy),
              const SizedBox(width: 24),
              actions,
            ],
          );
        },
      ),
    );
  }
}
