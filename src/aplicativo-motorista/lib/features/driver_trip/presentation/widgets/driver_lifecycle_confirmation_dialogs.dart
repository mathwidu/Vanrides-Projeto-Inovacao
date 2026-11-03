import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

Future<bool> showDriverLifecycleConfirmationDialog(
  BuildContext context, {
  required String title,
  required String message,
}) async {
  final confirmed = await showDialog<bool>(
    context: context,
    builder:
        (dialogContext) => AlertDialog(
          title: Text(title),
          content: Text(message),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: Text(AppStrings.motorista.manterOperacao),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: Text(AppStrings.motorista.confirmarAcao),
            ),
          ],
        ),
  );
  return confirmed ?? false;
}
