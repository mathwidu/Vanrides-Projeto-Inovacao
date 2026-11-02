import 'package:flutter/material.dart';

import '../../../../services/api_constants.dart';
import 'admin_portal_layout.dart';

class AdminPersonAvatar extends StatelessWidget {
  const AdminPersonAvatar({
    super.key,
    required this.name,
    this.photoUrl,
    this.radius = 22,
    this.background = AdminPortalColors.blueSoft,
    this.foreground = AdminPortalColors.text,
  });

  final String name;
  final String? photoUrl;
  final double radius;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    final resolvedPhotoUrl = _resolvePhotoUrl(photoUrl);
    final fallback = _AvatarFallback(
      initials: _initials(name),
      background: background,
      foreground: foreground,
    );

    return SizedBox(
      width: radius * 2,
      height: radius * 2,
      child: ClipOval(
        child:
            resolvedPhotoUrl == null
                ? fallback
                : Image.network(
                  resolvedPhotoUrl,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => fallback,
                ),
      ),
    );
  }
}

class _AvatarFallback extends StatelessWidget {
  const _AvatarFallback({
    required this.initials,
    required this.background,
    required this.foreground,
  });

  final String initials;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(color: background),
      child: Center(
        child: Text(
          initials,
          style: TextStyle(
            color: foreground,
            fontWeight: FontWeight.w800,
            fontSize: initials.length > 1 ? 12 : 14,
          ),
        ),
      ),
    );
  }
}

String? _resolvePhotoUrl(String? photoUrl) {
  final trimmed = photoUrl?.trim() ?? '';
  if (trimmed.isEmpty) {
    return null;
  }
  final uri = Uri.tryParse(trimmed);
  if (uri != null && uri.hasScheme) {
    return trimmed;
  }
  return Uri.parse(baseUrl).resolve(trimmed).toString();
}

String _initials(String value) {
  final parts = value
      .trim()
      .split(RegExp(r'\s+'))
      .where((part) => part.isNotEmpty)
      .toList(growable: false);
  if (parts.isEmpty) {
    return '?';
  }
  if (parts.length == 1) {
    return parts.first.substring(0, 1).toUpperCase();
  }
  return (parts.first.substring(0, 1) + parts.last.substring(0, 1))
      .toUpperCase();
}
