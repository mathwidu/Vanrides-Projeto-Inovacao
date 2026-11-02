import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_surface.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_password_change.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';

class ChangeAccountPasswordSheet extends StatefulWidget {
  const ChangeAccountPasswordSheet({super.key, required this.onSave});

  final Future<void> Function(AccountPasswordChange change) onSave;

  @override
  State<ChangeAccountPasswordSheet> createState() =>
      _ChangeAccountPasswordSheetState();
}

class _ChangeAccountPasswordSheetState
    extends State<ChangeAccountPasswordSheet> {
  final _formKey = GlobalKey<FormState>();
  final _currentController = TextEditingController();
  final _newController = TextEditingController();
  final _confirmController = TextEditingController();
  final _status = ValueNotifier(const _PasswordSheetStatus());

  @override
  void dispose() {
    _currentController.dispose();
    _newController.dispose();
    _confirmController.dispose();
    _status.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || _status.value.saving) {
      return;
    }

    _status.value = const _PasswordSheetStatus(saving: true);
    try {
      await widget.onSave(
        AccountPasswordChange(
          senhaAtual: _currentController.text,
          novaSenha: _newController.text,
        ),
      );
      if (mounted) {
        Navigator.of(context).pop();
      }
    } catch (error) {
      if (mounted) {
        _status.value = _PasswordSheetStatus(error: error.toString());
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottomInset),
      child: VanRidesLiquidSurface(
        variant: VanRidesLiquidSurfaceVariant.sheet,
        borderRadius: 28,
        padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
        child: SafeArea(
          top: false,
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Center(
                  child: Container(
                    width: 48,
                    height: 5,
                    decoration: BoxDecoration(
                      color: VanRidesMapTokens.textMuted.withValues(
                        alpha: 0.72,
                      ),
                      borderRadius: BorderRadius.circular(999),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                Text(
                  'Alterar senha',
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  'Use uma senha com pelo menos 8 caracteres para manter sua conta protegida.',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: VanRidesMapTokens.textSecondary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                ValueListenableBuilder<_PasswordSheetStatus>(
                  valueListenable: _status,
                  builder: (context, status, _) {
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        if (status.error != null) ...[
                          const SizedBox(height: 14),
                          _PasswordErrorMessage(message: status.error!),
                        ],
                        const SizedBox(height: 18),
                        _PasswordField(
                          fieldKey: const Key('account.password.currentField'),
                          controller: _currentController,
                          label: 'Senha atual',
                          validator: _requiredPassword,
                        ),
                        const SizedBox(height: 12),
                        _PasswordField(
                          fieldKey: const Key('account.password.newField'),
                          controller: _newController,
                          label: 'Nova senha',
                          validator: _newPassword,
                        ),
                        const SizedBox(height: 12),
                        _PasswordField(
                          fieldKey: const Key('account.password.confirmField'),
                          controller: _confirmController,
                          label: 'Confirmar nova senha',
                          validator: _confirmPassword,
                        ),
                        const SizedBox(height: 18),
                        FilledButton.icon(
                          key: const Key('account.password.saveButton'),
                          onPressed: status.saving ? null : _submit,
                          icon:
                              status.saving
                                  ? const SizedBox.square(
                                    dimension: 16,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                  : const Icon(Icons.lock_reset_rounded),
                          label: Text(
                            status.saving ? 'Alterando...' : 'Salvar senha',
                          ),
                          style: FilledButton.styleFrom(
                            minimumSize: const Size.fromHeight(52),
                            backgroundColor: VanRidesMapTokens.blueTracking,
                            foregroundColor: VanRidesMapTokens.backgroundDeep,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(18),
                            ),
                          ),
                        ),
                      ],
                    );
                  },
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  String? _requiredPassword(String? value) {
    return (value ?? '').trim().isEmpty ? 'Informe a senha atual.' : null;
  }

  String? _newPassword(String? value) {
    final normalized = (value ?? '').trim();
    if (normalized.length < 8) {
      return 'A nova senha precisa ter pelo menos 8 caracteres.';
    }
    return null;
  }

  String? _confirmPassword(String? value) {
    if ((value ?? '').trim() != _newController.text.trim()) {
      return 'As senhas não conferem.';
    }
    return null;
  }
}

class _PasswordSheetStatus {
  const _PasswordSheetStatus({this.saving = false, this.error});

  final bool saving;
  final String? error;
}

class _PasswordErrorMessage extends StatelessWidget {
  const _PasswordErrorMessage({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('account.password.errorMessage'),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.redAccent.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.redAccent.withValues(alpha: 0.34)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.error_outline_rounded,
            color: Colors.redAccent,
            size: 20,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PasswordField extends StatelessWidget {
  const _PasswordField({
    required this.fieldKey,
    required this.controller,
    required this.label,
    required this.validator,
  });

  final Key fieldKey;
  final TextEditingController controller;
  final String label;
  final String? Function(String?) validator;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      key: fieldKey,
      controller: controller,
      obscureText: true,
      validator: validator,
      style: const TextStyle(
        color: MapFirstPalette.textStrong,
        fontWeight: FontWeight.w700,
      ),
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: const Icon(Icons.lock_outline_rounded),
      ),
    );
  }
}
