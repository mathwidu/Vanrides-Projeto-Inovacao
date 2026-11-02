import 'package:flutter/material.dart';

import 'admin_portal_layout.dart';

class AdminLoginHero extends StatelessWidget {
  const AdminLoginHero({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xF2142336), Color(0xD90E1A2A)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(48),
        border: Border.all(color: AdminPortalColors.glassHighlight),
        boxShadow: const [
          BoxShadow(
            color: Color(0x66000000),
            blurRadius: 44,
            offset: Offset(0, 18),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(36, 32, 36, 32),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final useFlexibleFooterGap =
                constraints.hasBoundedHeight && constraints.maxHeight >= 720;
            final tightVertical =
                constraints.hasBoundedHeight && constraints.maxHeight < 840;
            final compactHorizontal = constraints.maxWidth < 420;
            final iconTileSize = tightVertical ? 116.0 : 132.0;
            final headlineSize = tightVertical ? 48.0 : 52.0;

            return Column(
              mainAxisSize:
                  useFlexibleFooterGap ? MainAxisSize.max : MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (compactHorizontal)
                  const Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          _BrandIcon(),
                          SizedBox(width: 12),
                          Text(
                            'VanRides',
                            style: TextStyle(
                              color: AdminPortalColors.text,
                              fontSize: 24,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ],
                      ),
                      SizedBox(height: 12),
                      _PortalStatusPill(),
                    ],
                  )
                else
                  const Row(
                    children: [
                      _BrandIcon(),
                      SizedBox(width: 12),
                      Text(
                        'VanRides',
                        style: TextStyle(
                          color: AdminPortalColors.text,
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      Spacer(),
                      _PortalStatusPill(),
                    ],
                  ),
                SizedBox(height: tightVertical ? 28 : 40),
                Container(
                  width: iconTileSize,
                  height: iconTileSize,
                  decoration: BoxDecoration(
                    color: AdminPortalColors.input.withValues(alpha: 0.88),
                    borderRadius: BorderRadius.circular(36),
                    border: Border.all(
                      color: AdminPortalColors.outline.withValues(alpha: 0.72),
                    ),
                  ),
                  child: const Stack(
                    clipBehavior: Clip.none,
                    children: [
                      Center(
                        child: Icon(
                          Icons.admin_panel_settings_rounded,
                          color: AdminPortalColors.blueBright,
                          size: 54,
                        ),
                      ),
                      Positioned(
                        right: -10,
                        top: -10,
                        child: CircleAvatar(
                          radius: 20,
                          backgroundColor: AdminPortalColors.green,
                          child: Icon(
                            Icons.shield_outlined,
                            color: AdminPortalColors.canvas,
                            size: 18,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                SizedBox(height: tightVertical ? 28 : 36),
                Text(
                  'Portal operacional da empresa',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: headlineSize,
                    fontWeight: FontWeight.w800,
                    height: 1.04,
                    letterSpacing: 0,
                  ),
                ),
                const SizedBox(height: 16),
                const Text(
                  'Gerencie rotas, viagens, passageiros e vínculos em uma experiência administrativa preparada para a operação diária.',
                  style: TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 18,
                    height: 1.6,
                  ),
                ),
                SizedBox(height: tightVertical ? 22 : 28),
                const Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  children: [
                    _HeroPill(
                      icon: Icons.alt_route_rounded,
                      label: 'Rotas-base',
                    ),
                    _HeroPill(
                      icon: Icons.departure_board_rounded,
                      label: 'Viagens do dia',
                    ),
                    _HeroPill(
                      icon: Icons.groups_rounded,
                      label: 'Equipe e frota',
                    ),
                    _HeroPill(
                      icon: Icons.verified_user_outlined,
                      label: 'Segurança por contrato',
                    ),
                  ],
                ),
                if (useFlexibleFooterGap)
                  const Spacer()
                else
                  SizedBox(height: tightVertical ? 24 : 32),
                const _HeroFooterCard(),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _BrandIcon extends StatelessWidget {
  const _BrandIcon();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 38,
      height: 38,
      decoration: BoxDecoration(
        color: AdminPortalColors.blueSoft,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: AdminPortalColors.blue.withValues(alpha: 0.3),
        ),
      ),
      child: const Icon(
        Icons.directions_bus_filled_rounded,
        color: AdminPortalColors.blueBright,
        size: 20,
      ),
    );
  }
}

class _PortalStatusPill extends StatelessWidget {
  const _PortalStatusPill();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: AdminPortalColors.greenSoft,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(
          color: AdminPortalColors.green.withValues(alpha: 0.3),
        ),
      ),
      child: const Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.lock_outline_rounded,
            color: AdminPortalColors.green,
            size: 15,
          ),
          SizedBox(width: 6),
          Text(
            'Acesso seguro',
            style: TextStyle(
              color: AdminPortalColors.successText,
              fontSize: 12,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _HeroPill extends StatelessWidget {
  const _HeroPill({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(maxWidth: 260),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AdminPortalColors.surfaceSoft.withValues(alpha: 0.78),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 15, color: AdminPortalColors.blueBright),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AdminPortalColors.text,
                fontSize: 14,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _HeroFooterCard extends StatelessWidget {
  const _HeroFooterCard();

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AdminPortalColors.blueBright.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(32),
        border: Border.all(
          color: AdminPortalColors.blueBright.withValues(alpha: 0.34),
        ),
      ),
      padding: const EdgeInsets.all(24),
      child: const Row(
        children: [
          CircleAvatar(
            radius: 24,
            backgroundColor: AdminPortalColors.blueSoft,
            child: Icon(
              Icons.verified_user_outlined,
              color: AdminPortalColors.blueBright,
            ),
          ),
          SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Bootstrap do primeiro admin',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                SizedBox(height: 6),
                Text(
                  'Crie o primeiro acesso da empresa e envie a confirmação por e-mail sem seed manual.',
                  style: TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 14,
                    height: 1.5,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
