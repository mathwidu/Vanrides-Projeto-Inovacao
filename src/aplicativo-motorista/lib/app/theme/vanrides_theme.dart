import 'package:flutter/material.dart';

import 'vanrides_design_tokens.dart';

class VanRidesTheme {
  const VanRidesTheme._();

  static ThemeData dark() {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: VanRidesDesignTokens.blueTracking,
      brightness: Brightness.dark,
      primary: VanRidesDesignTokens.blueTracking,
      secondary: VanRidesDesignTokens.greenAction,
      tertiary: VanRidesDesignTokens.amberWarning,
      surface: VanRidesDesignTokens.surface,
      error: VanRidesDesignTokens.redDanger,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: colorScheme,
      primaryColor: VanRidesDesignTokens.blueTracking,
      scaffoldBackgroundColor: VanRidesDesignTokens.backgroundDeep,
      fontFamily: 'SF Pro Display',
      canvasColor: VanRidesDesignTokens.backgroundDeep,
      dividerColor: VanRidesDesignTokens.border.withValues(alpha: 0.34),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        foregroundColor: VanRidesDesignTokens.textPrimary,
        surfaceTintColor: Colors.transparent,
      ),
      bottomSheetTheme: const BottomSheetThemeData(
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: VanRidesDesignTokens.blueTracking,
          foregroundColor: VanRidesDesignTokens.onAccent,
          disabledBackgroundColor: VanRidesDesignTokens.surfaceRaised
              .withValues(alpha: 0.72),
          disabledForegroundColor: VanRidesDesignTokens.textMuted,
          minimumSize: const Size(44, 48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: VanRidesDesignTokens.textPrimary,
          minimumSize: const Size(44, 48),
          side: BorderSide(
            color: VanRidesDesignTokens.border.withValues(alpha: 0.48),
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: VanRidesDesignTokens.surfaceRaised.withValues(alpha: 0.64),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(18),
          borderSide: BorderSide(
            color: VanRidesDesignTokens.border.withValues(alpha: 0.38),
          ),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(18),
          borderSide: BorderSide(
            color: VanRidesDesignTokens.border.withValues(alpha: 0.38),
          ),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(18),
          borderSide: const BorderSide(
            color: VanRidesDesignTokens.blueTracking,
            width: 1.4,
          ),
        ),
        labelStyle: const TextStyle(color: VanRidesDesignTokens.textSecondary),
        hintStyle: const TextStyle(color: VanRidesDesignTokens.textMuted),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: VanRidesDesignTokens.surfaceRaised,
        contentTextStyle: const TextStyle(
          color: VanRidesDesignTokens.textPrimary,
          fontWeight: FontWeight.w600,
        ),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      ),
    );
  }
}
