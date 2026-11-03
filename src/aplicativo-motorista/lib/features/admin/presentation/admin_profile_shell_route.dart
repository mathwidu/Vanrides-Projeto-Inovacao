import 'package:flutter/material.dart';

import '../../account/data/account_profile_service.dart';
import '../domain/admin_portal_session.dart';
import 'admin_shell_screen.dart';

typedef AdminProfileGatewayBuilder =
    AccountProfileGateway Function(AdminPortalSession session, String senha);

Route<void> buildAdminShellRoute({
  required AdminPortalSession session,
  required String senha,
  AdminProfileGatewayBuilder? accountProfileGatewayBuilder,
}) {
  final accountProfileGateway =
      accountProfileGatewayBuilder?.call(session, senha) ??
      AccountProfileService(
        email: session.email,
        senha: senha,
        accessToken: session.accessToken,
      );

  return MaterialPageRoute(
    builder:
        (_) => AdminShellScreen(
          session: session,
          accountProfileGateway: accountProfileGateway,
        ),
  );
}
