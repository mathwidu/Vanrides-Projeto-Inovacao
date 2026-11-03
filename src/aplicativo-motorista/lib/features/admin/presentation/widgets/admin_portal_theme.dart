import 'package:flutter/material.dart';

import 'admin_portal_colors.dart';

ThemeData adminPortalTheme(ThemeData baseTheme) {
  final borderRadius = BorderRadius.circular(16);
  final inputBorder = OutlineInputBorder(
    borderRadius: borderRadius,
    borderSide: const BorderSide(color: AdminPortalColors.outline),
  );
  final focusedInputBorder = OutlineInputBorder(
    borderRadius: borderRadius,
    borderSide: const BorderSide(
      color: AdminPortalColors.blueBright,
      width: 1.4,
    ),
  );
  final errorInputBorder = OutlineInputBorder(
    borderRadius: borderRadius,
    borderSide: const BorderSide(color: AdminPortalColors.red),
  );

  return baseTheme.copyWith(
    brightness: Brightness.dark,
    canvasColor: AdminPortalColors.canvas,
    scaffoldBackgroundColor: AdminPortalColors.canvas,
    disabledColor: AdminPortalColors.textFaint,
    focusColor: AdminPortalColors.blueSoft,
    hoverColor: AdminPortalColors.surfaceSoft.withValues(alpha: 0.58),
    highlightColor: AdminPortalColors.blueSoft,
    dividerColor: AdminPortalColors.divider,
    colorScheme: const ColorScheme.dark(
      primary: AdminPortalColors.blueBright,
      onPrimary: AdminPortalColors.canvas,
      secondary: AdminPortalColors.green,
      onSecondary: AdminPortalColors.canvas,
      error: AdminPortalColors.red,
      onError: AdminPortalColors.canvas,
      surface: AdminPortalColors.surfaceSoft,
      onSurface: AdminPortalColors.text,
    ),
    textTheme: baseTheme.textTheme.apply(
      bodyColor: AdminPortalColors.text,
      displayColor: AdminPortalColors.text,
    ),
    iconTheme: const IconThemeData(color: AdminPortalColors.textMuted),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: AdminPortalColors.elevatedSurface,
      actionTextColor: AdminPortalColors.blueBright,
      disabledActionTextColor: AdminPortalColors.textFaint,
      contentTextStyle: const TextStyle(
        color: AdminPortalColors.text,
        fontWeight: FontWeight.w700,
      ),
      elevation: 12,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: AdminPortalColors.input,
      labelStyle: const TextStyle(color: AdminPortalColors.textMuted),
      helperStyle: const TextStyle(color: AdminPortalColors.textFaint),
      hintStyle: const TextStyle(color: AdminPortalColors.textFaint),
      errorStyle: const TextStyle(color: AdminPortalColors.red),
      border: inputBorder,
      enabledBorder: inputBorder,
      focusedBorder: focusedInputBorder,
      errorBorder: errorInputBorder,
      focusedErrorBorder: errorInputBorder,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: ButtonStyle(
        backgroundColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.disabled)) {
            return AdminPortalColors.surfaceSoft;
          }
          return AdminPortalColors.blueBright;
        }),
        foregroundColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.disabled)) {
            return AdminPortalColors.textFaint;
          }
          return AdminPortalColors.canvas;
        }),
        overlayColor: WidgetStateProperty.all(
          AdminPortalColors.glassHighlight,
        ),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: AdminPortalColors.text,
        disabledForegroundColor: AdminPortalColors.textFaint,
        side: const BorderSide(color: AdminPortalColors.outline),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: AdminPortalColors.blueBright,
        disabledForegroundColor: AdminPortalColors.textFaint,
      ),
    ),
    popupMenuTheme: PopupMenuThemeData(
      color: AdminPortalColors.elevatedSurface,
      surfaceTintColor: Colors.transparent,
      textStyle: const TextStyle(color: AdminPortalColors.text),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
    ),
    dropdownMenuTheme: DropdownMenuThemeData(
      textStyle: const TextStyle(color: AdminPortalColors.text),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AdminPortalColors.input,
        border: inputBorder,
        enabledBorder: inputBorder,
        focusedBorder: focusedInputBorder,
      ),
    ),
    datePickerTheme: DatePickerThemeData(
      backgroundColor: AdminPortalColors.elevatedSurface,
      surfaceTintColor: Colors.transparent,
      headerBackgroundColor: AdminPortalColors.surfaceSoft,
      headerForegroundColor: AdminPortalColors.text,
      dayForegroundColor: WidgetStateProperty.all(AdminPortalColors.text),
      todayForegroundColor: WidgetStateProperty.all(AdminPortalColors.blue),
      todayBorder: const BorderSide(color: AdminPortalColors.blue),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
    ),
  );
}
