import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';

class DriverNavigationPanel extends StatelessWidget {
  const DriverNavigationPanel({
    required this.viewModel,
    required this.onPrimaryAction,
    required this.onFallback,
    required this.onRepeatInstruction,
    this.onSkipStop,
    this.onNoShowStop,
    this.onRetryQueuedActions,
    this.onToggleVoiceMute,
    this.voiceMuted = false,
    this.showDistanceLabel = true,
    this.showNextStopDetails = true,
    super.key,
  });

  static const rootKey = ValueKey('driver-navigation-panel');
  static const primaryActionKey = ValueKey('driver-navigation-primary-action');
  static const fallbackActionKey = ValueKey('driver-navigation-fallback');
  static const skipStopActionKey = ValueKey('driver-navigation-skip-stop');
  static const noShowActionKey = ValueKey('driver-navigation-no-show');
  static const repeatInstructionKey = ValueKey(
    'driver-navigation-repeat-instruction',
  );
  static const voiceMuteKey = ValueKey('driver-navigation-voice-mute');
  static const actionQueueStatusKey = ValueKey(
    'driver-navigation-action-queue-status',
  );
  static const actionQueueRetryKey = ValueKey(
    'driver-navigation-action-queue-retry',
  );

  final DriverNavigationViewModel viewModel;
  final VoidCallback? onPrimaryAction;
  final VoidCallback? onFallback;
  final VoidCallback? onRepeatInstruction;
  final VoidCallback? onSkipStop;
  final VoidCallback? onNoShowStop;
  final VoidCallback? onRetryQueuedActions;
  final VoidCallback? onToggleVoiceMute;
  final bool voiceMuted;
  final bool showDistanceLabel;
  final bool showNextStopDetails;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Semantics(
      label: 'Navegacao do motorista',
      container: true,
      explicitChildNodes: true,
      child: DecoratedBox(
        key: rootKey,
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: colors.outlineVariant.withValues(alpha: .72),
          ),
          boxShadow: [
            BoxShadow(
              color: colors.shadow.withValues(alpha: .08),
              blurRadius: 18,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _NavigationStatusRow(viewModel: viewModel),
              const SizedBox(height: 14),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (showDistanceLabel) ...[
                          Text(
                            viewModel.distanceLabel,
                            style: theme.textTheme.labelLarge?.copyWith(
                              color: colors.primary,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          const SizedBox(height: 6),
                        ],
                        Text(
                          viewModel.instructionText,
                          style: theme.textTheme.headlineSmall?.copyWith(
                            color: colors.onSurface,
                            fontWeight: FontWeight.w900,
                            height: 1.08,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 10),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      IconButton.filledTonal(
                        key: repeatInstructionKey,
                        tooltip: 'Repetir instrucao',
                        onPressed: onRepeatInstruction,
                        icon: const Icon(Icons.record_voice_over_outlined),
                      ),
                      if (onToggleVoiceMute != null)
                        IconButton.filledTonal(
                          key: voiceMuteKey,
                          tooltip: voiceMuted ? 'Ativar voz' : 'Silenciar voz',
                          onPressed: onToggleVoiceMute,
                          icon: Icon(
                            voiceMuted
                                ? Icons.volume_off_outlined
                                : Icons.volume_up_outlined,
                          ),
                        ),
                    ],
                  ),
                ],
              ),
              if (showNextStopDetails) ...[
                const SizedBox(height: 14),
                _NextStopBlock(viewModel: viewModel),
              ],
              const SizedBox(height: 14),
              Text(
                viewModel.helperText,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: colors.onSurfaceVariant,
                  height: 1.25,
                ),
              ),
              if (viewModel.queuedActionSummaryLabel != null) ...[
                const SizedBox(height: 12),
                _ActionQueueStatus(
                  viewModel: viewModel,
                  onRetryQueuedActions: onRetryQueuedActions,
                ),
              ],
              const SizedBox(height: 14),
              _NavigationActions(
                viewModel: viewModel,
                onPrimaryAction: onPrimaryAction,
                onFallback: onFallback,
                onSkipStop: onSkipStop,
                onNoShowStop: onNoShowStop,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ActionQueueStatus extends StatelessWidget {
  const _ActionQueueStatus({
    required this.viewModel,
    required this.onRetryQueuedActions,
  });

  final DriverNavigationViewModel viewModel;
  final VoidCallback? onRetryQueuedActions;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final detailLabel = viewModel.queuedActionDetailLabel;
    final helperText = viewModel.queuedActionHelperText;

    return Semantics(
      key: DriverNavigationPanel.actionQueueStatusKey,
      label: 'Fila local de acoes',
      liveRegion: true,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.sync_problem_outlined, size: 20, color: colors.tertiary),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  viewModel.queuedActionSummaryLabel!,
                  style: theme.textTheme.labelLarge?.copyWith(
                    color: colors.onSurface,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                if (detailLabel != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    detailLabel,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: colors.onSurface,
                      fontWeight: FontWeight.w700,
                      height: 1.2,
                    ),
                  ),
                ],
                if (helperText != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    helperText,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: colors.onSurfaceVariant,
                      height: 1.25,
                    ),
                  ),
                ],
                if (onRetryQueuedActions != null) ...[
                  const SizedBox(height: 6),
                  TextButton.icon(
                    key: DriverNavigationPanel.actionQueueRetryKey,
                    onPressed: onRetryQueuedActions,
                    icon: const Icon(Icons.sync_outlined),
                    label: const Text('Enviar agora'),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _NavigationStatusRow extends StatelessWidget {
  const _NavigationStatusRow({required this.viewModel});

  final DriverNavigationViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final isFallback = viewModel.fallbackIsPrimary;

    return Wrap(
      spacing: 8,
      runSpacing: 8,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        DecoratedBox(
          decoration: BoxDecoration(
            color:
                isFallback ? colors.tertiaryContainer : colors.primaryContainer,
            borderRadius: BorderRadius.circular(999),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            child: Text(
              viewModel.statusLabel,
              style: theme.textTheme.labelMedium?.copyWith(
                color:
                    isFallback
                        ? colors.onTertiaryContainer
                        : colors.onPrimaryContainer,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ),
        Text(
          viewModel.canUseInternalNavigation
              ? 'Navegacao VanRides'
              : 'Fallback recomendado',
          style: theme.textTheme.labelMedium?.copyWith(
            color: colors.onSurfaceVariant,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}

class _NextStopBlock extends StatelessWidget {
  const _NextStopBlock({required this.viewModel});

  final DriverNavigationViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surfaceContainerHighest.withValues(alpha: .54),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              viewModel.nextStopLabel,
              style: theme.textTheme.labelMedium?.copyWith(
                color: colors.onSurfaceVariant,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              viewModel.nextStopName,
              style: theme.textTheme.titleMedium?.copyWith(
                color: colors.onSurface,
                fontWeight: FontWeight.w900,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              viewModel.nextStopSummary,
              style: theme.textTheme.bodySmall?.copyWith(
                color: colors.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _NavigationActions extends StatelessWidget {
  const _NavigationActions({
    required this.viewModel,
    required this.onPrimaryAction,
    required this.onFallback,
    required this.onSkipStop,
    required this.onNoShowStop,
  });

  final DriverNavigationViewModel viewModel;
  final VoidCallback? onPrimaryAction;
  final VoidCallback? onFallback;
  final VoidCallback? onSkipStop;
  final VoidCallback? onNoShowStop;

  @override
  Widget build(BuildContext context) {
    final primary = FilledButton.icon(
      key: DriverNavigationPanel.primaryActionKey,
      onPressed: onPrimaryAction,
      icon: const Icon(Icons.check_circle_outline),
      label: Text(viewModel.primaryActionLabel),
    );
    final fallback = OutlinedButton.icon(
      key: DriverNavigationPanel.fallbackActionKey,
      onPressed: onFallback,
      icon: const Icon(Icons.open_in_new),
      label: Text(viewModel.fallbackLabel),
    );
    final secondaryActions = viewModel.secondaryActions
        .map(_buttonFor)
        .whereType<Widget>()
        .toList(growable: false);

    return Wrap(
      spacing: 10,
      runSpacing: 10,
      children:
          viewModel.fallbackIsPrimary
              ? [fallback, primary, ...secondaryActions]
              : [primary, ...secondaryActions, fallback],
    );
  }

  Widget? _buttonFor(DriverNavigationSecondaryAction action) {
    return switch (action.type) {
      DriverNavigationSecondaryActionType.skipStop when onSkipStop != null =>
        OutlinedButton.icon(
          key: DriverNavigationPanel.skipStopActionKey,
          onPressed: onSkipStop,
          icon: const Icon(Icons.skip_next_outlined),
          label: Text(action.label),
        ),
      DriverNavigationSecondaryActionType.noShow when onNoShowStop != null =>
        OutlinedButton.icon(
          key: DriverNavigationPanel.noShowActionKey,
          onPressed: onNoShowStop,
          icon: const Icon(Icons.person_off_outlined),
          label: Text(action.label),
        ),
      _ => null,
    };
  }
}
