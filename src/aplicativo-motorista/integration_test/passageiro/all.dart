import '../_support/flows.dart';

import 'cadastro_test.dart' as cadastro;
import 'login_test.dart' as login;
import 'confirmar_presenca_test.dart' as presencas;
import 'localizacao_tempo_real_test.dart' as localizacao;

void main() {
  ensureBinding();
  cadastro.registerPassageiroCadastroTests();
  login.registerPassageiroLoginTests();
  presencas.registerPassageiroConfirmarPresencaTests();
  localizacao.registerPassageiroLocalizacaoTempoRealTests();
}
