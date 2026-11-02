import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_surface.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_status_pill.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_password_change.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile_update.dart';
import 'package:vanrides_frontend_dev/features/account/presentation/account_role_label.dart';
import 'package:vanrides_frontend_dev/features/account/presentation/change_account_password_sheet.dart';
import 'package:vanrides_frontend_dev/features/account/presentation/edit_account_profile_sheet.dart';
import 'package:vanrides_frontend_dev/features/notifications/presentation/push_notification_preferences_card.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';
import 'package:vanrides_frontend_dev/services/api_constants.dart';

part 'account_profile_screen_components.dart';
part 'account_profile_completion_card.dart';

typedef AccountProfileRoleSectionsBuilder =
    List<Widget> Function(BuildContext context, AccountProfile? profile);

class AccountProfileScreen extends StatefulWidget {
  const AccountProfileScreen({
    super.key,
    required this.surfaceKey,
    required this.editButtonKey,
    required this.logoutButtonKey,
    required this.fallbackName,
    required this.fallbackEmail,
    required this.roleFallback,
    required this.avatarFallback,
    required this.onLogout,
    this.roleIcon = Icons.person_outline_rounded,
    this.enabled = false,
    this.accountProfileGateway,
    this.photoPicker,
    this.roleSectionsBuilder,
  });

  final Key surfaceKey;
  final Key editButtonKey;
  final Key logoutButtonKey;
  final String fallbackName;
  final String fallbackEmail;
  final String roleFallback;
  final String avatarFallback;
  final IconData roleIcon;
  final bool enabled;
  final AccountProfileGateway? accountProfileGateway;
  final Future<String?> Function()? photoPicker;
  final AccountProfileRoleSectionsBuilder? roleSectionsBuilder;
  final VoidCallback onLogout;

  @override
  State<AccountProfileScreen> createState() => _AccountProfileScreenState();
}

class _AccountProfileScreenState extends State<AccountProfileScreen> {
  AccountProfile? _profile;
  bool _loading = false;
  bool _saving = false;
  bool _uploadingPhoto = false;
  bool _changingPassword = false;
  String? _error;
  String? _success;

  void _updateState(VoidCallback callback) {
    if (mounted) setState(callback);
  }

  @override
  void initState() {
    super.initState();
    if (widget.enabled) {
      _loadProfile();
    }
  }

  @override
  void didUpdateWidget(AccountProfileScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    final gatewayChanged =
        oldWidget.accountProfileGateway != widget.accountProfileGateway;
    if ((!oldWidget.enabled && widget.enabled) || gatewayChanged) {
      _loadProfile(force: gatewayChanged);
    }
  }

  Future<void> _loadProfile({bool force = false}) async {
    final gateway = widget.accountProfileGateway;
    if (gateway == null || _loading || (_profile != null && !force)) {
      return;
    }

    _updateState(() {
      _loading = true;
      _error = null;
      _success = null;
    });

    try {
      final profile = await gateway.fetchMe();
      if (!mounted) {
        return;
      }
      _updateState(() {
        _profile = profile;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() {
        _loading = false;
        _error = _profileErrorMessage(
          error,
          fallback: 'Não foi possível atualizar os dados da conta.',
        );
      });
    }
  }

  Future<void> _openEditProfile() async {
    final gateway = widget.accountProfileGateway;
    if (gateway is! AccountProfileEditableGateway) {
      _updateState(() {
        _error = 'Perfil real indisponível para edição nesta sessão.';
        _success = null;
      });
      return;
    }

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) {
        return EditAccountProfileSheet(
          initialName: _profile?.nome ?? widget.fallbackName,
          initialPhone: _profile?.telefone ?? '',
          initialBirthDate: _profile?.dataNascimento,
          onSave: _saveProfile,
        );
      },
    );
  }

  Future<void> _saveProfile(AccountProfileUpdate update) async {
    final gateway = widget.accountProfileGateway;
    final editableGateway =
        gateway is AccountProfileEditableGateway
            ? gateway as AccountProfileEditableGateway
            : null;
    if (editableGateway == null) {
      throw const AccountProfileException(
        'Perfil real indisponível para edição nesta sessão.',
      );
    }

    _updateState(() {
      _saving = true;
      _error = null;
      _success = null;
    });

    try {
      final updated = await editableGateway.updateProfile(update);
      if (!mounted) {
        return;
      }
      _updateState(() {
        _profile = updated;
        _success = 'Perfil atualizado com sucesso.';
      });
      _showFeedbackSnackBar('Perfil atualizado com sucesso.');
    } catch (error) {
      if (mounted) {
        _updateState(() {
          _error = _profileErrorMessage(
            error,
            fallback: 'Não foi possível salvar os dados da conta.',
          );
        });
      }
      rethrow;
    } finally {
      if (mounted) {
        _updateState(() => _saving = false);
      }
    }
  }

  Future<void> _updatePhoto() async {
    final gateway = widget.accountProfileGateway;
    final editableGateway =
        gateway is AccountProfileEditableGateway
            ? gateway as AccountProfileEditableGateway
            : null;
    if (editableGateway == null || _uploadingPhoto) {
      _updateState(() {
        _error = 'Perfil real indisponível para edição nesta sessão.';
        _success = null;
      });
      return;
    }

    try {
      final picker =
          widget.photoPicker ??
          AccountProfilePhotoCodec().pickProfilePhotoDataUri;
      final photoDataUri = await picker();
      if (photoDataUri == null || !mounted) {
        return;
      }

      _updateState(() {
        _uploadingPhoto = true;
        _error = null;
        _success = null;
      });

      final updated = await editableGateway.updateProfile(
        AccountProfileUpdate(fotoPerfilUrl: photoDataUri),
      );
      if (!mounted) {
        return;
      }
      _updateState(() {
        _profile = updated;
        _success = 'Foto atualizada com sucesso.';
      });
      _showFeedbackSnackBar('Foto atualizada com sucesso.');
    } on AccountProfilePhotoException catch (error) {
      if (mounted) {
        _updateState(() => _error = error.message);
      }
    } catch (error) {
      if (mounted) {
        _updateState(() {
          _error = _profileErrorMessage(
            error,
            fallback: 'Não foi possível atualizar a foto da conta.',
          );
        });
      }
    } finally {
      if (mounted) {
        _updateState(() => _uploadingPhoto = false);
      }
    }
  }

  Future<void> _openChangePassword() async {
    final gateway = widget.accountProfileGateway;
    if (gateway is! AccountPasswordGateway) {
      _updateState(() {
        _error = 'Alteração de senha indisponível nesta sessão.';
        _success = null;
      });
      return;
    }

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) {
        return ChangeAccountPasswordSheet(onSave: _changePassword);
      },
    );
  }

  Future<void> _changePassword(AccountPasswordChange change) async {
    final gateway = widget.accountProfileGateway;
    final passwordGateway =
        gateway is AccountPasswordGateway
            ? gateway as AccountPasswordGateway
            : null;
    if (passwordGateway == null) {
      throw const AccountProfileException(
        'Alteração de senha indisponível nesta sessão.',
      );
    }

    _updateState(() {
      _changingPassword = true;
      _error = null;
      _success = null;
    });

    try {
      await passwordGateway.changePassword(change);
      if (!mounted) {
        return;
      }
      _updateState(() => _success = 'Senha alterada com sucesso.');
      _showFeedbackSnackBar('Senha alterada com sucesso.');
    } finally {
      if (mounted) {
        _updateState(() => _changingPassword = false);
      }
    }
  }

  void _showFeedbackSnackBar(String message) {
    try {
      ScaffoldMessenger.maybeOf(
        context,
      )?.showSnackBar(SnackBar(content: Text(message)));
    } on AssertionError {
      // The profile can be rendered in tests or embedded shells without a
      // descendant Scaffold. The inline sync state remains the primary feedback.
    }
  }

  @override
  Widget build(BuildContext context) {
    final displayName = _profile?.nome ?? widget.fallbackName;
    final displayEmail = _profile?.email ?? widget.fallbackEmail;
    final displayRole = presentAccountRoleLabel(
      tipoUsuario: _profile?.tipoUsuario,
      papel: _profile?.papel,
      fallback: widget.roleFallback,
    );
    final canEdit =
        widget.accountProfileGateway is AccountProfileEditableGateway;
    final canChangePassword =
        widget.accountProfileGateway is AccountPasswordGateway;
    final roleSections =
        widget.roleSectionsBuilder?.call(context, _profile) ?? const <Widget>[];
    final requiredProfileItems = _requiredProfileItems(_profile);
    final recommendedProfileItems = _recommendedProfileItems(_profile);
    final profileLoaded = _profile != null;

    return Container(
      key: widget.surfaceKey,
      color: VanRidesMapTokens.backgroundDeep,
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 188),
          children: [
            _AccountProfileHeader(
              name: displayName,
              email: displayEmail,
              role: displayRole,
              roleIcon: widget.roleIcon,
              photoUrl: _profile?.fotoPerfilUrl,
              avatarFallback: widget.avatarFallback,
              editButtonKey: widget.editButtonKey,
              editEnabled: canEdit && !_saving,
              photoEnabled: canEdit && !_uploadingPhoto,
              uploadingPhoto: _uploadingPhoto,
              emailVerified: _profile?.emailVerified,
              onEdit: _openEditProfile,
              onPhoto: _updatePhoto,
            ),
            if (_loading ||
                _saving ||
                _uploadingPhoto ||
                _changingPassword ||
                _error != null ||
                _success != null) ...[
              const SizedBox(height: 16),
              _AccountProfileSyncState(
                loading: _loading,
                saving: _saving || _changingPassword,
                uploadingPhoto: _uploadingPhoto,
                error: _error,
                success: _success,
                onRetry: () => _loadProfile(force: true),
              ),
            ],
            if (requiredProfileItems.isNotEmpty ||
                recommendedProfileItems.isNotEmpty) ...[
              const SizedBox(height: 18),
              _AccountProfileCompletionCard(
                requiredItems: requiredProfileItems,
                recommendedItems: recommendedProfileItems,
                canEdit: canEdit && !_saving,
                onEdit: _openEditProfile,
              ),
            ],
            const SizedBox(height: 18),
            AccountProfileInfoCard(
              children: [
                AccountProfileInfoRow(
                  icon: Icons.mail_outline_rounded,
                  title: 'E-mail',
                  value: displayEmail,
                ),
                AccountProfileInfoRow(
                  icon: Icons.phone_outlined,
                  title: 'Telefone',
                  value: _profile?.telefone ?? 'Não informado',
                  helper:
                      profileLoaded && _profile?.telefone == null
                          ? 'Toque em Editar perfil para completar.'
                          : null,
                  actionRequired: profileLoaded && _profile?.telefone == null,
                ),
                AccountProfileInfoRow(
                  icon: Icons.cake_outlined,
                  title: 'Nascimento',
                  value: formatAccountProfileDateBr(_profile?.dataNascimento),
                  helper:
                      profileLoaded && _profile?.dataNascimento == null
                          ? _birthDateHelperText(_profile)
                          : null,
                  actionRequired:
                      profileLoaded && _profile?.dataNascimento == null,
                ),
                if (_profile?.menorDeIdade != null)
                  AccountProfileInfoRow(
                    icon: Icons.family_restroom_outlined,
                    title: 'Responsabilidade',
                    value:
                        _profile!.menorDeIdade!
                            ? 'Passageiro menor de idade'
                            : 'Passageiro adulto',
                  ),
              ],
            ),
            for (final section in roleSections) ...[
              const SizedBox(height: 16),
              section,
            ],
            const SizedBox(height: 16),
            _AccountProfileSecurityCard(
              canChangePassword: canChangePassword && !_changingPassword,
              onChangePassword: _openChangePassword,
            ),
            const SizedBox(height: 16),
            const PushNotificationPreferencesCard(),
            const SizedBox(height: 24),
            FilledButton.icon(
              key: widget.logoutButtonKey,
              onPressed: widget.onLogout,
              icon: const Icon(Icons.logout_rounded),
              label: const Text('Sair da conta'),
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(56),
                backgroundColor: VanRidesMapTokens.surfaceRaised,
                foregroundColor: VanRidesMapTokens.textPrimary,
                elevation: 0,
                side: BorderSide(
                  color: VanRidesMapTokens.border.withValues(alpha: 0.42),
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(18),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

List<String> _requiredProfileItems(AccountProfile? profile) {
  if (profile == null) {
    return const [];
  }

  final items = <String>[];
  if ((profile.telefone ?? '').trim().isEmpty) {
    items.add('Telefone obrigatório');
  }
  if ((profile.dataNascimento ?? '').trim().isEmpty) {
    items.add('Nascimento obrigatório');
  }
  return items;
}

List<String> _recommendedProfileItems(AccountProfile? profile) {
  if (profile == null) {
    return const [];
  }

  final items = <String>[];
  if ((profile.fotoPerfilUrl ?? '').trim().isEmpty) {
    items.add('Foto recomendada');
  }
  return items;
}

String _birthDateHelperText(AccountProfile? profile) {
  final role = '${profile?.tipoUsuario ?? ''} ${profile?.papel ?? ''}';
  if (role.toLowerCase().contains('passageiro')) {
    return 'Ajuda a identificar passageiros menores de idade.';
  }
  return 'Ajuda a manter responsabilidade e segurança da conta.';
}
