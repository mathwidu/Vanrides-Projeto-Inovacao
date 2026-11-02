import '../_support/flows.dart';
import '../guardian/guardian_mvp_flow_test.dart' as guardian;

import 'operational_fixture_flow_test.dart' as operational_fixture;

void main() {
  ensureBinding();
  guardian.main();
  operational_fixture.main();
}
