import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../theme/vanrides_design_tokens.dart';
import '../widgets/vanrides_liquid_surface.dart';
import '../widgets/vanrides_status_pill.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';
import '../../features/admin/presentation/admin_login_screen.dart';
import 'app_experience_resolver.dart';

class ExperienceLaunchScreen extends StatelessWidget {
  const ExperienceLaunchScreen({super.key, this.isWebOverride});

  final bool? isWebOverride;

  @override
  Widget build(BuildContext context) {
    final isWeb = isWebOverride ?? kIsWeb;
    final experience = AppExperienceResolver.initialExperience(isWeb: isWeb);

    switch (experience) {
      case AppLaunchExperience.operationalLogin:
        return const LoginScreen();
      case AppLaunchExperience.experienceSelection:
        return const _ExperienceSelectionScreen();
    }
  }
}

class _ExperienceSelectionScreen extends StatelessWidget {
  const _ExperienceSelectionScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: VanRidesDesignTokens.backgroundDeep,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 1100),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const VanRidesStatusPill(
                    label: 'Operacao conectada',
                    icon: Icons.route_outlined,
                    tone: VanRidesStatusTone.tracking,
                  ),
                  const SizedBox(height: 20),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 720),
                    child: Text(
                      'VanRides',
                      style: Theme.of(
                        context,
                      ).textTheme.displayMedium?.copyWith(
                        color: VanRidesDesignTokens.textPrimary,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 0,
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 680),
                    child: Text(
                      'Entre no fluxo certo para acompanhar vans, presencas e operacao do dia.',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: VanRidesDesignTokens.textSecondary,
                        height: 1.35,
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                  LayoutBuilder(
                    builder: (context, constraints) {
                      final vertical = constraints.maxWidth < 780;
                      if (vertical) {
                        return Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            _ExperienceCard(
                              title: 'Gestao',
                              subtitle:
                                  'Motoristas, passageiros, responsaveis e viagens.',
                              icon: Icons.dashboard_customize_outlined,
                              actionLabel: 'Abrir gestao',
                              onPressed: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (_) => const AdminLoginScreen(),
                                  ),
                                );
                              },
                            ),
                            const SizedBox(height: 20),
                            _ExperienceCard(
                              title: 'Operacao',
                              subtitle:
                                  'Mapa, presenca, rota e conta no uso diario.',
                              icon: Icons.phone_iphone_outlined,
                              actionLabel: 'Entrar no app',
                              onPressed: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (_) => const LoginScreen(),
                                  ),
                                );
                              },
                            ),
                          ],
                        );
                      }

                      return Row(
                        children: [
                          Expanded(
                            child: _ExperienceCard(
                              title: 'Gestao',
                              subtitle:
                                  'Motoristas, passageiros, responsaveis e viagens.',
                              icon: Icons.dashboard_customize_outlined,
                              actionLabel: 'Abrir gestao',
                              onPressed: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (_) => const AdminLoginScreen(),
                                  ),
                                );
                              },
                            ),
                          ),
                          const SizedBox(width: 20),
                          Expanded(
                            child: _ExperienceCard(
                              title: 'Operacao',
                              subtitle:
                                  'Mapa, presenca, rota e conta no uso diario.',
                              icon: Icons.phone_iphone_outlined,
                              actionLabel: 'Entrar no app',
                              onPressed: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (_) => const LoginScreen(),
                                  ),
                                );
                              },
                            ),
                          ),
                        ],
                      );
                    },
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ExperienceCard extends StatelessWidget {
  const _ExperienceCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.actionLabel,
    required this.onPressed,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final String actionLabel;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.card(
      semanticLabel: title,
      borderRadius: 30,
      padding: const EdgeInsets.all(22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 54,
            height: 54,
            decoration: BoxDecoration(
              color: VanRidesDesignTokens.blueTracking.withValues(alpha: 0.16),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: VanRidesDesignTokens.blueTracking.withValues(
                  alpha: 0.42,
                ),
              ),
            ),
            child: Icon(
              icon,
              size: 28,
              color: VanRidesDesignTokens.blueTracking,
            ),
          ),
          const SizedBox(height: 20),
          Text(
            title,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: VanRidesDesignTokens.textPrimary,
              fontWeight: FontWeight.w900,
              letterSpacing: 0,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            subtitle,
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
              color: VanRidesDesignTokens.textSecondary,
              height: 1.35,
            ),
          ),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: onPressed,
            icon: const Icon(Icons.arrow_forward_rounded),
            label: Text(actionLabel),
          ),
        ],
      ),
    );
  }
}
