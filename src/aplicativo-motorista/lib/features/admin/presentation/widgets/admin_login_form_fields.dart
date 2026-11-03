import 'package:flutter/material.dart';

import 'admin_portal_colors.dart';

class AdminLoginFieldLabel extends StatelessWidget {
  const AdminLoginFieldLabel({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: const TextStyle(
        color: AdminPortalColors.textMuted,
        fontSize: 14,
        fontWeight: FontWeight.w700,
      ),
    );
  }
}

InputDecoration adminLoginInputDecoration({required String hintText}) {
  return InputDecoration(
    hintText: hintText,
    filled: true,
    fillColor: AdminPortalColors.input,
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: BorderSide.none,
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: BorderSide.none,
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: const BorderSide(color: AdminPortalColors.blueBright),
    ),
    contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
  );
}
