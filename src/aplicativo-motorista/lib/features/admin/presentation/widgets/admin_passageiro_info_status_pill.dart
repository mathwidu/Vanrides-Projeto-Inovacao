import 'package:flutter/material.dart';

import 'admin_portal_layout.dart';

class AdminPassageiroInfoStatusPill extends StatelessWidget {
  const AdminPassageiroInfoStatusPill({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: AdminPortalColors.statusSurface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Text(
        label,
        style: const TextStyle(
          color: AdminPortalColors.textMuted,
          fontSize: 10,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}
