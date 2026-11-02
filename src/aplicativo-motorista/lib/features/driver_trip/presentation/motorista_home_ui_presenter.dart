import 'package:flutter/foundation.dart';

import 'widgets/driver_trip_stop_widgets.dart';

class MotoristaHomeUiState {
  const MotoristaHomeUiState({
    required this.editingRoute,
    required this.savingRoute,
    required this.openingNavigation,
    required this.runningLifecycleAction,
    required this.currentTabIndex,
    required this.stopFilter,
  });

  const MotoristaHomeUiState.initial()
    : editingRoute = false,
      savingRoute = false,
      openingNavigation = false,
      runningLifecycleAction = false,
      currentTabIndex = 0,
      stopFilter = DriverStopFilter.all;

  final bool editingRoute;
  final bool savingRoute;
  final bool openingNavigation;
  final bool runningLifecycleAction;
  final int currentTabIndex;
  final DriverStopFilter stopFilter;

  MotoristaHomeUiState copyWith({
    bool? editingRoute,
    bool? savingRoute,
    bool? openingNavigation,
    bool? runningLifecycleAction,
    int? currentTabIndex,
    DriverStopFilter? stopFilter,
  }) {
    return MotoristaHomeUiState(
      editingRoute: editingRoute ?? this.editingRoute,
      savingRoute: savingRoute ?? this.savingRoute,
      openingNavigation: openingNavigation ?? this.openingNavigation,
      runningLifecycleAction:
          runningLifecycleAction ?? this.runningLifecycleAction,
      currentTabIndex: currentTabIndex ?? this.currentTabIndex,
      stopFilter: stopFilter ?? this.stopFilter,
    );
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MotoristaHomeUiState &&
            other.editingRoute == editingRoute &&
            other.savingRoute == savingRoute &&
            other.openingNavigation == openingNavigation &&
            other.runningLifecycleAction == runningLifecycleAction &&
            other.currentTabIndex == currentTabIndex &&
            other.stopFilter == stopFilter;
  }

  @override
  int get hashCode => Object.hash(
    editingRoute,
    savingRoute,
    openingNavigation,
    runningLifecycleAction,
    currentTabIndex,
    stopFilter,
  );
}

class MotoristaHomeUiPresenter extends ChangeNotifier {
  MotoristaHomeUiState _state = const MotoristaHomeUiState.initial();

  MotoristaHomeUiState get state => _state;

  void startRouteEditing() {
    _emit(_state.copyWith(editingRoute: true));
  }

  void finishRouteEditing() {
    _emit(_state.copyWith(editingRoute: false));
  }

  void setSavingRoute(bool savingRoute) {
    _emit(_state.copyWith(savingRoute: savingRoute));
  }

  void setOpeningNavigation(bool openingNavigation) {
    _emit(_state.copyWith(openingNavigation: openingNavigation));
  }

  bool startLifecycleAction() {
    if (_state.runningLifecycleAction) {
      return false;
    }
    _emit(_state.copyWith(runningLifecycleAction: true));
    return true;
  }

  void finishLifecycleAction() {
    _emit(_state.copyWith(runningLifecycleAction: false));
  }

  void selectTab(int currentTabIndex) {
    _emit(_state.copyWith(currentTabIndex: currentTabIndex));
  }

  void selectStopFilter(DriverStopFilter stopFilter) {
    _emit(_state.copyWith(stopFilter: stopFilter));
  }

  void _emit(MotoristaHomeUiState nextState) {
    if (nextState == _state) {
      return;
    }
    _state = nextState;
    notifyListeners();
  }
}
