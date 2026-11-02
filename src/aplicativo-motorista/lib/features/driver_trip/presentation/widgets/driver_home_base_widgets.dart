import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class DriverHomeUnavailableTripState extends StatelessWidget {
  const DriverHomeUnavailableTripState({
    super.key,
    required this.isLoading,
    required this.errorMessage,
    required this.refreshKey,
    required this.onRefresh,
  });

  final bool isLoading;
  final String? errorMessage;
  final Key refreshKey;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 48),
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    AppStrings.motorista.appName,
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      color: MapFirstPalette.textStrong,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                MapFirstIconButton(
                  key: refreshKey,
                  icon: Icons.refresh_rounded,
                  tooltip: AppStrings.motorista.atualizar,
                  onPressed: onRefresh,
                ),
              ],
            ),
            const SizedBox(height: 24),
            Text(
              AppStrings.motorista.corridaProntaParaDirigir,
              style: Theme.of(context).textTheme.displaySmall?.copyWith(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w800,
                height: 1.08,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              AppStrings.motorista.corridaProntaParaDirigirDescricao,
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                color: MapFirstPalette.textMuted,
                fontWeight: FontWeight.w400,
                height: 1.35,
              ),
            ),
            const SizedBox(height: 18),
            if (isLoading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 64),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (errorMessage != null)
              MapFirstStateCard(
                icon: Icons.warning_amber_outlined,
                title: AppStrings.motorista.erroAoCarregarOperacao,
                message: errorMessage!,
                actionLabel: AppStrings.motorista.tentarNovamente,
                onAction: onRefresh,
                color: MapFirstPalette.red,
              )
            else
              MapFirstStateCard(
                icon: Icons.alt_route_outlined,
                title: AppStrings.motorista.semViagemPublicadaHoje,
                message: AppStrings.motorista.semViagemPublicadaHojeDescricao,
                actionLabel: AppStrings.motorista.atualizar,
                onAction: onRefresh,
              ),
          ],
        ),
      ),
    );
  }
}

class DriverOperationTypeBadge extends StatelessWidget {
  const DriverOperationTypeBadge({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: MapFirstPalette.blueSoft,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: MapFirstPalette.blue,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class DriverHeaderMetric extends StatelessWidget {
  const DriverHeaderMetric({
    super.key,
    required this.label,
    required this.caption,
  });

  final String label;
  final String caption;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(
          label,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: MapFirstPalette.textStrong,
            fontWeight: FontWeight.w800,
          ),
        ),
        Text(
          caption,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: MapFirstPalette.textMuted,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }
}

class DriverRouteBadge extends StatelessWidget {
  const DriverRouteBadge({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.78),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: MapFirstPalette.border),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            Icons.route_rounded,
            size: 18,
            color: MapFirstPalette.brandBlue,
          ),
          const SizedBox(width: 8),
          Text(
            label,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class DriverUtilityAction {
  const DriverUtilityAction({
    required this.key,
    required this.icon,
    required this.label,
    required this.onPressed,
  });

  final Key key;
  final IconData icon;
  final String label;
  final VoidCallback? onPressed;
}

class DriverUtilityRow extends StatelessWidget {
  const DriverUtilityRow({super.key, required this.actions});

  final List<DriverUtilityAction> actions;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 420) {
          return Wrap(
            spacing: 10,
            runSpacing: 10,
            children: actions
                .map(
                  (action) => SizedBox(
                    width: (constraints.maxWidth - 10) / 2,
                    child: OutlinedButton.icon(
                      key: action.key,
                      onPressed: action.onPressed,
                      icon: Icon(action.icon, size: 18),
                      label: Text(action.label),
                      style: _driverUtilityStyle(),
                    ),
                  ),
                )
                .toList(growable: false),
          );
        }

        return Row(
          children: [
            for (var index = 0; index < actions.length; index++) ...[
              Expanded(
                child: OutlinedButton.icon(
                  key: actions[index].key,
                  onPressed: actions[index].onPressed,
                  icon: Icon(actions[index].icon, size: 18),
                  label: Text(actions[index].label),
                  style: _driverUtilityStyle(),
                ),
              ),
              if (index != actions.length - 1) const SizedBox(width: 10),
            ],
          ],
        );
      },
    );
  }

  ButtonStyle _driverUtilityStyle() {
    return OutlinedButton.styleFrom(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      minimumSize: const Size(0, 46),
      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
      foregroundColor: MapFirstPalette.textStrong,
      side: BorderSide(
        color: MapFirstPalette.border.withValues(alpha: 0.64),
        width: 1.2,
      ),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      backgroundColor: MapFirstPalette.surfaceRaised.withValues(alpha: 0.88),
    );
  }
}

class DriverAccountInfoRow extends StatelessWidget {
  const DriverAccountInfoRow({
    super.key,
    required this.icon,
    required this.title,
    required this.value,
  });

  final IconData icon;
  final String title;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 42,
          height: 42,
          decoration: const BoxDecoration(
            color: MapFirstPalette.greenSoft,
            shape: BoxShape.circle,
          ),
          alignment: Alignment.center,
          child: Icon(icon, color: MapFirstPalette.green, size: 20),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: MapFirstPalette.textMuted,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                value,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: MapFirstPalette.textStrong,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class DriverPrimaryActionButton extends StatelessWidget {
  const DriverPrimaryActionButton({
    super.key,
    required this.actionKey,
    required this.icon,
    required this.label,
    required this.onPressed,
  });

  final Key actionKey;
  final IconData icon;
  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF161B36).withValues(alpha: 0.18),
            blurRadius: 24,
            offset: const Offset(0, 14),
          ),
        ],
      ),
      child: FilledButton.icon(
        key: actionKey,
        onPressed: onPressed,
        icon: Icon(icon),
        label: Text(label),
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(60),
          backgroundColor: MapFirstPalette.blue,
          foregroundColor: const Color(0xFF07111A),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(22),
          ),
        ),
      ),
    );
  }
}

class DriverCompactStatusCard extends StatelessWidget {
  const DriverCompactStatusCard({
    super.key,
    required this.title,
    required this.subtitle,
  });

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: MapFirstPalette.surface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(26),
        border: Border.all(color: MapFirstPalette.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.35,
            ),
          ),
        ],
      ),
    );
  }
}
