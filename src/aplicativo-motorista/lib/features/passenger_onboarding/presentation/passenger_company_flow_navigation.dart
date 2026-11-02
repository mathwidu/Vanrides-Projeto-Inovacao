import 'package:flutter/widgets.dart';

import '../../../app/session/app_session_user.dart';
import '../data/passenger_onboarding_service.dart';
import '../domain/passenger_onboarding_models.dart';

typedef PassengerOnboardingStatusNavigation =
    Future<void> Function(
      BuildContext context, {
      required AppSessionUser sessionUser,
      required PassengerOnboardingGateway gateway,
      required PassengerOnboardingStatusRecord status,
    });

Future<void> noopPassengerOnboardingStatusNavigation(
  BuildContext context, {
  required AppSessionUser sessionUser,
  required PassengerOnboardingGateway gateway,
  required PassengerOnboardingStatusRecord status,
}) async {}
