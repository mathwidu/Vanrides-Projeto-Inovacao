import 'package:flutter/material.dart';

import '../../../app/theme/vanrides_design_tokens.dart';
import '../../../app/widgets/vanrides_liquid_surface.dart';
import '../../../app/widgets/vanrides_status_pill.dart';

class UnsupportedRoleScreen extends StatelessWidget {
  const UnsupportedRoleScreen({super.key, required this.roleLabel});

  final String roleLabel;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: VanRidesDesignTokens.backgroundDeep,
      appBar: AppBar(title: const Text('Conta em análise')),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 720),
              child: VanRidesLiquidSurface.card(
                semanticLabel: 'Status da conta',
                borderRadius: 32,
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const VanRidesStatusPill(
                      label: 'Suporte VanRides',
                      icon: Icons.verified_user_outlined,
                      tone: VanRidesStatusTone.tracking,
                    ),
                    const SizedBox(height: 20),
                    Text(
                      'Vamos ajustar seu acesso com segurança',
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        color: VanRidesDesignTokens.textPrimary,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'Sua conta entrou com o perfil "$roleLabel". Para proteger seus dados e a operação da empresa, nossa equipe precisa confirmar qual experiência deve ser liberada para este acesso.',
                      style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: VanRidesDesignTokens.textSecondary,
                        height: 1.4,
                      ),
                    ),
                    const SizedBox(height: 24),
                    const _SupportStep(
                      icon: Icons.account_circle_outlined,
                      title: 'Conta identificada',
                      description:
                          'A autenticação foi concluída e o perfil foi recebido pelo app.',
                    ),
                    const SizedBox(height: 14),
                    const _SupportStep(
                      icon: Icons.support_agent_outlined,
                      title: 'Atendimento preparado',
                      description:
                          'Fale com a empresa ou com o suporte VanRides para revisar o vínculo da conta.',
                    ),
                    const SizedBox(height: 14),
                    const _SupportStep(
                      icon: Icons.lock_outline_rounded,
                      title: 'Acesso protegido',
                      description:
                          'Enquanto a revisão acontece, nenhuma rotina operacional é aberta fora do perfil correto.',
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _SupportStep extends StatelessWidget {
  const _SupportStep({
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color: VanRidesDesignTokens.surfaceRaised,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: VanRidesDesignTokens.border),
          ),
          child: Icon(icon, color: VanRidesDesignTokens.blueTracking, size: 21),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: VanRidesDesignTokens.textPrimary,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                description,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: VanRidesDesignTokens.textSecondary,
                  height: 1.35,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
