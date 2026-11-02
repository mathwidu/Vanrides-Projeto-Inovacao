import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_design_tokens.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_theme.dart';

void main() {
  test('dark theme exposes VanRides premium dark defaults', () {
    final theme = VanRidesTheme.dark();

    expect(theme.brightness, Brightness.dark);
    expect(theme.scaffoldBackgroundColor, VanRidesDesignTokens.backgroundDeep);
    expect(theme.colorScheme.primary, VanRidesDesignTokens.blueTracking);
    expect(theme.colorScheme.secondary, VanRidesDesignTokens.greenAction);
    expect(theme.appBarTheme.foregroundColor, VanRidesDesignTokens.textPrimary);
    expect(theme.bottomSheetTheme.backgroundColor, Colors.transparent);
  });

  test('design tokens expose operational semantic scale', () {
    expect(VanRidesDesignTokens.backgroundElevated, isNot(Colors.white));
    expect(VanRidesDesignTokens.surfaceGlass, isNot(Colors.white));
    expect(VanRidesDesignTokens.borderSubtle, isNot(Colors.transparent));
    expect(VanRidesDesignTokens.stateSuccess, VanRidesDesignTokens.greenAction);
    expect(
      VanRidesDesignTokens.stateWarning,
      VanRidesDesignTokens.amberWarning,
    );
    expect(VanRidesDesignTokens.stateDanger, VanRidesDesignTokens.redDanger);
    expect(VanRidesDesignTokens.controlHeightCompact, 44);
    expect(VanRidesDesignTokens.controlHeightComfortable, 52);
    expect(VanRidesDesignTokens.radiusSheet, greaterThan(24));
  });
}
