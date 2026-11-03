enum DriverNavigationQueuedActionType { completeStop, skipStop, noShow }

enum DriverNavigationQueuedActionStatus { pending }

class DriverNavigationQueuedAction {
  const DriverNavigationQueuedAction({
    required this.tripId,
    required this.stopId,
    required this.stopName,
    required this.type,
    required this.createdAt,
    this.status = DriverNavigationQueuedActionStatus.pending,
  });

  final int tripId;
  final int stopId;
  final String stopName;
  final DriverNavigationQueuedActionType type;
  final DateTime createdAt;
  final DriverNavigationQueuedActionStatus status;

  String get id => '$tripId:$stopId:${type.storageKey}';

  String get label => type.label;

  Map<String, dynamic> toJson() {
    return {
      'tripId': tripId,
      'stopId': stopId,
      'stopName': stopName,
      'type': type.storageKey,
      'createdAt': createdAt.toIso8601String(),
      'status': status.storageKey,
    };
  }

  factory DriverNavigationQueuedAction.fromJson(Map<String, dynamic> json) {
    return DriverNavigationQueuedAction(
      tripId: _requiredInt(json, 'tripId'),
      stopId: _requiredInt(json, 'stopId'),
      stopName: _requiredString(json, 'stopName'),
      type: driverNavigationQueuedActionTypeFromStorageKey(
        _requiredString(json, 'type'),
      ),
      createdAt: DateTime.parse(_requiredString(json, 'createdAt')),
      status: driverNavigationQueuedActionStatusFromStorageKey(
        _requiredString(json, 'status'),
      ),
    );
  }

  bool matches({
    required int tripId,
    required int stopId,
    DriverNavigationQueuedActionType? type,
  }) {
    return this.tripId == tripId &&
        this.stopId == stopId &&
        (type == null || this.type == type);
  }
}

class DriverNavigationActionQueue {
  const DriverNavigationActionQueue.empty() : _actions = const [];

  const DriverNavigationActionQueue._(this._actions);

  factory DriverNavigationActionQueue({
    required Iterable<DriverNavigationQueuedAction> actions,
  }) {
    return DriverNavigationActionQueue._(List.unmodifiable(actions));
  }

  factory DriverNavigationActionQueue.fromJson(Map<String, dynamic> json) {
    final actions = json['actions'];
    if (actions is! List<dynamic>) {
      throw const FormatException('Fila local de navegacao invalida.');
    }
    return DriverNavigationActionQueue(
      actions: actions.whereType<Map<String, dynamic>>().map(
        DriverNavigationQueuedAction.fromJson,
      ),
    );
  }

  final List<DriverNavigationQueuedAction> _actions;

  List<DriverNavigationQueuedAction> get actions => _actions;

  Map<String, dynamic> toJson() {
    return {'actions': _actions.map((action) => action.toJson()).toList()};
  }

  int get pendingCount =>
      _actions
          .where(
            (action) =>
                action.status == DriverNavigationQueuedActionStatus.pending,
          )
          .length;

  DriverNavigationActionQueueSummary? get pendingSummary {
    final pendingActions = _actions
        .where(
          (action) =>
              action.status == DriverNavigationQueuedActionStatus.pending,
        )
        .toList(growable: false);
    final count = pendingActions.length;
    if (pendingActions.isEmpty) {
      return null;
    }
    final firstAction = pendingActions.first;
    return DriverNavigationActionQueueSummary(
      pendingCount: count,
      statusLabel:
          count == 1
              ? '1 acao local pendente'
              : '$count acoes locais pendentes',
      detailLabel:
          count == 1
              ? '${firstAction.label} de ${firstAction.stopName}'
              : 'Proxima: ${firstAction.label} de ${firstAction.stopName}',
      helperText:
          count == 1
              ? 'Sera enviada quando a conexao estiver segura.'
              : 'Serao enviadas quando a conexao estiver segura.',
    );
  }

  bool hasPendingForStop({required int tripId, required int stopId}) {
    return _actions.any(
      (action) =>
          action.status == DriverNavigationQueuedActionStatus.pending &&
          action.matches(tripId: tripId, stopId: stopId),
    );
  }

  DriverNavigationActionQueue enqueue({
    required int tripId,
    required int stopId,
    required String stopName,
    required DriverNavigationQueuedActionType type,
    required DateTime createdAt,
  }) {
    final alreadyQueued = _actions.any(
      (action) => action.matches(tripId: tripId, stopId: stopId, type: type),
    );
    if (alreadyQueued) {
      return this;
    }

    return DriverNavigationActionQueue(
      actions: [
        ..._actions,
        DriverNavigationQueuedAction(
          tripId: tripId,
          stopId: stopId,
          stopName: stopName,
          type: type,
          createdAt: createdAt,
        ),
      ],
    );
  }

  DriverNavigationActionQueue removeAction(String actionId) {
    return DriverNavigationActionQueue(
      actions: _actions.where((action) => action.id != actionId),
    );
  }
}

class DriverNavigationActionQueueSummary {
  const DriverNavigationActionQueueSummary({
    required this.pendingCount,
    required this.statusLabel,
    required this.detailLabel,
    required this.helperText,
  });

  final int pendingCount;
  final String statusLabel;
  final String detailLabel;
  final String helperText;
}

extension DriverNavigationQueuedActionTypeCopy
    on DriverNavigationQueuedActionType {
  String get storageKey {
    return switch (this) {
      DriverNavigationQueuedActionType.completeStop => 'completeStop',
      DriverNavigationQueuedActionType.skipStop => 'skipStop',
      DriverNavigationQueuedActionType.noShow => 'noShow',
    };
  }

  String get label {
    return switch (this) {
      DriverNavigationQueuedActionType.completeStop => 'Confirmar embarque',
      DriverNavigationQueuedActionType.skipStop => 'Pular',
      DriverNavigationQueuedActionType.noShow => 'Faltou',
    };
  }
}

extension DriverNavigationQueuedActionStatusCopy
    on DriverNavigationQueuedActionStatus {
  String get storageKey {
    return switch (this) {
      DriverNavigationQueuedActionStatus.pending => 'pending',
    };
  }
}

DriverNavigationQueuedActionType driverNavigationQueuedActionTypeFromStorageKey(
  String value,
) {
  return switch (value) {
    'completeStop' => DriverNavigationQueuedActionType.completeStop,
    'skipStop' => DriverNavigationQueuedActionType.skipStop,
    'noShow' => DriverNavigationQueuedActionType.noShow,
    _ => throw FormatException('Tipo de acao de navegacao invalido: $value'),
  };
}

DriverNavigationQueuedActionStatus
driverNavigationQueuedActionStatusFromStorageKey(String value) {
  return switch (value) {
    'pending' => DriverNavigationQueuedActionStatus.pending,
    _ => throw FormatException('Status de acao de navegacao invalido: $value'),
  };
}

int _requiredInt(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is int) {
    return value;
  }
  throw FormatException('Campo inteiro obrigatorio ausente: $key');
}

String _requiredString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String && value.trim().isNotEmpty) {
    return value;
  }
  throw FormatException('Campo texto obrigatorio ausente: $key');
}
