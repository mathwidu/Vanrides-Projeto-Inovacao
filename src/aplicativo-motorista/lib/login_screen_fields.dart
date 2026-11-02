import 'package:flutter/material.dart';

import 'app/theme/vanrides_design_tokens.dart';

InputDecoration loginInputDecoration({
  required String label,
  required IconData icon,
}) {
  return InputDecoration(
    labelText: label,
    filled: true,
    fillColor: VanRidesDesignTokens.surfaceRaised,
    labelStyle: const TextStyle(color: VanRidesDesignTokens.textMuted),
    prefixIcon: Icon(icon, color: VanRidesDesignTokens.textSecondary),
    contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: BorderSide.none,
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: BorderSide(
        color: VanRidesDesignTokens.border.withValues(alpha: 0.5),
        width: 1,
      ),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: const BorderSide(
        color: VanRidesDesignTokens.blueTracking,
        width: 1.4,
      ),
    ),
  );
}
