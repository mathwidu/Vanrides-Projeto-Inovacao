import 'package:flutter/material.dart';

import '../../domain/admin_motorista_record.dart';
import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_passageiro_update_input.dart';
import '../../domain/admin_portal_exception.dart';
import 'admin_portal_dialog.dart';

class AdminPassageiroEditDialog extends StatefulWidget {
  const AdminPassageiroEditDialog({
    super.key,
    required this.passageiro,
    required this.motoristas,
    required this.onSubmit,
  });

  final AdminPassageiroRecord passageiro;
  final List<AdminMotoristaRecord> motoristas;
  final Future<AdminPassageiroRecord> Function(AdminPassageiroUpdateInput input)
  onSubmit;

  @override
  State<AdminPassageiroEditDialog> createState() =>
      _AdminPassageiroEditDialogState();
}

class _AdminPassageiroEditDialogState extends State<AdminPassageiroEditDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nomeController;
  late final TextEditingController _emailController;
  late final TextEditingController _telefoneController;
  late final TextEditingController _enderecoController;
  late final TextEditingController _bairroController;
  late final TextEditingController _cidadeController;
  late final TextEditingController _estadoController;
  late final TextEditingController _cepController;
  final _senhaController = TextEditingController();
  bool _menorDeIdade = false;
  int? _motoristaId;
  bool _isSubmitting = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    final passageiro = widget.passageiro;
    _nomeController = TextEditingController(text: passageiro.nome);
    _emailController = TextEditingController(text: passageiro.email);
    _telefoneController = TextEditingController(text: passageiro.telefone);
    _enderecoController = TextEditingController(text: passageiro.endereco);
    _bairroController = TextEditingController(text: passageiro.bairro);
    _cidadeController = TextEditingController(text: passageiro.cidade);
    _estadoController = TextEditingController(text: passageiro.estado);
    _cepController = TextEditingController(text: passageiro.cep);
    _menorDeIdade = passageiro.menorDeIdade;
    _motoristaId = passageiro.motoristaId;
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final updated = await widget.onSubmit(
        AdminPassageiroUpdateInput(
          nome: _nomeController.text.trim(),
          email: _emailController.text.trim().toLowerCase(),
          telefone: _telefoneController.text.trim(),
          endereco: _enderecoController.text.trim(),
          bairro: _bairroController.text.trim(),
          cidade: _cidadeController.text.trim(),
          estado: _estadoController.text.trim(),
          cep: _cepController.text.trim(),
          menorDeIdade: _menorDeIdade,
          motoristaId: _motoristaId,
          senha:
              _senhaController.text.trim().isEmpty
                  ? null
                  : _senhaController.text.trim(),
        ),
      );

      if (!mounted) return;
      Navigator.of(context).pop(updated);
    } on AdminPortalException catch (error) {
      setState(() {
        _errorMessage = error.message;
        _isSubmitting = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AdminPortalDialog(
      title: 'Editar passageiro',
      subtitle:
          'A edicao administrativa usa contrato protegido e mantem o escopo da empresa.',
      maxWidth: 640,
      actions: [
        TextButton(
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
          label: Text(_isSubmitting ? 'Salvando...' : 'Salvar alteracoes'),
        ),
      ],
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          autovalidateMode: AutovalidateMode.onUserInteraction,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              _buildTextField(_nomeController, 'Nome completo'),
              const SizedBox(height: 12),
              _buildEmailField(_emailController),
              const SizedBox(height: 12),
              _buildTextField(_telefoneController, 'Telefone'),
              const SizedBox(height: 12),
              DropdownButtonFormField<int>(
                initialValue: _motoristaId,
                items: widget.motoristas
                    .map(
                      (motorista) => DropdownMenuItem<int>(
                        value: motorista.id,
                        child: Text(motorista.nome),
                      ),
                    )
                    .toList(growable: false),
                decoration: const InputDecoration(labelText: 'Motorista'),
                onChanged: (value) => setState(() => _motoristaId = value),
              ),
              const SizedBox(height: 12),
              SwitchListTile.adaptive(
                value: _menorDeIdade,
                contentPadding: EdgeInsets.zero,
                title: const Text('Passageiro menor de idade'),
                onChanged: (value) => setState(() => _menorDeIdade = value),
              ),
              const SizedBox(height: 12),
              _buildTextField(_enderecoController, 'Endereço'),
              const SizedBox(height: 12),
              _buildTextField(_bairroController, 'Bairro'),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(child: _buildTextField(_cidadeController, 'Cidade')),
                  const SizedBox(width: 12),
                  Expanded(child: _buildTextField(_estadoController, 'Estado')),
                ],
              ),
              const SizedBox(height: 12),
              _buildTextField(_cepController, 'CEP'),
              const SizedBox(height: 12),
              TextFormField(
                controller: _senhaController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'Nova senha (opcional)',
                ),
              ),
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

  Widget _buildTextField(TextEditingController controller, String label) {
    return TextFormField(
      controller: controller,
      decoration: InputDecoration(labelText: label),
      validator:
          (value) =>
              value == null || value.trim().isEmpty
                  ? 'Campo obrigatório'
                  : null,
    );
  }

  Widget _buildEmailField(TextEditingController controller) {
    return TextFormField(
      controller: controller,
      decoration: const InputDecoration(labelText: 'E-mail'),
      validator:
          (value) =>
              value != null && value.contains('@') ? null : 'E-mail inválido',
    );
  }
}
