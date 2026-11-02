import '../data/driver_tracking_platform.dart';

/// One immutable snapshot keeps operation controls and native status consistent.
class DriverTrackingViewState {
  const DriverTrackingViewState({
    this.status = const DriverTrackingStatus(),
    this.busy = false,
    this.starting = false,
    this.stopping = false,
    this.editingCredential = false,
    this.error,
  });

  final DriverTrackingStatus status;
  final bool busy;
  final bool starting;
  final bool stopping;
  final bool editingCredential;
  final String? error;

  DriverTrackingViewState copyWith({
    DriverTrackingStatus? status,
    bool? busy,
    bool? starting,
    bool? stopping,
    bool? editingCredential,
    String? error,
    bool clearError = false,
  }) => DriverTrackingViewState(
    status: status ?? this.status,
    busy: busy ?? this.busy,
    starting: starting ?? this.starting,
    stopping: stopping ?? this.stopping,
    editingCredential: editingCredential ?? this.editingCredential,
    error: clearError ? null : error ?? this.error,
  );
}
