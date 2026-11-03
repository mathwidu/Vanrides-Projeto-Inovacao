import 'package:flutter/material.dart';

import '../../data/admin_portal_auth_service.dart';
import '../../domain/admin_company_admin_bootstrap_input.dart';
import '../../domain/admin_company_admin_bootstrap_result.dart';
import '../../domain/admin_portal_exception.dart';
import 'admin_portal_layout.dart';

class AdminLoginBootstrapResult {
  const AdminLoginBootstrapResult({
    required this.bootstrap,
    required this.password,
  });

  final AdminCompanyAdminBootstrapResult bootstrap;
  final String password;
}

class AdminLoginBootstrapDialog extends StatefulWidget {
  const AdminLoginBootstrapDialog({super.key, required this.gateway});

  static const companyNameFieldKey = Key('admin.login.bootstrap.companyName');
  static const adminNameFieldKey = Key('admin.login.bootstrap.adminName');
  static const emailFieldKey = Key('admin.login.bootstrap.email');
  static const phoneFieldKey = Key('admin.login.bootstrap.phone');
  static const passwordFieldKey = Key('admin.login.bootstrap.password');
  static const createButtonKey = Key('admin.login.bootstrap.create');

  final AdminPortalAuthGateway gateway;

  @override
  State<AdminLoginBootstrapDialog> createState() =>
      _AdminLoginBootstrapDialogState();
}

class _AdminLoginBootstrapDialogState extends State<AdminLoginBootstrapDialog> {
  final _formKey = GlobalKey<FormState>();
  final _companyNameController = TextEditingController();
  final _nomeController = TextEditingController();
  final _emailController = TextEditingController();
  final _telefoneController = TextEditingController();
  final _senhaController = TextEditingController();
  bool _submitting = false;
  String? _errorMessage;

  @override
  void dispose() {
    _companyNameController.dispose();
    _nomeController.dispose();
    _emailController.dispose();
    _telefoneController.dispose();
    _senhaController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() {
      _submitting = true;
      _errorMessage = null;
    });

    try {
      final password = _senhaController.text;
      final result = await widget.gateway.bootstrap(
        AdminCompanyAdminBootstrapInput(
          companyName: _companyNameController.text.trim(),
          nome: _nomeController.text.trim(),
          email: _emailController.text.trim().toLowerCase(),
          telefone: _telefoneController.text.trim(),
          senha: password,
        ),
      );

      if (!mounted) {
        return;
      }

      Navigator.of(
        context,
      ).pop(AdminLoginBootstrapResult(bootstrap: result, password: password));
    } on AdminPortalException catch (error) {
      setState(() {
        _errorMessage = error.message;
        _submitting = false;
      });
    } catch (_) {
      setState(() {
        _errorMessage = 'Nao foi possivel criar o primeiro admin agora.';
        _submitting = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.sizeOf(context);
    final compact = screenSize.width < 760;

    return Dialog(
      insetPadding: const EdgeInsets.all(24),
      backgroundColor: Colors.transparent,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: 920,
          maxHeight: screenSize.height - 48,
        ),
        child: AdminPortalSurface(
          radius: 36,
          padding: EdgeInsets.zero,
          child: Form(
            key: _formKey,
            child: SingleChildScrollView(
              child: Padding(
                padding: EdgeInsets.fromLTRB(
                  compact ? 22 : 30,
                  compact ? 22 : 30,
                  compact ? 22 : 30,
                  24,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const _BootstrapHeader(),
                    const SizedBox(height: 24),
                    const Wrap(
                      spacing: 12,
                      runSpacing: 12,
                      children: [
                        _BootstrapStep(
                          icon: Icons.apartment_rounded,
                          label: 'Empresa',
                          description: 'Nome do ambiente',
                        ),
                        _BootstrapStep(
                          icon: Icons.admin_panel_settings_rounded,
                          label: 'Administrador',
                          description: 'Primeira conta',
                        ),
                        _BootstrapStep(
                          icon: Icons.mark_email_read_outlined,
                          label: 'E-mail de verificação',
                          description: 'Libera o portal',
                        ),
                      ],
                    ),
                    const SizedBox(height: 26),
                    _BootstrapFieldRow(
                      compact: compact,
                      first: _BootstrapField(
                        label: 'Nome da empresa',
                        child: TextFormField(
                          key: AdminLoginBootstrapDialog.companyNameFieldKey,
                          controller: _companyNameController,
                          textInputAction: TextInputAction.next,
                          decoration: _portalInputDecoration(
                            hintText: 'Ex: Colegio VanRides',
                          ),
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return 'Informe o nome da empresa';
                            }
                            return null;
                          },
                        ),
                      ),
                      second: _BootstrapField(
                        label: 'Nome do admin',
                        child: TextFormField(
                          key: AdminLoginBootstrapDialog.adminNameFieldKey,
                          controller: _nomeController,
                          textInputAction: TextInputAction.next,
                          decoration: _portalInputDecoration(
                            hintText: 'Ex: Ricardo Silva',
                          ),
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return 'Informe o nome do admin';
                            }
                            return null;
                          },
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    _BootstrapFieldRow(
                      compact: compact,
                      first: _BootstrapField(
                        label: 'E-mail administrativo',
                        child: TextFormField(
                          key: AdminLoginBootstrapDialog.emailFieldKey,
                          controller: _emailController,
                          keyboardType: TextInputType.emailAddress,
                          textInputAction: TextInputAction.next,
                          decoration: _portalInputDecoration(
                            hintText: 'admin@empresa.com',
                          ),
                          validator: (value) {
                            if (value == null || !value.contains('@')) {
                              return 'Informe um e-mail válido';
                            }
                            return null;
                          },
                        ),
                      ),
                      second: _BootstrapField(
                        label: 'Telefone',
                        child: TextFormField(
                          key: AdminLoginBootstrapDialog.phoneFieldKey,
                          controller: _telefoneController,
                          keyboardType: TextInputType.phone,
                          textInputAction: TextInputAction.next,
                          decoration: _portalInputDecoration(
                            hintText: '(51) 99999-9999',
                          ),
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return 'Informe o telefone';
                            }
                            return null;
                          },
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    _BootstrapField(
                      label: 'Senha inicial',
                      child: TextFormField(
                        key: AdminLoginBootstrapDialog.passwordFieldKey,
                        controller: _senhaController,
                        obscureText: true,
                        decoration: _portalInputDecoration(
                          hintText: 'Mínimo de 8 caracteres',
                        ),
                        validator: (value) {
                          if (value == null || value.length < 8) {
                            return 'Mínimo de 8 caracteres';
                          }
                          return null;
                        },
                      ),
                    ),
                    const SizedBox(height: 18),
                    const _BootstrapInfoCard(),
                    if (_errorMessage != null) ...[
                      const SizedBox(height: 16),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: AdminPortalColors.redSoft,
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(
                            color: AdminPortalColors.red.withValues(alpha: 0.3),
                          ),
                        ),
                        child: Text(
                          _errorMessage!,
                          style: const TextStyle(
                            color: AdminPortalColors.red,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                    const SizedBox(height: 24),
                    Align(
                      alignment: Alignment.centerRight,
                      child: Wrap(
                        spacing: 12,
                        runSpacing: 12,
                        alignment: WrapAlignment.end,
                        children: [
                          AdminPortalSecondaryButton(
                            label: 'Cancelar',
                            onPressed:
                                _submitting
                                    ? null
                                    : () => Navigator.of(context).pop(),
                          ),
                          KeyedSubtree(
                            key: AdminLoginBootstrapDialog.createButtonKey,
                            child: AdminPortalPrimaryButton(
                              label: _submitting ? 'Criando...' : 'Criar admin',
                              icon: Icons.verified_user_outlined,
                              onPressed: _submitting ? null : _submit,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _BootstrapHeader extends StatelessWidget {
  const _BootstrapHeader();

  @override
  Widget build(BuildContext context) {
    return const Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _BootstrapHeaderIcon(),
        SizedBox(width: 18),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Configuração inicial da empresa',
                style: TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 30,
                  fontWeight: FontWeight.w800,
                  height: 1.15,
                ),
              ),
              SizedBox(height: 8),
              Text(
                'Ative o primeiro acesso administrativo com dados reais da empresa. Depois da criação, o e-mail precisa ser confirmado para liberar o portal.',
                style: TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 15,
                  height: 1.55,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _BootstrapHeaderIcon extends StatelessWidget {
  const _BootstrapHeaderIcon();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 58,
      height: 58,
      decoration: BoxDecoration(
        color: AdminPortalColors.blueSoft,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: AdminPortalColors.blueBright.withValues(alpha: 0.28),
        ),
      ),
      child: const Icon(
        Icons.verified_user_outlined,
        color: AdminPortalColors.blueBright,
        size: 28,
      ),
    );
  }
}

class _BootstrapStep extends StatelessWidget {
  const _BootstrapStep({
    required this.icon,
    required this.label,
    required this.description,
  });

  final IconData icon;
  final String label;
  final String description;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minWidth: 190),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: AdminPortalColors.surfaceSoft.withValues(alpha: 0.72),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: AdminPortalColors.blueBright, size: 20),
          const SizedBox(width: 10),
          Flexible(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 13,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  description,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _BootstrapFieldRow extends StatelessWidget {
  const _BootstrapFieldRow({
    required this.compact,
    required this.first,
    required this.second,
  });

  final bool compact;
  final Widget first;
  final Widget second;

  @override
  Widget build(BuildContext context) {
    if (compact) {
      return Column(children: [first, const SizedBox(height: 16), second]);
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(child: first),
        const SizedBox(width: 16),
        Expanded(child: second),
      ],
    );
  }
}

class _BootstrapField extends StatelessWidget {
  const _BootstrapField({required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [_FieldLabel(label: label), const SizedBox(height: 8), child],
    );
  }
}

class _FieldLabel extends StatelessWidget {
  const _FieldLabel({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: const TextStyle(
        color: AdminPortalColors.textMuted,
        fontSize: 14,
        fontWeight: FontWeight.w700,
      ),
    );
  }
}

class _BootstrapInfoCard extends StatelessWidget {
  const _BootstrapInfoCard();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: AdminPortalColors.blueSoft.withValues(alpha: 0.22),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: AdminPortalColors.blueBright.withValues(alpha: 0.24),
        ),
      ),
      padding: const EdgeInsets.all(18),
      child: const Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            Icons.info_outline_rounded,
            color: AdminPortalColors.blueBright,
            size: 20,
          ),
          SizedBox(width: 12),
          Expanded(
            child: Text(
              'O admin nasce pendente por segurança. O portal só libera a operação depois que o e-mail corporativo for confirmado.',
              style: TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 13,
                height: 1.5,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

InputDecoration _portalInputDecoration({required String hintText}) {
  return InputDecoration(
    hintText: hintText,
    filled: true,
    fillColor: AdminPortalColors.input.withValues(alpha: 0.92),
    hintStyle: const TextStyle(color: AdminPortalColors.textFaint),
    errorStyle: const TextStyle(
      color: AdminPortalColors.red,
      fontWeight: FontWeight.w700,
    ),
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: const BorderSide(color: AdminPortalColors.outline),
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: const BorderSide(color: AdminPortalColors.outline),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: const BorderSide(color: AdminPortalColors.blueBright),
    ),
    errorBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: const BorderSide(color: AdminPortalColors.red),
    ),
    focusedErrorBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: const BorderSide(color: AdminPortalColors.red),
    ),
    contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
  );
}
