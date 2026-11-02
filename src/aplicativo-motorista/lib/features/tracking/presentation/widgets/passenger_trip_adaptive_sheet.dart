import 'package:flutter/material.dart';

import '../../../../app/theme/vanrides_motion.dart';
import 'passenger_trip_sheet_detent.dart';

class PassengerTripAdaptiveSheet extends StatefulWidget {
  const PassengerTripAdaptiveSheet({
    super.key,
    required this.detent,
    required this.onDetentChanged,
    required this.peek,
    required this.resting,
    required this.expanded,
  });

  final PassengerTripSheetDetent detent;
  final ValueChanged<PassengerTripSheetDetent> onDetentChanged;
  final Widget peek;
  final Widget resting;
  final Widget expanded;

  @override
  State<PassengerTripAdaptiveSheet> createState() =>
      _PassengerTripAdaptiveSheetState();
}

class _PassengerTripAdaptiveSheetState
    extends State<PassengerTripAdaptiveSheet> {
  double _dragDelta = 0;

  void _handleDragEnd(DragEndDetails details) {
    final velocity = details.primaryVelocity ?? 0;
    final nextDetent =
        velocity < -180 || _dragDelta < -48
            ? widget.detent.expand()
            : velocity > 180 || _dragDelta > 48
            ? widget.detent.collapse()
            : widget.detent;
    _dragDelta = 0;
    if (nextDetent != widget.detent) {
      widget.onDetentChanged(nextDetent);
    }
  }

  @override
  Widget build(BuildContext context) {
    final motion = VanRidesMotionPolicy.of(context);

    return Semantics(
      label: widget.detent.semanticLabel,
      container: true,
      child: GestureDetector(
        key: const Key('passenger.trip.sheet'),
        behavior: HitTestBehavior.opaque,
        onVerticalDragStart: (_) {
          _dragDelta = 0;
        },
        onVerticalDragUpdate: (details) {
          _dragDelta += details.primaryDelta ?? 0;
        },
        onVerticalDragEnd: _handleDragEnd,
        child: AnimatedSwitcher(
          duration: motion.duration(VanRidesMotionTokens.sheetContentSwitch),
          switchInCurve: motion.curve(VanRidesMotionTokens.easeOutQuart),
          switchOutCurve: motion.curve(VanRidesMotionTokens.easeOutQuart),
          child: switch (widget.detent) {
            PassengerTripSheetDetent.peek => KeyedSubtree(
              key: const Key('passenger.trip.sheet.peek'),
              child: widget.peek,
            ),
            PassengerTripSheetDetent.resting => KeyedSubtree(
              key: const Key('passenger.trip.sheet.resting'),
              child: widget.resting,
            ),
            PassengerTripSheetDetent.expanded => KeyedSubtree(
              key: const Key('passenger.trip.sheet.expanded'),
              child: widget.expanded,
            ),
          },
        ),
      ),
    );
  }
}
