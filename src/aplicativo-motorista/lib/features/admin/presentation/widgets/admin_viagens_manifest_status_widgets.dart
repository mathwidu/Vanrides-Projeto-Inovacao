import 'package:flutter/material.dart';

import '../../domain/admin_viagem_record.dart';
import 'admin_portal_layout.dart';

class AdminViagensTripServiceSummary extends StatelessWidget {
  const AdminViagensTripServiceSummary({super.key, required this.viagem});

  final AdminViagemRecord viagem;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const CircleAvatar(
          radius: 18,
          backgroundColor: AdminPortalColors.blueSoft,
          child: Icon(
            Icons.route_outlined,
            color: AdminPortalColors.blue,
            size: 18,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                viagem.nomeOperacional,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                ),
              ),
              if (viagem.manifesto.isVisible) ...[
                const SizedBox(height: 8),
                AdminViagensManifestStatusPill(manifesto: viagem.manifesto),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class AdminViagensManifestStatusPill extends StatelessWidget {
  const AdminViagensManifestStatusPill({super.key, required this.manifesto});

  final AdminViagemManifestRecord manifesto;

  @override
  Widget build(BuildContext context) {
    final palette = _AdminManifestStatusPalette.resolve(manifesto);
    final delta = manifesto.deltaLabel;
    return Wrap(
      spacing: 8,
      runSpacing: 6,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          decoration: BoxDecoration(
            color: palette.background,
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: palette.border),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(palette.icon, color: palette.foreground, size: 14),
              const SizedBox(width: 6),
              Text(
                manifesto.label,
                style: TextStyle(
                  color: palette.foreground,
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
        ),
        if (delta.isNotEmpty)
          Text(
            delta,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 11,
              fontWeight: FontWeight.w700,
            ),
          ),
      ],
    );
  }
}

class _AdminManifestStatusPalette {
  const _AdminManifestStatusPalette({
    required this.background,
    required this.border,
    required this.foreground,
    required this.icon,
  });

  final Color background;
  final Color border;
  final Color foreground;
  final IconData icon;

  static _AdminManifestStatusPalette resolve(
    AdminViagemManifestRecord manifesto,
  ) {
    if (manifesto.isPending) {
      return const _AdminManifestStatusPalette(
        background: Color(0x2438BDF8),
        border: Color(0x664DB8FF),
        foreground: AdminPortalColors.blue,
        icon: Icons.sync_problem_rounded,
      );
    }
    if (manifesto.isBlocked) {
      return const _AdminManifestStatusPalette(
        background: Color(0x24F4B63D),
        border: Color(0x66F4B63D),
        foreground: Color(0xFFF4B63D),
        icon: Icons.lock_clock_outlined,
      );
    }
    return const _AdminManifestStatusPalette(
      background: Color(0x2238D17A),
      border: Color(0x5538D17A),
      foreground: AdminPortalColors.green,
      icon: Icons.check_circle_outline,
    );
  }
}
