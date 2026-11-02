import 'app_strings_admin.dart';
import 'app_strings_deep_link.dart';
import 'app_strings_guardian.dart';
import 'app_strings_motorista.dart';
import 'app_strings_network.dart';
import 'app_strings_passageiro.dart';

abstract final class AppStrings {
  static const admin = AdminStrings();
  static const motorista = MotoristaStrings();
  static const passageiro = PassageiroStrings();
  static const guardian = GuardianStrings();
  static const network = NetworkStrings();
  static const deepLink = DeepLinkStrings();
}
