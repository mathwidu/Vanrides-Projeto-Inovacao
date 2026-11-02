import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/account/presentation/account_role_label.dart';

void main() {
  test('normaliza papeis de conta para rótulos em português', () {
    expect(
      presentAccountRoleLabel(tipoUsuario: 'Passenger', papel: 'PASSENGER'),
      'Passageiro',
    );
    expect(
      presentAccountRoleLabel(tipoUsuario: 'Driver', papel: 'DRIVER'),
      'Motorista',
    );
    expect(
      presentAccountRoleLabel(tipoUsuario: 'Guardian', papel: 'RESPONSAVEL'),
      'Responsável',
    );
    expect(
      presentAccountRoleLabel(tipoUsuario: 'Company_Admin'),
      'Administrador',
    );
  });

  test('preserva fallback quando papel é desconhecido', () {
    expect(
      presentAccountRoleLabel(tipoUsuario: '', papel: '', fallback: 'Conta'),
      'Conta',
    );
  });
}
