import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_trip_sheet_detent.dart';

void main() {
  test('detents expose stable snap sizes and transitions', () {
    expect(PassengerTripSheetDetent.initial, PassengerTripSheetDetent.resting);
    expect(PassengerTripSheetDetent.peek.snapSize, closeTo(0.18, 0.001));
    expect(PassengerTripSheetDetent.resting.snapSize, closeTo(0.34, 0.001));
    expect(PassengerTripSheetDetent.expanded.snapSize, closeTo(0.86, 0.001));
    expect(PassengerTripSheetDetent.snapSizes, const [0.18, 0.34, 0.86]);

    expect(
      PassengerTripSheetDetent.peek.expand(),
      PassengerTripSheetDetent.resting,
    );
    expect(
      PassengerTripSheetDetent.resting.expand(),
      PassengerTripSheetDetent.expanded,
    );
    expect(
      PassengerTripSheetDetent.expanded.expand(),
      PassengerTripSheetDetent.expanded,
    );

    expect(
      PassengerTripSheetDetent.expanded.collapse(),
      PassengerTripSheetDetent.resting,
    );
    expect(
      PassengerTripSheetDetent.resting.collapse(),
      PassengerTripSheetDetent.peek,
    );
    expect(
      PassengerTripSheetDetent.peek.collapse(),
      PassengerTripSheetDetent.peek,
    );
  });

  test('detents resolve from extent', () {
    expect(
      PassengerTripSheetDetent.fromExtent(0.17),
      PassengerTripSheetDetent.peek,
    );
    expect(
      PassengerTripSheetDetent.fromExtent(0.35),
      PassengerTripSheetDetent.resting,
    );
    expect(
      PassengerTripSheetDetent.fromExtent(0.72),
      PassengerTripSheetDetent.expanded,
    );
  });

  test('detents provide accessible labels', () {
    expect(
      PassengerTripSheetDetent.peek.semanticLabel,
      'Resumo minimo da viagem',
    );
    expect(PassengerTripSheetDetent.resting.semanticLabel, 'Resumo da viagem');
    expect(
      PassengerTripSheetDetent.expanded.semanticLabel,
      'Detalhes da viagem expandidos',
    );
  });
}
