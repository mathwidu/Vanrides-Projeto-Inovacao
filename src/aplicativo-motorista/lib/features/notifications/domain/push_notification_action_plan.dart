enum PushNotificationRole {
  passenger(label: 'Passageiro'),
  guardian(label: 'Responsável'),
  driver(label: 'Motorista'),
  company(label: 'Empresa');

  const PushNotificationRole({required this.label});

  final String label;
}

enum PushNotificationBackendEvent {
  tripStatusChanged(backendName: 'TRIP_STATUS_CHANGED'),
  stopStatusChanged(backendName: 'STOP_STATUS_CHANGED'),
  presenceStatusChanged(backendName: 'PRESENCE_STATUS_CHANGED'),
  supportTicketMessageCreated(backendName: 'SUPPORT_TICKET_MESSAGE_CREATED');

  const PushNotificationBackendEvent({required this.backendName});

  final String backendName;
}

enum PushNotificationPreferenceCategory {
  trip(label: 'Viagem'),
  presence(label: 'Presença'),
  support(label: 'Atendimento'),
  operation(label: 'Operação');

  const PushNotificationPreferenceCategory({required this.label});

  final String label;
}

class PlannedPushNotification {
  const PlannedPushNotification({
    required this.role,
    required this.backendEvent,
    required this.category,
    required this.title,
    required this.body,
    required this.actionLabel,
    required this.destinationLabel,
    required this.essential,
  });

  final PushNotificationRole role;
  final PushNotificationBackendEvent backendEvent;
  final PushNotificationPreferenceCategory category;
  final String title;
  final String body;
  final String actionLabel;
  final String destinationLabel;
  final bool essential;
}

class PushNotificationActionPlan {
  const PushNotificationActionPlan._();

  static const notifications = <PlannedPushNotification>[
    PlannedPushNotification(
      role: PushNotificationRole.passenger,
      backendEvent: PushNotificationBackendEvent.tripStatusChanged,
      category: PushNotificationPreferenceCategory.trip,
      title: 'Van iniciou a viagem',
      body: 'Abrimos o mapa da corrida com a van e a sua próxima parada.',
      actionLabel: 'Acompanhar rota',
      destinationLabel: 'Viagem em andamento',
      essential: true,
    ),
    PlannedPushNotification(
      role: PushNotificationRole.guardian,
      backendEvent: PushNotificationBackendEvent.stopStatusChanged,
      category: PushNotificationPreferenceCategory.trip,
      title: 'Van perto da parada',
      body: 'Mostramos somente a pessoa acompanhada e o estado da parada dela.',
      actionLabel: 'Ver acompanhamento',
      destinationLabel: 'Acompanhamento do responsável',
      essential: true,
    ),
    PlannedPushNotification(
      role: PushNotificationRole.driver,
      backendEvent: PushNotificationBackendEvent.stopStatusChanged,
      category: PushNotificationPreferenceCategory.operation,
      title: 'Próxima parada atualizada',
      body: 'Destacamos a parada e a ação esperada sem interromper a direção.',
      actionLabel: 'Abrir rota',
      destinationLabel: 'Corrida do motorista',
      essential: true,
    ),
    PlannedPushNotification(
      role: PushNotificationRole.passenger,
      backendEvent: PushNotificationBackendEvent.presenceStatusChanged,
      category: PushNotificationPreferenceCategory.presence,
      title: 'Presença precisa de decisão',
      body: 'Pedimos confirmar ou avisar ausência uma vez, sem repetição.',
      actionLabel: 'Responder presença',
      destinationLabel: 'Confirmação de presença',
      essential: false,
    ),
    PlannedPushNotification(
      role: PushNotificationRole.company,
      backendEvent: PushNotificationBackendEvent.supportTicketMessageCreated,
      category: PushNotificationPreferenceCategory.support,
      title: 'Novo retorno no atendimento',
      body: 'Levamos direto ao ticket para responder sem procurar conversa.',
      actionLabel: 'Abrir atendimento',
      destinationLabel: 'Atendimento',
      essential: false,
    ),
  ];

  static Set<String> get backendEventNames => {
    for (final notification in notifications)
      notification.backendEvent.backendName,
  };
}
