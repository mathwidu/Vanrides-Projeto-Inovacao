import 'package:flutter/material.dart';

import '../trip_live_map_state.dart';
import 'vanrides_map_shell.dart';

class TripLiveMapFrame extends StatelessWidget {
  const TripLiveMapFrame({
    super.key,
    required this.height,
    required this.body,
    required this.overlay,
    required this.showControls,
    required this.cameraLockedToContent,
    required this.onRecenter,
    required this.onZoomIn,
    required this.onZoomOut,
  });

  final double height;
  final Widget body;
  final Widget? overlay;
  final bool showControls;
  final bool cameraLockedToContent;
  final VoidCallback onRecenter;
  final VoidCallback onZoomIn;
  final VoidCallback onZoomOut;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(32),
        color: VanRidesMapTokens.backgroundDeep,
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.34),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x66070C12),
            blurRadius: 28,
            offset: Offset(0, 16),
          ),
        ],
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          body,
          Positioned.fill(
            child: IgnorePointer(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: VanRidesMapTokens.backgroundDeep.withValues(
                    alpha: 0.16,
                  ),
                ),
              ),
            ),
          ),
          Positioned.fill(
            child: IgnorePointer(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      VanRidesMapTokens.backgroundDeep.withValues(alpha: 0.22),
                      Colors.transparent,
                      VanRidesMapTokens.backgroundDeep.withValues(alpha: 0.46),
                    ],
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    stops: const [0, 0.38, 1],
                  ),
                ),
              ),
            ),
          ),
          if (overlay != null) Positioned.fill(child: overlay!),
          if (showControls)
            Positioned(
              right: 14,
              bottom: 14,
              child: VanRidesMapControlCluster(
                cameraLockedToContent: cameraLockedToContent,
                onRecenter: onRecenter,
                onZoomIn: onZoomIn,
                onZoomOut: onZoomOut,
              ),
            ),
        ],
      ),
    );
  }
}

class TripLiveMapPlaceholderSurface extends StatelessWidget {
  const TripLiveMapPlaceholderSurface({super.key, required this.content});

  final TripLiveMapPlaceholderContent content;

  @override
  Widget build(BuildContext context) {
    return VanRidesMapPlaceholderSurface(content: content);
  }
}
