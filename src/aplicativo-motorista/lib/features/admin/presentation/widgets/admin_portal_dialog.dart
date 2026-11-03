import 'package:flutter/material.dart';

import 'admin_portal_layout.dart';

class AdminPortalDialog extends StatelessWidget {
  const AdminPortalDialog({
    super.key,
    required this.title,
    required this.child,
    this.subtitle,
    this.actions = const [],
    this.maxWidth = 560,
  });

  final String title;
  final String? subtitle;
  final Widget child;
  final List<Widget> actions;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    final baseTheme = Theme.of(context);

    return Dialog(
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      child: Theme(
        data: _dialogTheme(baseTheme),
        child: AdminPortalSurface(
          radius: 28,
          padding: const EdgeInsets.all(24),
          color: AdminPortalColors.elevatedSurface,
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: maxWidth),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    height: 1.2,
                  ),
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    subtitle!,
                    style: const TextStyle(
                      color: AdminPortalColors.textMuted,
                      fontSize: 14,
                      height: 1.45,
                    ),
                  ),
                ],
                const SizedBox(height: 24),
                Flexible(fit: FlexFit.loose, child: child),
                if (actions.isNotEmpty) ...[
                  const SizedBox(height: 24),
                  Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    alignment: WrapAlignment.end,
                    children: actions,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  ThemeData _dialogTheme(ThemeData baseTheme) {
    final borderRadius = BorderRadius.circular(14);
    const enabledBorder = OutlineInputBorder(
      borderSide: BorderSide(color: AdminPortalColors.outline),
      borderRadius: BorderRadius.all(Radius.circular(14)),
    );
    const focusedBorder = OutlineInputBorder(
      borderSide: BorderSide(color: AdminPortalColors.blueBright, width: 1.4),
      borderRadius: BorderRadius.all(Radius.circular(14)),
    );
    const errorBorder = OutlineInputBorder(
      borderSide: BorderSide(color: AdminPortalColors.red),
      borderRadius: BorderRadius.all(Radius.circular(14)),
    );

    return baseTheme.copyWith(
      brightness: Brightness.dark,
      canvasColor: AdminPortalColors.elevatedSurface,
      scaffoldBackgroundColor: AdminPortalColors.canvas,
      textTheme: baseTheme.textTheme.apply(
        bodyColor: AdminPortalColors.text,
        displayColor: AdminPortalColors.text,
      ),
      inputDecorationTheme: const InputDecorationTheme(
        filled: true,
        fillColor: AdminPortalColors.input,
        labelStyle: TextStyle(color: AdminPortalColors.textMuted),
        helperStyle: TextStyle(color: AdminPortalColors.textFaint),
        hintStyle: TextStyle(color: AdminPortalColors.textFaint),
        errorStyle: TextStyle(color: AdminPortalColors.red),
        contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        border: enabledBorder,
        enabledBorder: enabledBorder,
        focusedBorder: focusedBorder,
        errorBorder: errorBorder,
        focusedErrorBorder: errorBorder,
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: AdminPortalColors.textMuted,
          textStyle: const TextStyle(fontWeight: FontWeight.w700),
          shape: RoundedRectangleBorder(borderRadius: borderRadius),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: AdminPortalColors.blueBright,
          foregroundColor: AdminPortalColors.canvas,
          disabledBackgroundColor: AdminPortalColors.surfaceSoft,
          disabledForegroundColor: AdminPortalColors.textFaint,
          textStyle: const TextStyle(fontWeight: FontWeight.w800),
          shape: RoundedRectangleBorder(borderRadius: borderRadius),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AdminPortalColors.text,
          side: const BorderSide(color: AdminPortalColors.outline),
          shape: RoundedRectangleBorder(borderRadius: borderRadius),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
        ),
      ),
      checkboxTheme: CheckboxThemeData(
        fillColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return AdminPortalColors.blueBright;
          }
          return AdminPortalColors.input;
        }),
        checkColor: WidgetStateProperty.all(AdminPortalColors.canvas),
        side: const BorderSide(color: AdminPortalColors.outline),
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return AdminPortalColors.blueBright;
          }
          return AdminPortalColors.textMuted;
        }),
        trackColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return AdminPortalColors.blueSoft;
          }
          return AdminPortalColors.surfaceSoft;
        }),
      ),
      dropdownMenuTheme: const DropdownMenuThemeData(
        textStyle: TextStyle(color: AdminPortalColors.text),
      ),
      listTileTheme: const ListTileThemeData(
        textColor: AdminPortalColors.text,
        iconColor: AdminPortalColors.textMuted,
      ),
    );
  }
}

class AdminPortalDialogError extends StatelessWidget {
  const AdminPortalDialogError({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AdminPortalColors.redSoft,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AdminPortalColors.red.withValues(alpha: 0.3)),
      ),
      child: Text(
        message,
        style: const TextStyle(color: AdminPortalColors.red),
      ),
    );
  }
}
