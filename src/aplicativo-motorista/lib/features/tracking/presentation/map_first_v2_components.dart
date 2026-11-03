import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_design_tokens.dart';

class MapFirstPalette {
  static const background = VanRidesDesignTokens.backgroundDeep;
  static const surface = VanRidesDesignTokens.surface;
  static const surfaceRaised = VanRidesDesignTokens.surfaceRaised;
  static const border = VanRidesDesignTokens.border;
  static const textStrong = VanRidesDesignTokens.textPrimary;
  static const textMuted = VanRidesDesignTokens.textMuted;
  static const iconMuted = VanRidesDesignTokens.textSecondary;
  static const brandBlue = VanRidesDesignTokens.blueTracking;
  static const blue = VanRidesDesignTokens.blueTracking;
  static const blueSoft = Color(0x26358AC0);
  static const green = VanRidesDesignTokens.greenAction;
  static const greenSoft = Color(0x2636B36E);
  static const amber = VanRidesDesignTokens.amberWarning;
  static const amberSoft = Color(0x26E7AF43);
  static const red = VanRidesDesignTokens.redDanger;
  static const redSoft = Color(0x26E25B55);
  static const purple = Color(0xFF9BA8FF);
}

class MapFirstGlassChip extends StatelessWidget {
  const MapFirstGlassChip({
    super.key,
    required this.icon,
    required this.label,
    this.color = MapFirstPalette.textStrong,
    this.compact = false,
  });

  final IconData icon;
  final String label;
  final Color color;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: compact ? 12 : 14,
        vertical: compact ? 9 : 11,
      ),
      decoration: BoxDecoration(
        color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.88),
        borderRadius: BorderRadius.circular(22),
        boxShadow: const [
          BoxShadow(
            color: Color(0x14161B36),
            blurRadius: 24,
            offset: Offset(0, 12),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: compact ? 18 : 20, color: color),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                color: color,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class MapFirstIconButton extends StatelessWidget {
  const MapFirstIconButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.tooltip,
    this.filled = false,
    this.color,
  });

  final IconData icon;
  final VoidCallback? onPressed;
  final String? tooltip;
  final bool filled;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final foregroundColor =
        color ??
        (filled ? VanRidesDesignTokens.onAccent : MapFirstPalette.textStrong);
    final backgroundColor =
        filled
            ? (color ?? MapFirstPalette.blue)
            : MapFirstPalette.surfaceRaised.withValues(alpha: 0.92);

    return Tooltip(
      message: tooltip ?? '',
      child: Material(
        color: backgroundColor,
        shape: const CircleBorder(),
        elevation: 8,
        shadowColor: const Color(0x22161B36),
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: onPressed,
          child: SizedBox(
            width: 44,
            height: 44,
            child: Icon(icon, color: foregroundColor, size: 22),
          ),
        ),
      ),
    );
  }
}

class MapFirstPrimaryButton extends StatelessWidget {
  const MapFirstPrimaryButton({
    super.key,
    required this.label,
    required this.icon,
    required this.onPressed,
    this.backgroundColor = MapFirstPalette.green,
    this.foregroundColor = VanRidesDesignTokens.onAccent,
    this.buttonKey,
  });

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;
  final Color backgroundColor;
  final Color foregroundColor;
  final Key? buttonKey;

  @override
  Widget build(BuildContext context) {
    return FilledButton.icon(
      key: buttonKey,
      onPressed: onPressed,
      style: FilledButton.styleFrom(
        backgroundColor: backgroundColor,
        foregroundColor: foregroundColor,
        minimumSize: const Size.fromHeight(58),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
        textStyle: Theme.of(
          context,
        ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
      ),
      icon: Icon(icon, size: 22),
      label: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
    );
  }
}

class MapFirstSecondaryButton extends StatelessWidget {
  const MapFirstSecondaryButton({
    super.key,
    required this.label,
    required this.icon,
    required this.onPressed,
    this.color = MapFirstPalette.textStrong,
    this.buttonKey,
  });

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;
  final Color color;
  final Key? buttonKey;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      key: buttonKey,
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        foregroundColor: color,
        side: BorderSide(color: color.withValues(alpha: 0.18)),
        backgroundColor: MapFirstPalette.surfaceRaised.withValues(alpha: 0.94),
        minimumSize: const Size.fromHeight(48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        textStyle: Theme.of(
          context,
        ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
      ),
      icon: Icon(icon, size: 18),
      label: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
    );
  }
}

class MapFirstCompactActionButton extends StatelessWidget {
  const MapFirstCompactActionButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.color = MapFirstPalette.red,
    this.backgroundColor = MapFirstPalette.surfaceRaised,
    this.buttonKey,
    this.tooltip,
  });

  final IconData icon;
  final VoidCallback? onPressed;
  final Color color;
  final Color backgroundColor;
  final Key? buttonKey;
  final String? tooltip;

  @override
  Widget build(BuildContext context) {
    final child = OutlinedButton(
      key: buttonKey,
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        foregroundColor: color,
        side: BorderSide(color: color.withValues(alpha: 0.2)),
        backgroundColor: backgroundColor.withValues(alpha: 0.96),
        minimumSize: const Size(54, 48),
        padding: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      ),
      child: Icon(icon, size: 20),
    );

    if (tooltip == null || tooltip!.isEmpty) {
      return child;
    }

    return Tooltip(message: tooltip!, child: child);
  }
}

class MapFirstAvatar extends StatelessWidget {
  const MapFirstAvatar({
    super.key,
    required this.label,
    this.size = 56,
    this.backgroundColor = MapFirstPalette.blue,
    this.foregroundColor = VanRidesDesignTokens.onAccent,
  });

  final String label;
  final double size;
  final Color backgroundColor;
  final Color foregroundColor;

  @override
  Widget build(BuildContext context) {
    final seed =
        label.trim().isEmpty ? '?' : label.trim().substring(0, 1).toUpperCase();
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [backgroundColor.withValues(alpha: 0.88), backgroundColor],
        ),
        shape: BoxShape.circle,
        boxShadow: const [
          BoxShadow(
            color: Color(0x22161B36),
            blurRadius: 18,
            offset: Offset(0, 10),
          ),
        ],
      ),
      alignment: Alignment.center,
      child: Text(
        seed,
        style: Theme.of(context).textTheme.titleLarge?.copyWith(
          color: foregroundColor,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class MapFirstSurfaceCard extends StatelessWidget {
  const MapFirstSurfaceCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(18),
    this.color = MapFirstPalette.surface,
  });

  final Widget child;
  final EdgeInsets padding;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: padding,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: MapFirstPalette.border),
        boxShadow: const [
          BoxShadow(
            color: Color(0x66070C12),
            blurRadius: 24,
            offset: Offset(0, 12),
          ),
        ],
      ),
      child: child,
    );
  }
}

class MapFirstStateCard extends StatelessWidget {
  const MapFirstStateCard({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
    this.color = MapFirstPalette.blue,
  });

  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return MapFirstSurfaceCard(
      padding: const EdgeInsets.fromLTRB(22, 24, 22, 22),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 74,
            height: 74,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: color, size: 34),
          ),
          const SizedBox(height: 18),
          Text(
            title,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            message,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.45,
            ),
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 20),
            MapFirstSecondaryButton(
              label: actionLabel!,
              icon: Icons.refresh_rounded,
              onPressed: onAction,
            ),
          ],
        ],
      ),
    );
  }
}

class MapFirstStopCard extends StatelessWidget {
  const MapFirstStopCard({
    super.key,
    required this.indexLabel,
    required this.title,
    required this.subtitle,
    required this.passengerLabel,
    required this.statusLabel,
    this.etaLabel,
    this.highlighted = false,
    this.dimmed = false,
    this.onTap,
  });

  final String indexLabel;
  final String title;
  final String subtitle;
  final String passengerLabel;
  final String statusLabel;
  final String? etaLabel;
  final bool highlighted;
  final bool dimmed;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final background =
        highlighted ? MapFirstPalette.greenSoft : MapFirstPalette.surface;
    final borderColor =
        highlighted
            ? MapFirstPalette.green.withValues(alpha: 0.24)
            : MapFirstPalette.border;
    final opacity = dimmed ? 0.6 : 1.0;

    return Opacity(
      opacity: opacity,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(24),
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: background,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: borderColor),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 34,
                  height: 34,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color:
                        highlighted
                            ? MapFirstPalette.green
                            : MapFirstPalette.background,
                    shape: BoxShape.circle,
                  ),
                  child: Text(
                    indexLabel,
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color:
                          highlighted
                              ? VanRidesDesignTokens.onAccent
                              : MapFirstPalette.textStrong,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: Theme.of(
                          context,
                        ).textTheme.titleMedium?.copyWith(
                          color: MapFirstPalette.textStrong,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        subtitle,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: MapFirstPalette.textMuted,
                        ),
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              passengerLabel,
                              style: Theme.of(
                                context,
                              ).textTheme.bodyMedium?.copyWith(
                                color: MapFirstPalette.textStrong,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                          if (etaLabel != null)
                            Text(
                              etaLabel!,
                              style: Theme.of(
                                context,
                              ).textTheme.bodyMedium?.copyWith(
                                color: MapFirstPalette.blue,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: _statusColor(statusLabel).withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Text(
                    statusLabel,
                    style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      color: _statusColor(statusLabel),
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Color _statusColor(String label) {
    switch (label.toLowerCase()) {
      case 'proxima':
      case 'próxima':
      case 'em andamento':
        return MapFirstPalette.green;
      case 'concluída':
      case 'concluida':
        return MapFirstPalette.textMuted;
      case 'pulada':
      case 'não embarcou':
      case 'nao embarcou':
        return MapFirstPalette.red;
      default:
        return MapFirstPalette.blue;
    }
  }
}
