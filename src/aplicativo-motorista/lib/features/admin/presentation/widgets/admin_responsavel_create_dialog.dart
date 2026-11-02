import 'package:flutter/material.dart';

import '../../../../onboarding_flow_components.dart';
import '../../domain/admin_portal_exception.dart';
import '../../domain/admin_responsavel_create_input.dart';
import '../../domain/admin_responsavel_record.dart';
import 'admin_portal_dialog.dart';
import 'admin_portal_layout.dart';

String? _validateResponsavelNome(String? value) =>
    value == null || value.trim().isEmpty ? 'Informe o nome.' : null;

String? _validateResponsavelEmail(String? value) =>
    value == null || !value.contains('@') ? 'Informe um e-mail valido.' : null;

String? _validateResponsavelTelefone(String? value) =>
    value == null || value.trim().length < 8 ? 'Informe o telefone.' : null;

bool _senhaEhForte(String senha) {
  return senha.contains(RegExp(r'[A-Z]')) &&
      senha.contains(RegExp(r'[a-z]')) &&
      senha.contains(RegExp(r'[0-9]')) &&
      senha.contains(RegExp(r'[!@#$%^&*(),.?\":{}|<>]')) &&
      senha.length >= 8;
}

class AdminResponsavelCreateDialog extends StatefulWidget {
  const AdminResponsavelCreateDialog({
    super.key,
    required this.title,
    required this.subtitle,
    required this.onSubmit,
  });

  final String title;
  final String subtitle;
  final Future<AdminResponsavelRecord> Function(
    AdminResponsavelCreateInput input,
  )
  onSubmit;

  @override
  State<AdminResponsavelCreateDialog> createState() =>
      _AdminResponsavelCreateDialogState();
}

class _AdminResponsavelCreateDialogState
    extends State<AdminResponsavelCreateDialog> {
  final _formKey = GlobalKey<FormState>();
  final _nomeController = TextEditingController();
  final _emailController = TextEditingController();
  final _telefoneController = TextEditingController();
  final _dataNascimentoController = TextEditingController();
  final _senhaController = TextEditingController();
  final _confirmarSenhaController = TextEditingController();
  bool _isSubmitting = false;
  String? _errorMessage;

  void _update(VoidCallback callback) {
    if (mounted) setState(callback);
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    final senha = _senhaController.text.trim();
    final confirmarSenha = _confirmarSenhaController.text.trim();
    if (senha.isNotEmpty && !_senhaEhForte(senha)) {
      _update(() {
        _errorMessage =
            'Se informada, a senha precisa seguir o padrão forte do projeto.';
      });
      return;
    }
    if (senha != confirmarSenha) {
      _update(() => _errorMessage = 'As senhas precisam coincidir.');
      return;
    }

    _update(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final created = await widget.onSubmit(
        AdminResponsavelCreateInput(
          nome: _nomeController.text.trim(),
          email: _emailController.text.trim().toLowerCase(),
          telefone: _telefoneController.text.trim(),
          dataNascimento: VanRidesBirthDate.toIsoString(
            _dataNascimentoController.text,
          ),
          senha: senha.isEmpty ? null : senha,
        ),
      );

      if (!mounted) return;
      Navigator.of(context).pop(created);
    } on AdminPortalException catch (error) {
      _update(() {
        _errorMessage = error.message;
        _isSubmitting = false;
      });
    } catch (_) {
      _update(() {
        _errorMessage = 'Nao foi possivel salvar o responsavel agora.';
        _isSubmitting = false;
      });
    }
  }

  @override
  void dispose() {
    _nomeController.dispose();
    _emailController.dispose();
    _telefoneController.dispose();
    _dataNascimentoController.dispose();
    _senhaController.dispose();
    _confirmarSenhaController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AdminPortalDialog(
      title: widget.title,
      subtitle: widget.subtitle,
      maxWidth: 500,
      actions: [
        TextButton(
          onPressed: _isSubmitting ? null : () => Navigator.of(context).pop(),
          child: const Text('Cancelar'),
        ),
        FilledButton.icon(
          onPressed: _isSubmitting ? null : _submit,
          icon:
              _isSubmitting
                  ? const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                  : const Icon(Icons.save_outlined),
          label: const Text('Salvar'),
        ),
      ],
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          autovalidateMode: AutovalidateMode.onUserInteraction,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Se voce informar uma senha inicial, o responsavel ja podera entrar no app. Se deixar em branco, o cadastro fica criado, mas o acesso ao app continua pendente.',
                style: TextStyle(color: AdminPortalColors.textMuted),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _nomeController,
                decoration: const InputDecoration(labelText: 'Nome'),
                validator: _validateResponsavelNome,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _emailController,
                decoration: const InputDecoration(labelText: 'E-mail'),
                validator: _validateResponsavelEmail,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _telefoneController,
                decoration: const InputDecoration(labelText: 'Telefone'),
                validator: _validateResponsavelTelefone,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _dataNascimentoController,
                decoration: const InputDecoration(
                  labelText: 'Data de nascimento',
                  helperText: 'Use DD/MM/AAAA.',
                ),
                keyboardType: TextInputType.datetime,
                validator: VanRidesBirthDate.validate,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _senhaController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'Senha inicial (opcional)',
                ),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _confirmarSenhaController,
                obscureText: true,
                decoration: const InputDecoration(labelText: 'Confirmar senha'),
              ),
              if (_errorMessage != null) ...[
                const SizedBox(height: 12),
                AdminPortalDialogError(message: _errorMessage!),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
