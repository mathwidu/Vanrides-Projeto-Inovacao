import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Future<void> expandGuardianTripSheet(
  WidgetTester tester, {
  bool revealDetails = false,
}) async {
  await tester.tap(find.byKey(const Key('passenger.trip.sheet.chevron')));
  await tester.pumpAndSettle();
  if (!revealDetails) {
    return;
  }
  await tester.drag(
    find.byKey(const Key('guardian.trip.sheet')),
    const Offset(0, -260),
  );
  await tester.pumpAndSettle();
}
