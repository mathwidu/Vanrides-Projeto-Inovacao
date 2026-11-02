import '../_support/flows.dart';

import 'cadastro_test.dart' as cadastro;
import 'login_test.dart' as login;
import 'compartilhar_localizacao_test.dart' as localizacao;
import 'presencas_hoje_test.dart' as presencas;

void main() {
  ensureBinding();
  cadastro.registerMotoristaCadastroTests();
  login.registerMotoristaLoginTests();
  localizacao.registerMotoristaCompartilharLocalizacaoTests();
  presencas.registerMotoristaPresencasHojeTests();
}
