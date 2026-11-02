import 'package:flutter/material.dart';

import '../trip_live_map_state.dart';
import 'vanrides_map_tokens.dart';

class VanRidesMapPlaceholderSurface extends StatelessWidget {
  const VanRidesMapPlaceholderSurface({super.key, required this.content});

  final TripLiveMapPlaceholderContent content;

  @override
  Widget build(BuildContext context) {
    final accentColor =
        content.loading
            ? VanRidesMapTokens.blueTracking
            : content.routeReady
            ? VanRidesMapTokens.blueTracking
            : content.waitingForSignal
            ? VanRidesMapTokens.amberWarning
            : VanRidesMapTokens.redDanger;

    return DecoratedBox(
      key: const Key('vanrides.map.placeholder.surface'),
      decoration: const BoxDecoration(color: VanRidesMapTokens.mapBase),
      child: Stack(
        fit: StackFit.expand,
        children: [
          const Positioned.fill(
            child: CustomPaint(painter: _VanRidesMapTexturePainter()),
          ),
          LayoutBuilder(
            builder: (context, constraints) {
              final horizontalPadding =
                  constraints.maxWidth < 340 ? 18.0 : 28.0;

              return Center(
                child: Padding(
                  padding: EdgeInsets.symmetric(
                    horizontal: horizontalPadding,
                    vertical: 24,
                  ),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 328),
                    child: _VanRidesPlaceholderPanel(
                      content: content,
                      accentColor: accentColor,
                    ),
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}

class _VanRidesPlaceholderPanel extends StatelessWidget {
  const _VanRidesPlaceholderPanel({
    required this.content,
    required this.accentColor,
  });

  final TripLiveMapPlaceholderContent content;
  final Color accentColor;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.5),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x59070C12),
            blurRadius: 26,
            offset: Offset(0, 16),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _VanRidesPlaceholderSignal(
              content: content,
              accentColor: accentColor,
            ),
            const SizedBox(height: 14),
            Text(
              content.title,
              textAlign: TextAlign.center,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: VanRidesMapTokens.textPrimary,
                fontWeight: FontWeight.w800,
                height: 1.16,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              content.description,
              textAlign: TextAlign.center,
              maxLines: 5,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: VanRidesMapTokens.textSecondary,
                height: 1.38,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _VanRidesPlaceholderSignal extends StatelessWidget {
  const _VanRidesPlaceholderSignal({
    required this.content,
    required this.accentColor,
  });

  final TripLiveMapPlaceholderContent content;
  final Color accentColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 54,
      height: 54,
      decoration: BoxDecoration(
        color: accentColor.withValues(alpha: 0.14),
        shape: BoxShape.circle,
        border: Border.all(color: accentColor.withValues(alpha: 0.54)),
      ),
      child:
          content.loading
              ? Padding(
                padding: const EdgeInsets.all(15),
                child: CircularProgressIndicator(
                  strokeWidth: 3,
                  color: accentColor,
                  backgroundColor: VanRidesMapTokens.border.withValues(
                    alpha: 0.28,
                  ),
                ),
              )
              : Icon(content.icon, color: accentColor, size: 26),
    );
  }
}

class _VanRidesMapTexturePainter extends CustomPainter {
  const _VanRidesMapTexturePainter();

  @override
  void paint(Canvas canvas, Size size) {
    final gridPaint =
        Paint()
          ..color = VanRidesMapTokens.border.withValues(alpha: 0.12)
          ..strokeWidth = 1.4
          ..style = PaintingStyle.stroke;
    final minorGridPaint =
        Paint()
          ..color = VanRidesMapTokens.border.withValues(alpha: 0.07)
          ..strokeWidth = 1
          ..style = PaintingStyle.stroke;
    final plannedRoutePaint =
        Paint()
          ..color = VanRidesMapTokens.blueTracking.withValues(alpha: 0.28)
          ..strokeWidth = 9
          ..strokeCap = StrokeCap.round
          ..style = PaintingStyle.stroke;
    final activeRoutePaint =
        Paint()
          ..color = VanRidesMapTokens.greenAction.withValues(alpha: 0.7)
          ..strokeWidth = 4
          ..strokeCap = StrokeCap.round
          ..style = PaintingStyle.stroke;
    final stopPaint =
        Paint()
          ..color = VanRidesMapTokens.textPrimary.withValues(alpha: 0.72)
          ..style = PaintingStyle.fill;
    final nextStopPaint =
        Paint()
          ..color = VanRidesMapTokens.blueTracking
          ..style = PaintingStyle.fill;

    for (var i = -size.height; i < size.width + size.height; i += 72) {
      canvas.drawLine(
        Offset(i, 0),
        Offset(i + size.height, size.height),
        minorGridPaint,
      );
    }
    for (var i = 0.0; i < size.width; i += 64) {
      canvas.drawLine(Offset(i, 0), Offset(i, size.height), gridPaint);
    }
    for (var j = 0.0; j < size.height; j += 64) {
      canvas.drawLine(Offset(0, j), Offset(size.width, j), gridPaint);
    }

    final route =
        Path()
          ..moveTo(size.width * 0.1, size.height * 0.78)
          ..quadraticBezierTo(
            size.width * 0.28,
            size.height * 0.22,
            size.width * 0.52,
            size.height * 0.48,
          )
          ..quadraticBezierTo(
            size.width * 0.73,
            size.height * 0.7,
            size.width * 0.9,
            size.height * 0.24,
          );
    canvas.drawPath(route, plannedRoutePaint);
    canvas.drawPath(route, activeRoutePaint);

    final stops = [
      Offset(size.width * 0.18, size.height * 0.62),
      Offset(size.width * 0.52, size.height * 0.48),
      Offset(size.width * 0.82, size.height * 0.34),
    ];
    canvas.drawCircle(
      stops[1],
      13,
      Paint()
        ..color = VanRidesMapTokens.blueTracking.withValues(alpha: 0.16)
        ..style = PaintingStyle.fill,
    );
    for (final stop in stops) {
      canvas.drawCircle(stop, 5, stopPaint);
    }
    canvas.drawCircle(stops[1], 8, nextStopPaint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
