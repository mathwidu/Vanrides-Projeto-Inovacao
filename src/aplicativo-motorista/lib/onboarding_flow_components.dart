import 'package:flutter/material.dart';

import 'app/theme/vanrides_design_tokens.dart';

export 'onboarding_blur_dot.dart';

part 'onboarding_flow_extra.dart';

class OnboardingPalette {
  static const background = VanRidesDesignTokens.backgroundDeep;
  static const surface = VanRidesDesignTokens.surface;
  static const surfaceRaised = VanRidesDesignTokens.surfaceRaised;
  static const border = VanRidesDesignTokens.border;
  static const fieldFill = VanRidesDesignTokens.surfaceRaised;
  static const textStrong = VanRidesDesignTokens.textPrimary;
  static const textMuted = VanRidesDesignTokens.textMuted;
  static const passenger = VanRidesDesignTokens.blueTracking;
  static const passengerStrong = VanRidesDesignTokens.blueTrackingStrong;
  static const passengerSoft = Color(0x26358AC0);
  static const driver = VanRidesDesignTokens.greenAction;
  static const driverStrong = VanRidesDesignTokens.greenActionStrong;
  static const driverSoft = Color(0x2636B36E);
  static const danger = VanRidesDesignTokens.redDanger;
}

class OnboardingScaffold extends StatelessWidget {
  const OnboardingScaffold({
    super.key,
    required this.header,
    required this.child,
    this.maxWidth = 448,
    this.bottomPadding = 48,
    this.topPadding = 24,
    this.blurDots = const [],
    this.footer,
  });

  final Widget header;
  final Widget child;
  final double maxWidth;
  final double bottomPadding;
  final double topPadding;
  final List<Widget> blurDots;
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    final baseTheme = Theme.of(context);
    final darkTheme = baseTheme.copyWith(
      colorScheme: baseTheme.colorScheme.copyWith(
        primary: OnboardingPalette.passenger,
        secondary: OnboardingPalette.driver,
        surface: OnboardingPalette.surface,
        onSurface: OnboardingPalette.textStrong,
        error: OnboardingPalette.danger,
      ),
      textTheme: baseTheme.textTheme.apply(
        bodyColor: OnboardingPalette.textStrong,
        displayColor: OnboardingPalette.textStrong,
      ),
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: OnboardingPalette.passenger,
        selectionColor: OnboardingPalette.passenger.withValues(alpha: 0.28),
        selectionHandleColor: OnboardingPalette.passenger,
      ),
    );

    return Theme(
      data: darkTheme,
      child: Scaffold(
        backgroundColor: OnboardingPalette.background,
        body: Stack(
          children: [
            Positioned.fill(
              child: IgnorePointer(child: Stack(children: blurDots)),
            ),
            SafeArea(
              child: Column(
                children: [
                  header,
                  Expanded(
                    child: Center(
                      child: SingleChildScrollView(
                        padding: EdgeInsets.fromLTRB(
                          24,
                          topPadding,
                          24,
                          bottomPadding,
                        ),
                        child: ConstrainedBox(
                          constraints: BoxConstraints(maxWidth: maxWidth),
                          child: child,
                        ),
                      ),
                    ),
                  ),
                  if (footer != null)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.fromLTRB(24, 12, 24, 16),
                      decoration: BoxDecoration(
                        color: OnboardingPalette.background.withValues(
                          alpha: 0.94,
                        ),
                        border: Border(
                          top: BorderSide(
                            color: OnboardingPalette.border.withValues(
                              alpha: 0.55,
                            ),
                          ),
                        ),
                      ),
                      child: Center(
                        child: ConstrainedBox(
                          constraints: BoxConstraints(maxWidth: maxWidth),
                          child: footer!,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class OnboardingHeader extends StatelessWidget {
  const OnboardingHeader({
    super.key,
    this.onBack,
    this.badgeText,
    this.badgeColor = OnboardingPalette.driver,
    this.brandColor = OnboardingPalette.passenger,
  });

  final VoidCallback? onBack;
  final String? badgeText;
  final Color badgeColor;
  final Color brandColor;

  @override
  Widget build(BuildContext context) {
    final badge =
        badgeText == null
            ? null
            : Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: badgeColor.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(999),
              ),
              child: Text(
                badgeText!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: badgeColor,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.2,
                ),
              ),
            );

    return Container(
      color: OnboardingPalette.background.withValues(alpha: 0.94),
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      child: Row(
        children: [
          Expanded(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                InkWell(
                  onTap: onBack,
                  borderRadius: BorderRadius.circular(999),
                  child: const Padding(
                    padding: EdgeInsets.all(8),
                    child: Icon(
                      Icons.arrow_back_rounded,
                      color: OnboardingPalette.passenger,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Flexible(
                  child: Text(
                    'VanRides',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      color: brandColor,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (badge != null) ...[
            const SizedBox(width: 8),
            Flexible(
              child: Align(
                alignment: Alignment.centerRight,
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  alignment: Alignment.centerRight,
                  child: badge,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class OnboardingSectionHeader extends StatelessWidget {
  const OnboardingSectionHeader({
    super.key,
    required this.title,
    required this.subtitle,
    this.titleColor = OnboardingPalette.textStrong,
  });

  final String title;
  final String subtitle;
  final Color titleColor;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.displaySmall?.copyWith(
            color: titleColor,
            fontWeight: FontWeight.w800,
            fontSize: 30,
            height: 1.2,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          subtitle,
          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
            color: OnboardingPalette.textMuted,
            height: 1.5,
          ),
        ),
      ],
    );
  }
}

class OnboardingFieldLabel extends StatelessWidget {
  const OnboardingFieldLabel(this.label, {super.key});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: Theme.of(context).textTheme.labelMedium?.copyWith(
        color: OnboardingPalette.textMuted,
        fontWeight: FontWeight.w700,
        letterSpacing: 0.4,
      ),
    );
  }
}

InputDecoration onboardingInputDecoration({
  required String hintText,
  Widget? prefixIcon,
  Widget? suffixIcon,
}) {
  return InputDecoration(
    hintText: hintText,
    filled: true,
    fillColor: OnboardingPalette.fieldFill,
    prefixIcon: prefixIcon,
    suffixIcon: suffixIcon,
    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
    hintStyle: const TextStyle(
      color: Color(0xB392A1B2),
      fontSize: 16,
      fontWeight: FontWeight.w400,
    ),
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: BorderSide.none,
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: BorderSide(
        color: OnboardingPalette.border.withValues(alpha: 0.32),
      ),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(
        color: OnboardingPalette.passenger,
        width: 1.4,
      ),
    ),
    errorBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: OnboardingPalette.danger, width: 1.2),
    ),
    focusedErrorBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: OnboardingPalette.danger, width: 1.4),
    ),
  );
}
