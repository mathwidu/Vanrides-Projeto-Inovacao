part of 'account_profile_screen.dart';

class _AccountProfileHeader extends StatelessWidget {
  const _AccountProfileHeader({
    required this.name,
    required this.email,
    required this.role,
    required this.roleIcon,
    required this.photoUrl,
    required this.avatarFallback,
    required this.editButtonKey,
    required this.editEnabled,
    required this.photoEnabled,
    required this.uploadingPhoto,
    required this.emailVerified,
    required this.onEdit,
    required this.onPhoto,
  });

  final String name;
  final String email;
  final String role;
  final IconData roleIcon;
  final String? photoUrl;
  final String avatarFallback;
  final Key editButtonKey;
  final bool editEnabled;
  final bool photoEnabled;
  final bool uploadingPhoto;
  final bool? emailVerified;
  final VoidCallback onEdit;
  final VoidCallback onPhoto;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Stack(
          clipBehavior: Clip.none,
          children: [
            AccountProfileAvatar(
              name: name,
              photoUrl: photoUrl,
              fallbackLabel: avatarFallback,
              size: 104,
            ),
            Positioned(
              right: -4,
              bottom: -4,
              child: Semantics(
                button: true,
                label: uploadingPhoto ? 'Enviando foto' : 'Trocar foto',
                child: IconButton.filled(
                  key: const Key('account.profile.photoButton'),
                  tooltip: uploadingPhoto ? 'Enviando foto' : 'Trocar foto',
                  onPressed: photoEnabled ? onPhoto : null,
                  icon:
                      uploadingPhoto
                          ? const SizedBox.square(
                            dimension: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                          : const Icon(Icons.add_a_photo_outlined, size: 19),
                  style: IconButton.styleFrom(
                    backgroundColor: VanRidesMapTokens.blueTracking,
                    foregroundColor: VanRidesMapTokens.backgroundDeep,
                    disabledBackgroundColor: VanRidesMapTokens.surfaceRaised,
                    disabledForegroundColor: VanRidesMapTokens.textMuted,
                  ),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          name.trim().isEmpty ? avatarFallback : name,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
            fontWeight: FontWeight.w800,
            color: MapFirstPalette.textStrong,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          email,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
            color: VanRidesMapTokens.textSecondary,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 12),
        Wrap(
          alignment: WrapAlignment.center,
          spacing: 10,
          runSpacing: 8,
          children: [
            VanRidesStatusPill(
              icon: roleIcon,
              label: role,
              tone: VanRidesStatusTone.tracking,
            ),
            if (emailVerified != null)
              VanRidesStatusPill(
                icon:
                    emailVerified!
                        ? Icons.verified_rounded
                        : Icons.mark_email_unread_outlined,
                label:
                    emailVerified!
                        ? 'E-mail verificado'
                        : 'Verificação pendente',
                tone:
                    emailVerified!
                        ? VanRidesStatusTone.success
                        : VanRidesStatusTone.warning,
              ),
          ],
        ),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          key: editButtonKey,
          onPressed: editEnabled ? onEdit : null,
          icon: const Icon(Icons.edit_rounded),
          label: const Text('Editar perfil'),
          style: OutlinedButton.styleFrom(
            foregroundColor: VanRidesMapTokens.textPrimary,
            backgroundColor: VanRidesMapTokens.surfaceRaised.withValues(
              alpha: 0.86,
            ),
            side: BorderSide(
              color: VanRidesMapTokens.border.withValues(alpha: 0.42),
            ),
            minimumSize: const Size.fromHeight(48),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(18),
            ),
          ),
        ),
      ],
    );
  }
}

class AccountProfileAvatar extends StatelessWidget {
  const AccountProfileAvatar({
    super.key,
    required this.name,
    required this.fallbackLabel,
    this.photoUrl,
    this.size = 96,
  });

  final String name;
  final String fallbackLabel;
  final String? photoUrl;
  final double size;

  @override
  Widget build(BuildContext context) {
    final image = _profileImage(photoUrl);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [
            VanRidesMapTokens.blueTracking,
            VanRidesMapTokens.greenAction,
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(size * 0.26),
        boxShadow: const [
          BoxShadow(
            color: Color(0x1A2D6CDF),
            blurRadius: 28,
            offset: Offset(0, 14),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      alignment: Alignment.center,
      child:
          image ??
          Text(
            _initials(name, fallbackLabel),
            key: const Key('account.profile.avatarInitials'),
            style: Theme.of(context).textTheme.displaySmall?.copyWith(
              color: VanRidesMapTokens.backgroundDeep,
              fontWeight: FontWeight.w800,
            ),
          ),
    );
  }

  Widget? _profileImage(String? value) {
    final normalized = value?.trim();
    if (normalized == null || normalized.isEmpty) {
      return null;
    }

    final bytes = _tryDecodeDataUri(normalized);
    if (bytes != null) {
      return Image.memory(
        bytes,
        key: const Key('account.profile.avatarImage'),
        width: size,
        height: size,
        fit: BoxFit.cover,
        gaplessPlayback: true,
      );
    }

    final uri = Uri.tryParse(normalized);
    final networkUrl = _networkImageUrl(normalized, uri);
    if (networkUrl != null) {
      return Image.network(
        networkUrl,
        key: const Key('account.profile.avatarImage'),
        width: size,
        height: size,
        fit: BoxFit.cover,
        errorBuilder:
            (context, error, stackTrace) => Text(
              _initials(name, fallbackLabel),
              key: const Key('account.profile.avatarInitials'),
              style: Theme.of(context).textTheme.displaySmall?.copyWith(
                color: VanRidesMapTokens.backgroundDeep,
                fontWeight: FontWeight.w800,
              ),
            ),
      );
    }

    return null;
  }

  String? _networkImageUrl(String value, Uri? uri) {
    if (uri != null &&
        (uri.scheme == 'https' || uri.scheme == 'http') &&
        uri.host.isNotEmpty) {
      return value;
    }
    if (value.startsWith('/')) {
      return Uri.parse(baseUrl).resolve(value).toString();
    }
    return null;
  }

  Uint8List? _tryDecodeDataUri(String value) {
    if (!value.startsWith('data:image/')) {
      return null;
    }

    final commaIndex = value.indexOf(',');
    if (commaIndex < 1) {
      return null;
    }

    try {
      return base64Decode(value.substring(commaIndex + 1));
    } on FormatException {
      return null;
    }
  }

  String _initials(String name, String fallback) {
    final parts =
        name
            .trim()
            .split(RegExp(r'\s+'))
            .where((part) => part.isNotEmpty)
            .toList();
    if (parts.isEmpty) {
      return fallback;
    }
    if (parts.length == 1) {
      return parts.first.substring(0, 1).toUpperCase();
    }
    return '${parts.first.substring(0, 1)}${parts.last.substring(0, 1)}'
        .toUpperCase();
  }
}

class AccountProfileInfoCard extends StatelessWidget {
  const AccountProfileInfoCard({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var index = 0; index < children.length; index++) ...[
            if (index > 0) const SizedBox(height: 16),
            children[index],
          ],
        ],
      ),
    );
  }
}

class _AccountProfileSecurityCard extends StatelessWidget {
  const _AccountProfileSecurityCard({
    required this.canChangePassword,
    required this.onChangePassword,
  });

  final bool canChangePassword;
  final VoidCallback onChangePassword;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface(
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 460;
          final copy = Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Segurança da conta',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: MapFirstPalette.textStrong,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'Senha, e-mail verificado e acesso ficam separados dos dados operacionais.',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: VanRidesMapTokens.textSecondary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          );
          final button = OutlinedButton.icon(
            key: const Key('account.profile.changePasswordButton'),
            onPressed: canChangePassword ? onChangePassword : null,
            icon: const Icon(Icons.lock_reset_rounded),
            label: const Text('Alterar senha'),
            style: OutlinedButton.styleFrom(
              foregroundColor: VanRidesMapTokens.textPrimary,
              backgroundColor: VanRidesMapTokens.surfaceRaised.withValues(
                alpha: 0.86,
              ),
              side: BorderSide(
                color: VanRidesMapTokens.border.withValues(alpha: 0.42),
              ),
              minimumSize: Size(compact ? double.infinity : 170, 48),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
            ),
          );

          if (compact) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [copy, const SizedBox(height: 12), button],
            );
          }

          return Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Expanded(child: copy),
              const SizedBox(width: 14),
              button,
            ],
          );
        },
      ),
    );
  }
}

class AccountProfileInfoRow extends StatelessWidget {
  const AccountProfileInfoRow({
    super.key,
    required this.icon,
    required this.title,
    required this.value,
    this.helper,
    this.actionRequired = false,
  });

  final IconData icon;
  final String title;
  final String value;
  final String? helper;
  final bool actionRequired;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color:
                actionRequired
                    ? VanRidesMapTokens.amberWarning.withValues(alpha: 0.16)
                    : MapFirstPalette.blueSoft,
            shape: BoxShape.circle,
          ),
          alignment: Alignment.center,
          child: Icon(
            icon,
            color:
                actionRequired
                    ? VanRidesMapTokens.amberWarning
                    : MapFirstPalette.brandBlue,
            size: 20,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: MapFirstPalette.textMuted,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                value,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: MapFirstPalette.textStrong,
                  fontWeight: FontWeight.w800,
                ),
              ),
              if (helper != null) ...[
                const SizedBox(height: 4),
                Text(
                  helper!,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: VanRidesMapTokens.textMuted,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _AccountProfileSyncState extends StatelessWidget {
  const _AccountProfileSyncState({
    required this.loading,
    required this.saving,
    required this.uploadingPhoto,
    required this.error,
    required this.success,
    required this.onRetry,
  });

  final bool loading;
  final bool saving;
  final bool uploadingPhoto;
  final String? error;
  final String? success;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final message =
        saving
            ? 'Salvando perfil...'
            : uploadingPhoto
            ? 'Enviando foto...'
            : loading
            ? 'Atualizando dados da conta...'
            : error ?? success ?? 'Dados da conta atualizados.';
    final isBusy = loading || saving || uploadingPhoto;

    return VanRidesLiquidSurface(
      child: Row(
        children: [
          if (isBusy)
            const SizedBox.square(
              dimension: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          else
            Icon(
              error == null
                  ? Icons.check_circle_outline_rounded
                  : Icons.info_outline_rounded,
              color:
                  error == null
                      ? VanRidesMapTokens.greenAction
                      : VanRidesMapTokens.textMuted,
              size: 20,
            ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              message,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: VanRidesMapTokens.textSecondary,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          if (!isBusy && error != null)
            IconButton(
              tooltip: 'Tentar novamente',
              onPressed: onRetry,
              icon: const Icon(Icons.refresh_rounded),
              color: VanRidesMapTokens.blueTracking,
            ),
        ],
      ),
    );
  }
}

String _profileErrorMessage(Object error, {required String fallback}) {
  if (error is AccountProfileException) {
    return error.message;
  }
  return fallback;
}
