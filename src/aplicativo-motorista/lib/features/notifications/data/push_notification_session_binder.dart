import 'noop_push_notification_gateway.dart';
import 'push_notification_gateway.dart';
import 'push_notification_registration_service.dart';

/// Ponto único onde a app tenta registrar push token após login.
/// A chamada pós-login é passiva: consulta permissão já concedida e nunca abre
/// o prompt do sistema. O opt-in explícito fica para a tela de preferências.
class PushNotificationSessionBinder {
  PushNotificationSessionBinder({
    PushNotificationGateway? pushGateway,
    PushNotificationRegistrationGateway? registrationGateway,
  }) : _service = PushNotificationRegistrationService(
         pushGateway: pushGateway ?? NoopPushNotificationGateway(),
         registrationGateway:
             registrationGateway ?? PushNotificationRegistrationHttpGateway(),
       );

  final PushNotificationRegistrationService _service;

  /// Chamar logo após login bem-sucedido.
  /// Retorna sem bloquear o fluxo de login mesmo em caso de falha.
  Future<void> bindSession({required String authorizationHeader}) async {
    try {
      await _service.setupAndRegister(authorizationHeader: authorizationHeader);
    } catch (_) {
      // Erros já são logados pelas implementações internas.
      // Aqui apenas garantimos que não propagam para o login.
    }
  }
}
