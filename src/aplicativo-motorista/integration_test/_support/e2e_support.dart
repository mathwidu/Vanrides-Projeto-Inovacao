import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

const bool kVanRidesE2eMode =
    bool.fromEnvironment('VANRIDES_E2E_TEST_MODE', defaultValue: false);

Future<void> pumpUntil(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 30),
  Duration step = const Duration(milliseconds: 250),
}) async {
  final end = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(end)) {
    await tester.pump(step);
    if (finder.evaluate().isNotEmpty) return;
  }
  throw TestFailure('Timeout esperando por: $finder');
}

Future<void> pumpUntilGone(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 30),
  Duration step = const Duration(milliseconds: 250),
}) async {
  final end = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(end)) {
    await tester.pump(step);
    if (finder.evaluate().isEmpty) return;
  }
  throw TestFailure('Timeout esperando sumir: $finder');
}

Future<void> back(WidgetTester tester) async {
  await tester.binding.handlePopRoute();
  await tester.pumpAndSettle();
}

Future<void> scrollIntoView(
  WidgetTester tester,
  Finder finder, {
  double delta = 300,
}) async {
  final scrollableAncestor = find.ancestor(of: finder, matching: find.byType(Scrollable));
  final scrollable =
      scrollableAncestor.evaluate().isNotEmpty ? scrollableAncestor.first : find.byType(Scrollable).first;

  await tester.scrollUntilVisible(
    finder,
    delta,
    scrollable: scrollable,
  );
  await tester.pumpAndSettle();
}

Future<void> dismissKeyboard(WidgetTester tester) async {
  // Toca em um ponto "neutro" para tirar o foco de inputs e evitar teclado
  // cobrindo botões (flaky em iOS Simulator).
  await tester.tapAt(const Offset(1, 1));
  await tester.pumpAndSettle();
}

Future<void> selectDropdownItemByText(
  WidgetTester tester,
  Finder dropdown,
  String itemText,
) async {
  await tester.tap(dropdown);
  await tester.pumpAndSettle();
  await pumpUntil(tester, find.text(itemText));
  await tester.tap(find.text(itemText).last);
  await tester.pumpAndSettle();
}

String uniqueEmail(String prefix) =>
    '$prefix.${DateTime.now().millisecondsSinceEpoch}@example.com';
