import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../../l10n/app_strings.dart';
import 'vanrides_map_tokens.dart';

export 'vanrides_map_placeholder_surface.dart';
export 'vanrides_map_tokens.dart';

class VanRidesMapShell extends StatelessWidget {
  const VanRidesMapShell({
    super.key,
    required this.map,
    this.topStatus,
    this.bottomSheet,
    this.controls,
    this.floatingAction,
    this.horizontalPadding = 16,
    this.topPadding = 14,
    this.sheetBottomInset = 12,
    this.sheetMaxHeightFactor = 0.46,
    this.showSheetHandle = true,
  });

  final Widget map;
  final Widget? topStatus;
  final Widget? bottomSheet;
  final Widget? controls;
  final Widget? floatingAction;
  final double horizontalPadding;
  final double topPadding;
  final double sheetBottomInset;
  final double sheetMaxHeightFactor;
  final bool showSheetHandle;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final viewportHeight =
            constraints.hasBoundedHeight
                ? constraints.maxHeight
                : MediaQuery.sizeOf(context).height;
        final maxSheetHeight = math.max(
          180.0,
          viewportHeight * sheetMaxHeightFactor,
        );

        return ColoredBox(
          key: const Key('vanrides.map.shell'),
          color: VanRidesMapTokens.backgroundDeep,
          child: Stack(
            fit: StackFit.expand,
            children: [
              Positioned.fill(child: map),
              const Positioned.fill(child: _VanRidesMapAtmosphere()),
              if (topStatus != null)
                SafeArea(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(
                      horizontalPadding,
                      topPadding,
                      horizontalPadding,
                      0,
                    ),
                    child: Align(
                      alignment: Alignment.topCenter,
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 760),
                        child: topStatus!,
                      ),
                    ),
                  ),
                ),
              if (controls != null)
                Positioned(
                  right: horizontalPadding,
                  bottom:
                      bottomSheet == null
                          ? 18
                          : math.min(
                            maxSheetHeight + 28,
                            viewportHeight * 0.56,
                          ),
                  child: controls!,
                ),
              if (floatingAction != null)
                Positioned(
                  left: horizontalPadding,
                  right: horizontalPadding,
                  bottom: math.min(maxSheetHeight + 30, viewportHeight * 0.58),
                  child: Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 520),
                      child: floatingAction!,
                    ),
                  ),
                ),
              if (bottomSheet != null)
                SafeArea(
                  child: Align(
                    alignment: Alignment.bottomCenter,
                    child: Padding(
                      padding: EdgeInsets.fromLTRB(
                        horizontalPadding,
                        0,
                        horizontalPadding,
                        sheetBottomInset,
                      ),
                      child: ConstrainedBox(
                        constraints: BoxConstraints(
                          maxWidth: 760,
                          maxHeight: maxSheetHeight,
                        ),
                        child: _VanRidesTripBottomSheet(
                          showHandle: showSheetHandle,
                          child: bottomSheet!,
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
}

class VanRidesMapTopStatus extends StatelessWidget {
  const VanRidesMapTopStatus({
    super.key,
    required this.icon,
    required this.label,
    this.value,
    this.trailing,
    this.accentColor = VanRidesMapTokens.blueTracking,
  });

  final IconData icon;
  final String label;
  final String? value;
  final Widget? trailing;
  final Color accentColor;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final semanticLabel =
        value == null || value!.trim().isEmpty ? label : '$label. $value';

    return Semantics(
      container: true,
      label: semanticLabel,
      child: DecoratedBox(
        key: const Key('vanrides.map.top-status'),
        decoration: BoxDecoration(
          color: VanRidesMapTokens.surface.withValues(alpha: 0.94),
          borderRadius: BorderRadius.circular(22),
          border: Border.all(
            color: VanRidesMapTokens.border.withValues(alpha: 0.56),
          ),
          boxShadow: const [
            BoxShadow(
              color: Color(0x66070C12),
              blurRadius: 22,
              offset: Offset(0, 12),
            ),
          ],
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 10, 14, 10),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: accentColor.withValues(alpha: 0.16),
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: accentColor.withValues(alpha: 0.44),
                  ),
                ),
                child: Icon(icon, color: accentColor, size: 20),
              ),
              const SizedBox(width: 10),
              Flexible(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: textTheme.labelMedium?.copyWith(
                        color: VanRidesMapTokens.textSecondary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    if (value != null) ...[
                      const SizedBox(height: 2),
                      Text(
                        value!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: textTheme.bodyMedium?.copyWith(
                          color: VanRidesMapTokens.textPrimary,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              if (trailing != null) ...[
                const SizedBox(width: 10),
                Flexible(flex: 0, child: trailing!),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class VanRidesMapControlCluster extends StatelessWidget {
  const VanRidesMapControlCluster({
    super.key,
    required this.cameraLockedToContent,
    required this.onRecenter,
    required this.onZoomIn,
    required this.onZoomOut,
  });

  final bool cameraLockedToContent;
  final VoidCallback onRecenter;
  final VoidCallback onZoomIn;
  final VoidCallback onZoomOut;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      key: const Key('vanrides.map.controls'),
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surface.withValues(alpha: 0.94),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.5),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x66070C12),
            blurRadius: 18,
            offset: Offset(0, 10),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(6),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _VanRidesMapIconButton(
              tooltip: AppStrings.passageiro.aproximarMapa,
              icon: Icons.add,
              onTap: onZoomIn,
            ),
            const SizedBox(height: 4),
            _VanRidesMapIconButton(
              tooltip: AppStrings.passageiro.afastarMapa,
              icon: Icons.remove,
              onTap: onZoomOut,
            ),
            const SizedBox(height: 4),
            _VanRidesMapIconButton(
              tooltip:
                  cameraLockedToContent
                      ? AppStrings.passageiro.mapaJaCentralizado
                      : AppStrings.passageiro.recentralizarRota,
              icon: cameraLockedToContent ? Icons.gps_fixed : Icons.my_location,
              accent:
                  cameraLockedToContent
                      ? VanRidesMapTokens.greenAction
                      : VanRidesMapTokens.blueTracking,
              onTap: onRecenter,
            ),
          ],
        ),
      ),
    );
  }
}

class _VanRidesTripBottomSheet extends StatelessWidget {
  const _VanRidesTripBottomSheet({
    required this.child,
    required this.showHandle,
  });

  final Widget child;
  final bool showHandle;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      key: const Key('vanrides.map.bottom-sheet'),
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surface.withValues(alpha: 0.97),
        borderRadius: BorderRadius.circular(30),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.42),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x80070C12),
            blurRadius: 30,
            offset: Offset(0, 16),
          ),
        ],
      ),
      child: DefaultTextStyle.merge(
        style: const TextStyle(color: VanRidesMapTokens.textPrimary),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(22, 14, 22, 22),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (showHandle) ...[
                Align(
                  child: Container(
                    key: const Key('vanrides.map.bottom-sheet.handle'),
                    width: 54,
                    height: 5,
                    decoration: BoxDecoration(
                      color: VanRidesMapTokens.border.withValues(alpha: 0.7),
                      borderRadius: BorderRadius.circular(99),
                    ),
                  ),
                ),
                const SizedBox(height: 14),
              ],
              child,
            ],
          ),
        ),
      ),
    );
  }
}

class _VanRidesMapAtmosphere extends StatelessWidget {
  const _VanRidesMapAtmosphere();

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              VanRidesMapTokens.backgroundDeep.withValues(alpha: 0.42),
              Colors.transparent,
              VanRidesMapTokens.backgroundDeep.withValues(alpha: 0.62),
            ],
            stops: const [0, 0.44, 1],
          ),
        ),
      ),
    );
  }
}

class _VanRidesMapIconButton extends StatelessWidget {
  const _VanRidesMapIconButton({
    required this.tooltip,
    required this.icon,
    required this.onTap,
    this.accent = VanRidesMapTokens.textPrimary,
  });

  final String tooltip;
  final IconData icon;
  final VoidCallback onTap;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkResponse(
        radius: 24,
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Tooltip(
          message: tooltip,
          child: SizedBox(
            width: 44,
            height: 44,
            child: Icon(icon, color: accent, size: 22),
          ),
        ),
      ),
    );
  }
}
