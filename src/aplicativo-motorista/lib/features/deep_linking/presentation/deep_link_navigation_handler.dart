import 'package:flutter/material.dart';

import '../../../app/session/app_user_role.dart';
import '../../../app/session/app_session_user.dart';
import '../../../features/guardian/domain/guardian_session.dart';
import '../../../features/guardian/presentation/guardian_localizacao_screen.dart';
import '../../../features/driver_onboarding/presentation/driver_invite_screen.dart';
import '../../../features/passenger_onboarding/presentation/passenger_company_link_flow.dart';
import '../../../features/tracking/data/trip_tracking_service.dart';
import '../../../features/tracking/presentation/passenger_trip_details_screen.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/home_passageiro_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';
import '../../../l10n/app_strings.dart';
import '../data/deep_link_resolver.dart';
import '../domain/deep_link_models.dart';

typedef DriverViagemScreenBuilder =
    Widget Function(AppSessionUser session, ViagemDeepLink target);
typedef PassengerViagemScreenBuilder =
    Widget Function(AppSessionUser session, ViagemDeepLink target);
typedef GuardianViagemScreenBuilder =
    Widget Function(AppSessionUser session, ViagemDeepLink target);
typedef PassengerRastreioScreenBuilder =
    Widget Function(AppSessionUser session, RastreioDeepLink target);
typedef GuardianRastreioScreenBuilder =
    Widget Function(AppSessionUser session, RastreioDeepLink target);
typedef ConviteScreenBuilder =
    Widget Function(AppSessionUser? session, ConviteDeepLink target);
typedef DriverInviteScreenBuilder =
    Widget Function(AppSessionUser? session, DriverInviteDeepLink target);

/// Recebe uma DeepLinkResolution e navega usando o NavigatorKey global.
/// Desacoplado do app shell: basta injetar o navigatorKey e o provider de sessão.
class DeepLinkNavigationHandler {
  DeepLinkNavigationHandler({
    required GlobalKey<NavigatorState> navigatorKey,
    required AppSessionUser? Function() currentSession,
    DriverViagemScreenBuilder? driverViagemScreenBuilder,
    PassengerViagemScreenBuilder? passengerViagemScreenBuilder,
    GuardianViagemScreenBuilder? guardianViagemScreenBuilder,
    PassengerRastreioScreenBuilder? passengerRastreioScreenBuilder,
    GuardianRastreioScreenBuilder? guardianRastreioScreenBuilder,
    ConviteScreenBuilder? conviteScreenBuilder,
    DriverInviteScreenBuilder? driverInviteScreenBuilder,
  }) : _navigatorKey = navigatorKey,
       _currentSession = currentSession,
       _driverViagemScreenBuilder = driverViagemScreenBuilder,
       _passengerViagemScreenBuilder = passengerViagemScreenBuilder,
       _guardianViagemScreenBuilder = guardianViagemScreenBuilder,
       _passengerRastreioScreenBuilder = passengerRastreioScreenBuilder,
       _guardianRastreioScreenBuilder = guardianRastreioScreenBuilder,
       _conviteScreenBuilder = conviteScreenBuilder,
       _driverInviteScreenBuilder = driverInviteScreenBuilder;

  final GlobalKey<NavigatorState> _navigatorKey;
  final AppSessionUser? Function() _currentSession;
  final DriverViagemScreenBuilder? _driverViagemScreenBuilder;
  final PassengerViagemScreenBuilder? _passengerViagemScreenBuilder;
  final GuardianViagemScreenBuilder? _guardianViagemScreenBuilder;
  final PassengerRastreioScreenBuilder? _passengerRastreioScreenBuilder;
  final GuardianRastreioScreenBuilder? _guardianRastreioScreenBuilder;
  final ConviteScreenBuilder? _conviteScreenBuilder;
  final DriverInviteScreenBuilder? _driverInviteScreenBuilder;

  void handle(DeepLinkResolution resolution) {
    try {
      final session = _currentSession();
      final navigator = _navigatorKey.currentState;
      if (navigator == null) {
        debugPrint('[deep-link] navigator indisponível; evento ignorado.');
        return;
      }

      switch (resolution) {
        case DeepLinkAllowed(target: final target):
          _navigateTo(navigator, target, session);
          return;
        case DeepLinkRoleMismatch():
          _showSnackBar(AppStrings.deepLink.papelInvalido);
          return;
        case DeepLinkUnsupported():
          _showSnackBar(AppStrings.deepLink.linkDesconhecido);
          return;
      }
    } catch (error, stackTrace) {
      debugPrint('[deep-link] erro ao tratar resolução: $error');
      debugPrintStack(stackTrace: stackTrace);
    }
  }

  void _navigateTo(
    NavigatorState navigator,
    DeepLinkTarget target,
    AppSessionUser? session,
  ) {
    switch (target) {
      case ViagemDeepLink():
        _handleViagem(navigator, target, session);
        return;
      case RastreioDeepLink():
        _handleRastreio(navigator, target, session);
        return;
      case ConviteDeepLink():
        _handleConvite(navigator, target, session);
        return;
      case DriverInviteDeepLink():
        _handleDriverInvite(navigator, target, session);
        return;
      case UnknownDeepLink():
        _showSnackBar(AppStrings.deepLink.linkDesconhecido);
        return;
    }
  }

  void _handleViagem(
    NavigatorState navigator,
    ViagemDeepLink target,
    AppSessionUser? session,
  ) {
    if (session == null) {
      _showSnackBar(AppStrings.deepLink.papelInvalido);
      return;
    }

    switch (session.role) {
      case AppUserRole.driver:
        _pushScreen(
          navigator,
          (_driverViagemScreenBuilder ?? _buildDriverViagemScreen)(
            session,
            target,
          ),
        );
        return;
      case AppUserRole.passenger:
        _pushScreen(
          navigator,
          (_passengerViagemScreenBuilder ?? _buildPassengerViagemScreen)(
            session,
            target,
          ),
        );
        return;
      case AppUserRole.guardian:
        if (session.accessToken == null || session.companyId == null) {
          _showSnackBar(AppStrings.deepLink.sessaoGuardianSemContexto);
          return;
        }
        _pushScreen(
          navigator,
          (_guardianViagemScreenBuilder ?? _buildGuardianViagemScreen)(
            session,
            target,
          ),
        );
        return;
      case AppUserRole.companyAdmin:
      case AppUserRole.unknown:
        _showSnackBar(AppStrings.deepLink.papelInvalido);
        return;
    }
  }

  void _handleRastreio(
    NavigatorState navigator,
    RastreioDeepLink target,
    AppSessionUser? session,
  ) {
    if (session == null) {
      _showSnackBar(AppStrings.deepLink.papelInvalido);
      return;
    }

    switch (session.role) {
      case AppUserRole.passenger:
        _pushScreen(
          navigator,
          (_passengerRastreioScreenBuilder ?? _buildPassengerRastreioScreen)(
            session,
            target,
          ),
        );
        return;
      case AppUserRole.guardian:
        if (session.accessToken == null || session.companyId == null) {
          _showSnackBar(AppStrings.deepLink.sessaoGuardianSemContexto);
          return;
        }
        _pushScreen(
          navigator,
          (_guardianRastreioScreenBuilder ?? _buildGuardianRastreioScreen)(
            session,
            target,
          ),
        );
        return;
      case AppUserRole.driver:
        _showSnackBar(
          AppStrings.deepLink.rastreioDisponivelParaPassageiroOuResponsavel,
        );
        return;
      case AppUserRole.companyAdmin:
      case AppUserRole.unknown:
        _showSnackBar(AppStrings.deepLink.papelInvalido);
        return;
    }
  }

  void _handleConvite(
    NavigatorState navigator,
    ConviteDeepLink target,
    AppSessionUser? session,
  ) {
    _pushScreen(
      navigator,
      (_conviteScreenBuilder ?? _buildConviteScreen)(session, target),
    );
  }

  void _handleDriverInvite(
    NavigatorState navigator,
    DriverInviteDeepLink target,
    AppSessionUser? session,
  ) {
    _pushScreen(
      navigator,
      (_driverInviteScreenBuilder ?? _buildDriverInviteScreen)(session, target),
    );
  }

  Widget _buildDriverViagemScreen(
    AppSessionUser session,
    ViagemDeepLink target,
  ) {
    return HomeMotoristaScreen(
      nome: session.displayName,
      motoristaId: session.userId,
      email: session.email,
      senha: session.password,
      accessToken: session.accessToken,
      initialTripId: target.tripId,
    );
  }

  Widget _buildPassengerViagemScreen(
    AppSessionUser session,
    ViagemDeepLink target,
  ) {
    return HomePassageiroScreen(
      nome: session.displayName,
      idPassageiro: session.userId,
      motoristaId: session.motoristaId,
      email: session.email,
      senha: session.password,
      accessToken: session.accessToken,
      initialTripId: target.tripId,
    );
  }

  Widget _buildGuardianViagemScreen(
    AppSessionUser session,
    ViagemDeepLink target,
  ) {
    return GuardianLocalizacaoScreen(
      session: GuardianSession(
        guardianId: session.userId,
        guardianName: session.displayName,
        companyId: session.companyId!,
        email: session.email,
        accessToken: session.accessToken!,
        backendRoleLabel: session.backendRoleLabel,
      ),
      initialTripId: target.tripId,
    );
  }

  Widget _buildPassengerRastreioScreen(
    AppSessionUser session,
    RastreioDeepLink target,
  ) {
    return PassengerTripDetailsScreen.fromSession(
      passageiroId: target.passageiroId,
      email: session.email,
      senha: session.password,
      accessToken: session.accessToken,
      trackingGateway: TripTrackingService(
        email: session.email,
        senha: session.password,
        accessToken: session.accessToken,
      ),
    );
  }

  Widget _buildGuardianRastreioScreen(
    AppSessionUser session,
    RastreioDeepLink target,
  ) {
    return GuardianLocalizacaoScreen(
      session: GuardianSession(
        guardianId: session.userId,
        guardianName: session.displayName,
        companyId: session.companyId!,
        email: session.email,
        accessToken: session.accessToken!,
        backendRoleLabel: session.backendRoleLabel,
      ),
      initialPassageiroId: target.passageiroId,
    );
  }

  Widget _buildConviteScreen(AppSessionUser? session, ConviteDeepLink target) {
    if (session != null && session.role == AppUserRole.passenger) {
      return PassengerInviteCodeScreen(
        sessionUser: session,
        initialCode: target.codigo,
        onStatusNavigation: handlePassengerCompanyStatusNavigation,
      );
    }

    return LoginScreen(initialInviteCode: target.codigo);
  }

  Widget _buildDriverInviteScreen(
    AppSessionUser? session,
    DriverInviteDeepLink target,
  ) {
    return DriverInviteScreen(token: target.token, sessionUser: session);
  }

  void _pushScreen(NavigatorState navigator, Widget screen) {
    navigator.push(MaterialPageRoute<void>(builder: (_) => screen));
  }

  void _showSnackBar(String message) {
    final context = _navigatorKey.currentContext;
    if (context == null) {
      debugPrint('[deep-link] contexto indisponível para snackbar.');
      return;
    }

    final messenger = ScaffoldMessenger.maybeOf(context);
    if (messenger == null) {
      debugPrint('[deep-link] ScaffoldMessenger indisponível.');
      return;
    }

    messenger.showSnackBar(SnackBar(content: Text(message)));
  }
}
