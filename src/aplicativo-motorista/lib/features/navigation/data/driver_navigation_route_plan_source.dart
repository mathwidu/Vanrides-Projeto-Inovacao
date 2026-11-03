import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_route_plan_contract.dart';

abstract class DriverNavigationRoutePlanSnapshotSource {
  DriverNavigationRoutePlanResponse? snapshotForTrip(DriverTripRecord trip);
}

class NoopDriverNavigationRoutePlanSnapshotSource
    implements DriverNavigationRoutePlanSnapshotSource {
  const NoopDriverNavigationRoutePlanSnapshotSource();

  @override
  DriverNavigationRoutePlanResponse? snapshotForTrip(DriverTripRecord trip) {
    return null;
  }
}

class DriverTripNavigationRoutePlanSnapshotSource
    implements DriverNavigationRoutePlanSnapshotSource {
  const DriverTripNavigationRoutePlanSnapshotSource();

  @override
  DriverNavigationRoutePlanResponse? snapshotForTrip(DriverTripRecord trip) {
    return trip.navigationRoutePlan;
  }
}
