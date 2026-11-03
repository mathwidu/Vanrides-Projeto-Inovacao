import 'package:flutter/material.dart';

import '../../../app/theme/vanrides_design_tokens.dart';
import '../../../app/widgets/vanrides_liquid_surface.dart';
import '../../../features/driver_onboarding/domain/driver_onboarding_models.dart';

const driverCompanyPendingSearchFieldKey = Key('driver.companyPending.search');
const driverCompanyPendingOpenInviteButtonKey = Key(
  'driver.companyPending.openInvite',
);
const driverCompanyPendingRequestButtonPrefix =
    'driver.companyPending.request.';

class DriverPendingAccountEmailCard extends StatelessWidget {
  const DriverPendingAccountEmailCard({super.key, required this.email});

  final String email;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.action(
      semanticLabel: 'Email do motorista',
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          const Icon(
            Icons.alternate_email_rounded,
            color: VanRidesDesignTokens.blueTracking,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Conta de motorista',
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: VanRidesDesignTokens.textMuted,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  email,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: VanRidesDesignTokens.textPrimary,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class DriverPendingInviteAction extends StatelessWidget {
  const DriverPendingInviteAction({super.key, required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: FilledButton.icon(
        key: driverCompanyPendingOpenInviteButtonKey,
        onPressed: onPressed,
        icon: const Icon(Icons.link_rounded),
        label: const Text('Aceitar convite da empresa'),
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(54),
          backgroundColor: VanRidesDesignTokens.greenAction,
          foregroundColor: VanRidesDesignTokens.onAccent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
        ),
      ),
    );
  }
}

class DriverPendingRequestCard extends StatelessWidget {
  const DriverPendingRequestCard({super.key, required this.status});

  final DriverOnboardingStatusRecord? status;

  @override
  Widget build(BuildContext context) {
    final request = status?.pendingRequest;
    return VanRidesLiquidSurface.action(
      semanticLabel: 'Solicitacao pendente',
      padding: const EdgeInsets.all(18),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.hourglass_top_rounded,
            color: VanRidesDesignTokens.amberWarning,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Solicitação manual',
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: VanRidesDesignTokens.amberWarning,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  request?.companyName ?? status?.companyName ?? 'Empresa',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: VanRidesDesignTokens.textPrimary,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  'A empresa precisa aprovar este pedido no portal. Você não precisa pedir outro convite para este mesmo vínculo.',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: VanRidesDesignTokens.textSecondary,
                    height: 1.4,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class DriverLinkedSessionAction extends StatelessWidget {
  const DriverLinkedSessionAction({
    super.key,
    required this.loading,
    required this.onPressed,
  });

  final bool loading;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.action(
      semanticLabel: 'Empresa aprovada',
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(
                Icons.check_circle_rounded,
                color: VanRidesDesignTokens.greenAction,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  'Empresa aprovada',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: VanRidesDesignTokens.textPrimary,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'Atualize seu acesso para carregar a operação da empresa nesta sessão.',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: VanRidesDesignTokens.textSecondary,
              height: 1.4,
            ),
          ),
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: loading ? null : onPressed,
              icon:
                  loading
                      ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                      : const Icon(Icons.sync_rounded),
              label: const Text('Atualizar acesso'),
              style: FilledButton.styleFrom(
                backgroundColor: VanRidesDesignTokens.greenAction,
                foregroundColor: VanRidesDesignTokens.onAccent,
                minimumSize: const Size.fromHeight(52),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(18),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class DriverCompanySearch extends StatelessWidget {
  const DriverCompanySearch({
    super.key,
    required this.controller,
    required this.loading,
    required this.submitting,
    required this.companies,
    required this.onSearch,
    required this.onRequest,
  });

  final TextEditingController controller;
  final bool loading;
  final bool submitting;
  final List<DriverOnboardingCompanyRecord> companies;
  final VoidCallback onSearch;
  final ValueChanged<DriverOnboardingCompanyRecord> onRequest;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Buscar empresa',
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: VanRidesDesignTokens.textPrimary,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          key: driverCompanyPendingSearchFieldKey,
          controller: controller,
          onSubmitted: (_) => onSearch(),
          cursorColor: VanRidesDesignTokens.blueTracking,
          style: const TextStyle(color: VanRidesDesignTokens.textPrimary),
          decoration: InputDecoration(
            hintText: 'Nome da empresa',
            hintStyle: const TextStyle(color: VanRidesDesignTokens.textMuted),
            prefixIcon: const Icon(
              Icons.search_rounded,
              color: VanRidesDesignTokens.textSecondary,
            ),
            suffixIcon: IconButton(
              onPressed: loading ? null : onSearch,
              icon: const Icon(Icons.arrow_forward_rounded),
            ),
            filled: true,
            fillColor: VanRidesDesignTokens.surfaceRaised,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(18),
              borderSide: BorderSide.none,
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(18),
              borderSide: BorderSide(
                color: VanRidesDesignTokens.border.withValues(alpha: 0.38),
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        if (loading)
          const Center(child: CircularProgressIndicator())
        else if (companies.isEmpty)
          Text(
            'Nenhuma empresa encontrada com esse termo.',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: VanRidesDesignTokens.textMuted,
            ),
          )
        else
          ...companies.map(
            (company) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: VanRidesLiquidSurface.action(
                semanticLabel: 'Empresa ${company.nome}',
                padding: const EdgeInsets.all(14),
                child: Row(
                  children: [
                    const Icon(
                      Icons.apartment_rounded,
                      color: VanRidesDesignTokens.blueTracking,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            company.nome,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(
                              context,
                            ).textTheme.titleSmall?.copyWith(
                              color: VanRidesDesignTokens.textPrimary,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                          Text(
                            company.slug,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(
                              context,
                            ).textTheme.bodySmall?.copyWith(
                              color: VanRidesDesignTokens.textMuted,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 10),
                    FilledButton(
                      key: Key(
                        '$driverCompanyPendingRequestButtonPrefix${company.id}',
                      ),
                      onPressed: submitting ? null : () => onRequest(company),
                      child: const Text('Solicitar'),
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}
