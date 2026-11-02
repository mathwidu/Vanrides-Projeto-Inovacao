import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/motorista_home_ui_presenter.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_trip_stop_widgets.dart';

void main() {
  test('controla flags visuais, aba e filtro do fluxo motorista', () {
    final presenter = MotoristaHomeUiPresenter();
    var notifications = 0;
    presenter.addListener(() => notifications++);

    expect(presenter.state.editingRoute, isFalse);
    expect(presenter.state.savingRoute, isFalse);
    expect(presenter.state.openingNavigation, isFalse);
    expect(presenter.state.runningLifecycleAction, isFalse);
    expect(presenter.state.currentTabIndex, 0);
    expect(presenter.state.stopFilter, DriverStopFilter.all);

    presenter.startRouteEditing();
    presenter.setSavingRoute(true);
    presenter.setOpeningNavigation(true);

    expect(presenter.state.editingRoute, isTrue);
    expect(presenter.state.savingRoute, isTrue);
    expect(presenter.state.openingNavigation, isTrue);

    expect(presenter.startLifecycleAction(), isTrue);
    expect(presenter.startLifecycleAction(), isFalse);
    expect(presenter.state.runningLifecycleAction, isTrue);

    presenter.selectTab(2);
    presenter.selectStopFilter(DriverStopFilter.pending);

    expect(presenter.state.currentTabIndex, 2);
    expect(presenter.state.stopFilter, DriverStopFilter.pending);

    presenter.finishLifecycleAction();
    presenter.finishRouteEditing();
    presenter.setSavingRoute(false);
    presenter.setOpeningNavigation(false);

    expect(presenter.state.editingRoute, isFalse);
    expect(presenter.state.savingRoute, isFalse);
    expect(presenter.state.openingNavigation, isFalse);
    expect(presenter.state.runningLifecycleAction, isFalse);
    expect(notifications, 10);
  });
}
