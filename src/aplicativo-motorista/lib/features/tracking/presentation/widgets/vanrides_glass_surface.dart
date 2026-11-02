import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_surface.dart';

enum VanRidesGlassVariant {
  subtle,
  sheet,
  nav,
  card,
  dialog,
  action,
  solidFallback,
}

class VanRidesGlassSurface extends StatelessWidget {
  const VanRidesGlassSurface({
    super.key,
    required this.child,
    this.variant = VanRidesGlassVariant.subtle,
    this.semanticLabel,
    this.borderRadius = 24,
    this.padding = const EdgeInsets.all(12),
  });

  const VanRidesGlassSurface.subtle({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius = 20,
    this.padding = const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
  }) : variant = VanRidesGlassVariant.subtle;

  const VanRidesGlassSurface.sheet({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius = 30,
    this.padding = const EdgeInsets.fromLTRB(18, 12, 18, 16),
  }) : variant = VanRidesGlassVariant.sheet;

  const VanRidesGlassSurface.nav({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius = 30,
    this.padding = const EdgeInsets.all(8),
  }) : variant = VanRidesGlassVariant.nav;

  const VanRidesGlassSurface.card({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius = 22,
    this.padding = const EdgeInsets.all(18),
  }) : variant = VanRidesGlassVariant.card;

  const VanRidesGlassSurface.dialog({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius = 28,
    this.padding = const EdgeInsets.all(22),
  }) : variant = VanRidesGlassVariant.dialog;

  const VanRidesGlassSurface.action({
    super.key,
    required this.child,
    this.semanticLabel,
    this.borderRadius = 18,
    this.padding = const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
  }) : variant = VanRidesGlassVariant.action;

  static const glassKey = ValueKey('vanrides.glass.surface');
  static const solidFallbackKey = ValueKey('vanrides.glass.solid-fallback');

  final Widget child;
  final VanRidesGlassVariant variant;
  final String? semanticLabel;
  final double borderRadius;
  final EdgeInsetsGeometry padding;

  static bool shouldUseSolidFallback(MediaQueryData mediaQuery) {
    return VanRidesLiquidSurface.shouldUseSolidFallback(mediaQuery);
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final useSolidFallback =
        variant == VanRidesGlassVariant.solidFallback ||
        shouldUseSolidFallback(mediaQuery);

    return VanRidesLiquidSurface(
      key: useSolidFallback ? solidFallbackKey : glassKey,
      variant: _liquidVariant,
      semanticLabel: semanticLabel,
      borderRadius: borderRadius,
      padding: padding,
      forceSolidFallback: useSolidFallback,
      child: child,
    );
  }

  VanRidesLiquidSurfaceVariant get _liquidVariant {
    return switch (variant) {
      VanRidesGlassVariant.sheet => VanRidesLiquidSurfaceVariant.sheet,
      VanRidesGlassVariant.nav => VanRidesLiquidSurfaceVariant.nav,
      VanRidesGlassVariant.card => VanRidesLiquidSurfaceVariant.card,
      VanRidesGlassVariant.dialog => VanRidesLiquidSurfaceVariant.dialog,
      VanRidesGlassVariant.action => VanRidesLiquidSurfaceVariant.action,
      VanRidesGlassVariant.subtle ||
      VanRidesGlassVariant.solidFallback => VanRidesLiquidSurfaceVariant.subtle,
    };
  }
}
