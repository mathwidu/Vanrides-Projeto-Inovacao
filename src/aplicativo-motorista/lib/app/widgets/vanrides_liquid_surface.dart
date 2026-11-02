import 'dart:ui';

import 'package:flutter/material.dart';

import '../theme/vanrides_design_tokens.dart';

enum VanRidesLiquidSurfaceVariant { subtle, sheet, nav, card, dialog, action }

class VanRidesLiquidSurface extends StatelessWidget {
  const VanRidesLiquidSurface({
    super.key,
    required this.child,
    this.variant = VanRidesLiquidSurfaceVariant.subtle,
    this.semanticLabel,
    this.borderRadius,
    this.padding,
    this.forceSolidFallback = false,
  });

  const VanRidesLiquidSurface.sheet({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius,
    this.padding,
    this.forceSolidFallback = false,
  }) : variant = VanRidesLiquidSurfaceVariant.sheet;

  const VanRidesLiquidSurface.nav({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius,
    this.padding,
    this.forceSolidFallback = false,
  }) : variant = VanRidesLiquidSurfaceVariant.nav;

  const VanRidesLiquidSurface.card({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius,
    this.padding,
    this.forceSolidFallback = false,
  }) : variant = VanRidesLiquidSurfaceVariant.card;

  const VanRidesLiquidSurface.dialog({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius,
    this.padding,
    this.forceSolidFallback = false,
  }) : variant = VanRidesLiquidSurfaceVariant.dialog;

  const VanRidesLiquidSurface.action({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius,
    this.padding,
    this.forceSolidFallback = false,
  }) : variant = VanRidesLiquidSurfaceVariant.action;

  static const liquidKey = ValueKey('vanrides.liquid.surface');
  static const solidFallbackKey = ValueKey('vanrides.liquid.solid-fallback');

  final Widget child;
  final VanRidesLiquidSurfaceVariant variant;
  final String? semanticLabel;
  final double? borderRadius;
  final EdgeInsetsGeometry? padding;
  final bool forceSolidFallback;

  static bool shouldUseSolidFallback(MediaQueryData mediaQuery) {
    return mediaQuery.accessibleNavigation ||
        mediaQuery.disableAnimations ||
        mediaQuery.highContrast;
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final useSolidFallback =
        forceSolidFallback || shouldUseSolidFallback(mediaQuery);
    final spec = _VanRidesLiquidSurfaceSpec.forVariant(variant);
    final resolvedRadius = BorderRadius.circular(borderRadius ?? spec.radius);
    final resolvedPadding = padding ?? spec.padding;
    final content = Padding(padding: resolvedPadding, child: child);

    Widget surface = DecoratedBox(
      key: useSolidFallback ? solidFallbackKey : liquidKey,
      decoration: _decoration(spec, useSolidFallback),
      child:
          useSolidFallback
              ? content
              : BackdropFilter(
                filter: ImageFilter.blur(
                  sigmaX: spec.blurSigma,
                  sigmaY: spec.blurSigma,
                ),
                child: content,
              ),
    );

    surface = ClipRRect(borderRadius: resolvedRadius, child: surface);
    if (semanticLabel == null) {
      return surface;
    }
    return Semantics(label: semanticLabel, container: true, child: surface);
  }

  BoxDecoration _decoration(
    _VanRidesLiquidSurfaceSpec spec,
    bool useSolidFallback,
  ) {
    final baseColor =
        useSolidFallback
            ? VanRidesDesignTokens.surface
            : spec.color.withValues(alpha: spec.alpha);
    final borderAlpha = useSolidFallback ? 0.72 : spec.borderAlpha;

    return BoxDecoration(
      color: baseColor,
      borderRadius: BorderRadius.circular(borderRadius ?? spec.radius),
      border: Border.all(
        color: VanRidesDesignTokens.border.withValues(alpha: borderAlpha),
      ),
      gradient:
          useSolidFallback
              ? null
              : LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  VanRidesDesignTokens.textPrimary.withValues(alpha: 0.055),
                  spec.color.withValues(alpha: spec.alpha),
                ],
              ),
      boxShadow: [
        BoxShadow(
          color: Colors.black.withValues(alpha: useSolidFallback ? 0.34 : 0.26),
          blurRadius: useSolidFallback ? 18 : spec.shadowBlur,
          offset: Offset(0, spec.shadowOffset),
        ),
      ],
    );
  }
}

class _VanRidesLiquidSurfaceSpec {
  const _VanRidesLiquidSurfaceSpec({
    required this.color,
    required this.alpha,
    required this.blurSigma,
    required this.borderAlpha,
    required this.radius,
    required this.padding,
    required this.shadowBlur,
    required this.shadowOffset,
  });

  factory _VanRidesLiquidSurfaceSpec.forVariant(
    VanRidesLiquidSurfaceVariant variant,
  ) {
    return switch (variant) {
      VanRidesLiquidSurfaceVariant.sheet => const _VanRidesLiquidSurfaceSpec(
        color: VanRidesDesignTokens.surfaceGlass,
        alpha: 0.64,
        blurSigma: 24,
        borderAlpha: 0.34,
        radius: VanRidesDesignTokens.radiusSheet,
        padding: EdgeInsets.fromLTRB(18, 12, 18, 16),
        shadowBlur: 34,
        shadowOffset: 18,
      ),
      VanRidesLiquidSurfaceVariant.nav => const _VanRidesLiquidSurfaceSpec(
        color: VanRidesDesignTokens.surfaceGlass,
        alpha: 0.58,
        blurSigma: 26,
        borderAlpha: 0.30,
        radius: VanRidesDesignTokens.radiusNav,
        padding: EdgeInsets.all(8),
        shadowBlur: 28,
        shadowOffset: 16,
      ),
      VanRidesLiquidSurfaceVariant.card => const _VanRidesLiquidSurfaceSpec(
        color: VanRidesDesignTokens.surfaceRaised,
        alpha: 0.52,
        blurSigma: 18,
        borderAlpha: 0.34,
        radius: VanRidesDesignTokens.radiusCard,
        padding: EdgeInsets.all(18),
        shadowBlur: 22,
        shadowOffset: 12,
      ),
      VanRidesLiquidSurfaceVariant.dialog => const _VanRidesLiquidSurfaceSpec(
        color: VanRidesDesignTokens.surfaceGlass,
        alpha: 0.78,
        blurSigma: 22,
        borderAlpha: 0.42,
        radius: VanRidesDesignTokens.radiusDialog,
        padding: EdgeInsets.all(22),
        shadowBlur: 42,
        shadowOffset: 22,
      ),
      VanRidesLiquidSurfaceVariant.action => const _VanRidesLiquidSurfaceSpec(
        color: VanRidesDesignTokens.surfaceRaised,
        alpha: 0.48,
        blurSigma: 16,
        borderAlpha: 0.32,
        radius: VanRidesDesignTokens.radiusAction,
        padding: EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        shadowBlur: 18,
        shadowOffset: 10,
      ),
      VanRidesLiquidSurfaceVariant.subtle => const _VanRidesLiquidSurfaceSpec(
        color: VanRidesDesignTokens.surfaceRaised,
        alpha: 0.44,
        blurSigma: 16,
        borderAlpha: 0.30,
        radius: 20,
        padding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        shadowBlur: 20,
        shadowOffset: 10,
      ),
    };
  }

  final Color color;
  final double alpha;
  final double blurSigma;
  final double borderAlpha;
  final double radius;
  final EdgeInsetsGeometry padding;
  final double shadowBlur;
  final double shadowOffset;
}
