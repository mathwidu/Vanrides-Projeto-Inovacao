import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_login_hero.dart';

void main() {
  testWidgets('AdminLoginHero renderiza conteudo operacional do portal', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1000, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SizedBox(width: 760, height: 860, child: AdminLoginHero()),
        ),
      ),
    );

    expect(find.text('VanRides'), findsOneWidget);
    expect(find.text('Portal operacional da empresa'), findsOneWidget);
    expect(
      find.textContaining('Gerencie rotas, viagens, passageiros e vínculos'),
      findsOneWidget,
    );
    expect(find.text('Rotas-base'), findsOneWidget);
    expect(find.text('Viagens do dia'), findsOneWidget);
    expect(find.text('Equipe e frota'), findsOneWidget);
    expect(find.text('Segurança por contrato'), findsOneWidget);
    expect(find.text('Bootstrap do primeiro admin'), findsOneWidget);
  });

  testWidgets('AdminLoginHero renderiza em layout scrollado estreito', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(390, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: Column(
              children: [
                AdminLoginHero(),
                SizedBox(height: 24),
                Text('Formulario vem depois'),
              ],
            ),
          ),
        ),
      ),
    );

    expect(find.text('Portal operacional da empresa'), findsOneWidget);
    expect(find.text('Formulario vem depois'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
