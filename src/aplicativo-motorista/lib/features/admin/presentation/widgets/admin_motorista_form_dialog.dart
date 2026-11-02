import 'package:flutter/material.dart';

import '../../../../onboarding_flow_components.dart';
import '../../data/admin_motorista_portal_service.dart';
import '../../domain/admin_motorista_create_input.dart';
import '../../domain/admin_motorista_record.dart';
import 'admin_portal_dialog.dart';

class AdminMotoristaFormDialog extends StatefulWidget {
  const AdminMotoristaFormDialog({
    super.key,
    required this.gateway,
    this.initialMotorista,
  });

  final AdminMotoristaGateway gateway;
  final AdminMotoristaRecord? initialMotorista;

  @override
  State<AdminMotoristaFormDialog> createState() =>
      _AdminMotoristaFormDialogState();
}

class _AdminMotoristaFormDialogState extends State<AdminMotoristaFormDialog> {
  final _formKey = GlobalKey<FormState>();
  final _nomeController = TextEditingController();
  final _emailController = TextEditingController();
  final _telefoneController = TextEditingController();
  final _dataNascimentoController = TextEditingController();
  bool _isSubmitting = false;
  String? _errorMessage;

  bool get _isEditing => widget.initialMotorista != null;

  @override
  void initState() {
    super.initState();
    final m = widget.initialMotorista;
    if (m != null) {
      _nomeController.text = m.nome;
      _emailController.text = m.email;
      _telefoneController.text = m.telefone;
    }
  }

  @override
  void dispose() {
    _nomeController.dispose();
    _emailController.dispose();
    _telefoneController.dispose();
    _dataNascimentoController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final AdminMotoristaRecord motorista;

      if (_isEditing) {
        motorista = await widget.gateway.editMotorista(
          widget.initialMotorista!.id,
          nome: _nomeController.text.trim(),
          email: _emailController.text.trim().toLowerCase(),
          telefone: _telefoneController.text.trim(),
        );
      } else {
        final dataNascimento =
            VanRidesBirthDate.toIsoString(_dataNascimentoController.text)!;
        motorista = await widget.gateway.createMotorista(
          AdminMotoristaCreateInput(
            nome: _nomeController.text.trim(),
            email: _emailController.text.trim().toLowerCase(),
            telefone: _telefoneController.text.trim(),
            dataNascimento: dataNascimento,
          ),
        );
      }

      if (!mounted) return;
      Navigator.of(context).pop(motorista);
    } on AdminMotoristaPortalException catch (error) {
      setState(() {
        _errorMessage = error.message;
        _isSubmitting = false;
      });
    } catch (_) {
      setState(() {
        _errorMessage =
            _isEditing
                ? 'Nao foi possivel atualizar o motorista agora.'
                : 'Nao foi possivel criar o motorista agora.';
        _isSubmitting = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AdminPortalDialog(
      title: _isEditing ? 'Editar motorista' : 'Vincular motorista',
      subtitle:
          _isEditing
              ? 'Atualize nome, e-mail e telefone. A senha nao pode ser alterada por aqui.'
              : 'Use este caminho apenas quando a conta do motorista já existe ou quando a equipe precisa registrar o vínculo operacional. Para novos motoristas, prefira convite por link.',
      maxWidth: 560,
      actions: [
        OutlinedButton(
          onPressed: _isSubmitting ? null : () => Navigator.of(context).pop(),
          child: const Text('Cancelar'),
        ),
        FilledButton.icon(
          onPressed: _isSubmitting ? null : _submit,
          icon:
              _isSubmitting
                  ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                  : const Icon(Icons.save_outlined),
          label: Text(
            _isSubmitting
                ? 'Salvando...'
                : (_isEditing ? 'Salvar alteracoes' : 'Salvar vínculo'),
          ),
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
              TextFormField(
                controller: _nomeController,
                decoration: const InputDecoration(labelText: 'Nome completo'),
                validator:
                    (value) =>
                        value == null || value.isEmpty
                            ? 'Campo obrigatorio'
                            : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _emailController,
                decoration: const InputDecoration(labelText: 'E-mail'),
                validator:
                    (value) =>
                        value != null && value.contains('@')
                            ? null
                            : 'E-mail invalido',
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _telefoneController,
                decoration: const InputDecoration(labelText: 'Telefone'),
                validator:
                    (value) =>
                        value == null || value.isEmpty
                            ? 'Campo obrigatorio'
                            : null,
              ),
              if (!_isEditing) ...[
                const SizedBox(height: 12),
                TextFormField(
                  controller: _dataNascimentoController,
                  keyboardType: TextInputType.datetime,
                  decoration: const InputDecoration(
                    labelText: 'Data de nascimento',
                    hintText: 'dd/mm/aaaa',
                  ),
                  validator: VanRidesBirthDate.validate,
                ),
              ],
              if (_errorMessage != null) ...[
                const SizedBox(height: 16),
                AdminPortalDialogError(message: _errorMessage!),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
