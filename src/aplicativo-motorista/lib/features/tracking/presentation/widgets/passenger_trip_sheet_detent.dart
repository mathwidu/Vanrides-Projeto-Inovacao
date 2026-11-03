import '../../../../app/theme/vanrides_motion.dart';

enum PassengerTripSheetDetent {
  peek,
  resting,
  expanded;

  static const initial = PassengerTripSheetDetent.resting;
  static const snapSizes = <double>[0.18, 0.34, 0.86];
  static const snapAnimationDuration = VanRidesMotionTokens.sheetSnap;
  static const programmaticAnimationDuration =
      VanRidesMotionTokens.sheetProgrammatic;

  double get snapSize {
    return switch (this) {
      PassengerTripSheetDetent.peek => snapSizes[0],
      PassengerTripSheetDetent.resting => snapSizes[1],
      PassengerTripSheetDetent.expanded => snapSizes[2],
    };
  }

  String get semanticLabel {
    return switch (this) {
      PassengerTripSheetDetent.peek => 'Resumo minimo da viagem',
      PassengerTripSheetDetent.resting => 'Resumo da viagem',
      PassengerTripSheetDetent.expanded => 'Detalhes da viagem expandidos',
    };
  }

  static PassengerTripSheetDetent fromExtent(double extent) {
    if (extent < (snapSizes[0] + snapSizes[1]) / 2) {
      return PassengerTripSheetDetent.peek;
    }
    if (extent < (snapSizes[1] + snapSizes[2]) / 2) {
      return PassengerTripSheetDetent.resting;
    }
    return PassengerTripSheetDetent.expanded;
  }

  PassengerTripSheetDetent expand() {
    return switch (this) {
      PassengerTripSheetDetent.peek => PassengerTripSheetDetent.resting,
      PassengerTripSheetDetent.resting => PassengerTripSheetDetent.expanded,
      PassengerTripSheetDetent.expanded => PassengerTripSheetDetent.expanded,
    };
  }

  PassengerTripSheetDetent collapse() {
    return switch (this) {
      PassengerTripSheetDetent.peek => PassengerTripSheetDetent.peek,
      PassengerTripSheetDetent.resting => PassengerTripSheetDetent.peek,
      PassengerTripSheetDetent.expanded => PassengerTripSheetDetent.resting,
    };
  }

  PassengerTripSheetDetent toggleByChevron() {
    return switch (this) {
      PassengerTripSheetDetent.peek => PassengerTripSheetDetent.resting,
      PassengerTripSheetDetent.resting => PassengerTripSheetDetent.expanded,
      PassengerTripSheetDetent.expanded => PassengerTripSheetDetent.resting,
    };
  }
}
