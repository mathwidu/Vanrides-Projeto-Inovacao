import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';

import '../../../../app/theme/vanrides_motion.dart';
import 'passenger_trip_sheet_detent.dart';
import 'vanrides_map_tokens.dart';

typedef PassengerFooterSheetBuilder =
    Widget Function(
      BuildContext context,
      ScrollController scrollController,
      PassengerTripSheetDetent detent,
      ValueChanged<PassengerTripSheetDetent> onDetentSelected,
    );

class PassengerMapFooterSheetScaffold extends StatefulWidget {
  const PassengerMapFooterSheetScaffold({
    super.key,
    required this.map,
    required this.topBar,
    required this.footer,
    required this.sheetBuilder,
    this.horizontalPadding = 14,
    this.topPadding = 16,
  });

  static const rootKey = Key('passenger.map.footerSheet');
  static const footerKey = Key('passenger.map.footerSheet.footer');
  static const surfaceKey = Key('passenger.map.footerSheet.surface');
  static const sheetSafeAreaKey = Key('passenger.map.footerSheet.safeArea');

  final Widget map;
  final Widget topBar;
  final Widget footer;
  final PassengerFooterSheetBuilder sheetBuilder;
  final double horizontalPadding;
  final double topPadding;

  @override
  State<PassengerMapFooterSheetScaffold> createState() =>
      _PassengerMapFooterSheetScaffoldState();
}

class _PassengerMapFooterSheetScaffoldState
    extends State<PassengerMapFooterSheetScaffold> {
  late final DraggableScrollableController _sheetController;
  late final ValueNotifier<double> _extent;

  @override
  void initState() {
    super.initState();
    _sheetController = DraggableScrollableController();
    _extent = ValueNotifier<double>(PassengerTripSheetDetent.initial.snapSize);
  }

  @override
  void dispose() {
    _extent.dispose();
    _sheetController.dispose();
    super.dispose();
  }

  void _animateTo(PassengerTripSheetDetent detent) {
    _extent.value = detent.snapSize;
    if (!_sheetController.isAttached) {
      return;
    }
    final mediaQuery = MediaQuery.maybeOf(context);
    final motion =
        mediaQuery == null
            ? const VanRidesMotionPolicy(reduceMotion: false)
            : VanRidesMotionPolicy.fromMediaQuery(mediaQuery);
    if (motion.reduceMotion) {
      _sheetController.jumpTo(detent.snapSize);
      return;
    }
    unawaited(
      _sheetController.animateTo(
        detent.snapSize,
        duration: motion.duration(
          PassengerTripSheetDetent.programmaticAnimationDuration,
        ),
        curve: motion.curve(VanRidesMotionTokens.easeOutQuart),
      ),
    );
  }

  bool _handleSheetNotification(DraggableScrollableNotification notification) {
    if ((_extent.value - notification.extent).abs() > 0.005) {
      _extent.value = notification.extent;
    }
    return false;
  }

  Widget _buildSheetSurface(
    BuildContext context,
    ScrollController scrollController,
  ) {
    return ValueListenableBuilder<double>(
      valueListenable: _extent,
      builder: (context, extent, _) {
        final detent = PassengerTripSheetDetent.fromExtent(extent);
        final expandedProgress = ((extent -
                    PassengerTripSheetDetent.resting.snapSize) /
                (PassengerTripSheetDetent.expanded.snapSize -
                    PassengerTripSheetDetent.resting.snapSize))
            .clamp(0.0, 1.0);
        final radius = lerpDouble(32, 24, expandedProgress) ?? 30;
        final borderRadius = BorderRadius.circular(radius);

        return ClipRRect(
          borderRadius: borderRadius,
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 22, sigmaY: 22),
            child: DecoratedBox(
              key: PassengerMapFooterSheetScaffold.surfaceKey,
              decoration: BoxDecoration(
                color: VanRidesMapTokens.surface.withValues(
                  alpha: lerpDouble(0.72, 0.82, expandedProgress) ?? 0.76,
                ),
                borderRadius: borderRadius,
                border: Border.all(
                  color: VanRidesMapTokens.border.withValues(alpha: 0.32),
                ),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x66070C12),
                    blurRadius: 34,
                    offset: Offset(0, -12),
                  ),
                ],
              ),
              child: Column(
                children: [
                  Expanded(
                    child: widget.sheetBuilder(
                      context,
                      scrollController,
                      detent,
                      _animateTo,
                    ),
                  ),
                  Divider(
                    height: 1,
                    thickness: 1,
                    color: VanRidesMapTokens.border.withValues(alpha: 0.22),
                  ),
                  KeyedSubtree(
                    key: PassengerMapFooterSheetScaffold.footerKey,
                    child: widget.footer,
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Positioned.fill(child: widget.map),
        Positioned.fill(
          child: IgnorePointer(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.black.withValues(alpha: 0.04),
                    Colors.transparent,
                    Colors.black.withValues(alpha: 0.26),
                  ],
                  stops: const [0, 0.36, 1],
                ),
              ),
            ),
          ),
        ),
        SafeArea(
          child: Padding(
            padding: EdgeInsets.fromLTRB(
              widget.horizontalPadding,
              widget.topPadding,
              widget.horizontalPadding,
              0,
            ),
            child: widget.topBar,
          ),
        ),
        NotificationListener<DraggableScrollableNotification>(
          onNotification: _handleSheetNotification,
          child: DraggableScrollableSheet(
            key: PassengerMapFooterSheetScaffold.rootKey,
            controller: _sheetController,
            minChildSize: PassengerTripSheetDetent.peek.snapSize,
            initialChildSize: PassengerTripSheetDetent.initial.snapSize,
            maxChildSize: PassengerTripSheetDetent.expanded.snapSize,
            snap: true,
            snapSizes: PassengerTripSheetDetent.snapSizes,
            snapAnimationDuration:
                PassengerTripSheetDetent.snapAnimationDuration,
            builder: (context, scrollController) {
              return SafeArea(
                key: PassengerMapFooterSheetScaffold.sheetSafeAreaKey,
                top: false,
                minimum: EdgeInsets.fromLTRB(
                  widget.horizontalPadding,
                  0,
                  widget.horizontalPadding,
                  0,
                ),
                child: _buildSheetSurface(context, scrollController),
              );
            },
          ),
        ),
      ],
    );
  }
}
