import '_support/flows.dart';

import 'guardian/all.dart' as guardian;
import 'motorista/all.dart' as motorista;
import 'passageiro/all.dart' as passageiro;

void main() {
  ensureBinding();
  guardian.main();
  motorista.main();
  passageiro.main();
}
