import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';

enum DriverNavigationOfflineState { ready, missing, outdated, partial, failed }

enum DriverNavigationCoverageStatus {
  validated,
  observation,
  fallback,
  blocked,
}

enum DriverNavigationFallbackReason {
  routeNotValidated,
  routingProviderUnavailable,
  offlinePackMissing,
  offlinePackOutdated,
  offlinePackPartial,
  offlinePackFailed,
  instructionMissing,
  unknown,
}

enum DriverNavigationMode { internal, safeMode, externalFallback }

enum DriverNavigationSecondaryActionType { skipStop, noShow }

class DriverNavigationSecondaryAction {
  const DriverNavigationSecondaryAction({
    required this.type,
    required this.label,
  });

  final DriverNavigationSecondaryActionType type;
  final String label;
}

class DriverNavigationInstruction {
  const DriverNavigationInstruction({
    required this.id,
    required this.text,
    required this.voiceText,
    this.distanceMeters,
  });

  final String id;
  final String text;
  final String voiceText;
  final int? distanceMeters;
}

class DriverNavigationSession {
  const DriverNavigationSession({
    required this.trip,
    required this.offlineState,
    required this.coverageStatus,
    this.currentInstruction,
    this.actionQueueSummary,
    this.fallbackReason,
    this.offRoute = false,
  });

  final DriverTripRecord trip;
  final DriverNavigationInstruction? currentInstruction;
  final DriverNavigationActionQueueSummary? actionQueueSummary;
  final DriverNavigationFallbackReason? fallbackReason;
  final DriverNavigationOfflineState offlineState;
  final DriverNavigationCoverageStatus coverageStatus;
  final bool offRoute;
}

class DriverNavigationViewModel {
  const DriverNavigationViewModel({
    required this.mode,
    required this.instructionText,
    required this.distanceLabel,
    required this.helperText,
    required this.nextStopLabel,
    required this.nextStopName,
    required this.nextStopSummary,
    required this.primaryActionLabel,
    this.secondaryActions = const [],
    required this.fallbackLabel,
    required this.statusLabel,
    required this.fallbackIsPrimary,
    required this.canUseInternalNavigation,
    this.voicePrompt,
    this.instructionWasNormalized = false,
    this.queuedActionSummaryLabel,
    this.queuedActionDetailLabel,
    this.queuedActionHelperText,
  });

  final DriverNavigationMode mode;
  final String instructionText;
  final String distanceLabel;
  final String helperText;
  final String nextStopLabel;
  final String nextStopName;
  final String nextStopSummary;
  final String primaryActionLabel;
  final List<DriverNavigationSecondaryAction> secondaryActions;
  final String fallbackLabel;
  final String statusLabel;
  final String? voicePrompt;
  final String? queuedActionSummaryLabel;
  final String? queuedActionDetailLabel;
  final String? queuedActionHelperText;
  final bool instructionWasNormalized;
  final bool fallbackIsPrimary;
  final bool canUseInternalNavigation;
}
