import 'package:flutter/material.dart';

import '../../../../onboarding_flow_components.dart';
import '../../domain/admin_motorista_record.dart';
import '../../domain/admin_passageiro_create_input.dart';
import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_portal_exception.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import 'admin_portal_dialog.dart';
import 'admin_portal_layout.dart';

class AdminPassageiroFormDialog extends StatefulWidget {
  const AdminPassageiroFormDialog({
    super.key,
    required this.title,
    required this.subtitle,
    required this.motoristas,
    required this.servicos,
    required this.onSubmit,
  });

  final String title;
  final String subtitle;
  final List<AdminMotoristaRecord> motoristas;
  final List<AdminServicoRecorrenteRecord> servicos;
  final Future<AdminPassageiroRecord> Function(
    AdminPassageiroCreateInput input,
    List<int> servicoIds,
  )
  onSubmit;

  @override
  State<AdminPassageiroFormDialog> createState() =>
      _AdminPassageiroFormDialogState();
}

class _AdminPassageiroFormDialogState extends State<AdminPassageiroFormDialog> {
  final _formKey = GlobalKey<FormState>();
  final _nomeController = TextEditingController();
  final _emailController = TextEditingController();
  final _telefoneController = TextEditingController();
  final _dataNascimentoController = TextEditingController();
  final _senhaController = TextEditingController();
  final _confirmarSenhaController = TextEditingController();
  final _enderecoController = TextEditingController();
  final _bairroController = TextEditingController();
  final _cidadeController = TextEditingController();
  final _estadoController = TextEditingController();
  final _cepController = TextEditingController();
  int? _motoristaId;
  final Set<int> _servicoIds = <int>{};
  bool _isSubmitting = false;
  String? _errorMessage;

  bool _senhaEhForte(String senha) {
    return senha.length >= 8 &&
        senha.contains(RegExp(r'[A-Z]')) &&
        senha.contains(RegExp(r'[a-z]')) &&
        senha.contains(RegExp(r'[0-9]')) &&
        senha.contains(RegExp(r'[!@#$%^&*(),.?":{}|<>]'));
  }

  DateTime? get _dataNascimento =>
      VanRidesBirthDate.parse(_dataNascimentoController.text);

  bool get _menorDeIdadeCalculado {
    final birthDate = _dataNascimento;
    return birthDate != null && VanRidesBirthDate.isMinor(birthDate);
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    final senha = _senhaController.text.trim();
    final birthDate = _dataNascimento!;
    if (!_senhaEhForte(senha)) {
      setState(
        () =>
            _errorMessage = 'A senha precisa seguir o padrão forte do projeto.',
      );
      return;
    }

    if (senha != _confirmarSenhaController.text.trim()) {
      setState(() => _errorMessage = 'As senhas precisam coincidir.');
      return;
    }

    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final created = await widget.onSubmit(
        AdminPassageiroCreateInput(
          nome: _nomeController.text.trim(),
          email: _emailController.text.trim().toLowerCase(),
          telefone: _telefoneController.text.trim(),
          dataNascimento: VanRidesBirthDate.formatIso(birthDate),
          senha: senha,
          endereco: _enderecoController.text.trim(),
          bairro: _bairroController.text.trim(),
          cidade: _cidadeController.text.trim(),
          estado: _estadoController.text.trim(),
          cep: _cepController.text.trim(),
          menorDeIdade: VanRidesBirthDate.isMinor(birthDate),
          motoristaId: _motoristaId,
          servicoIds: _servicoIds.toList(growable: false),
        ),
        _servicoIds.toList(growable: false),
      );

      if (!mounted) return;
      Navigator.of(context).pop(created);
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
      title: widget.title,
      subtitle: widget.subtitle,
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
          label: Text(_isSubmitting ? 'Salvando...' : 'Salvar passageiro'),
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
              TextFormField(
                controller: _dataNascimentoController,
                keyboardType: TextInputType.datetime,
                onChanged: (_) => setState(() {}),
                decoration: const InputDecoration(
                  labelText: 'Data de nascimento',
                  hintText: 'dd/mm/aaaa',
                  helperText: 'Usada para calcular menor ou adulto.',
                ),
                validator: VanRidesBirthDate.validate,
              ),
              const SizedBox(height: 12),
              _PassengerAgePreview(isMinor: _menorDeIdadeCalculado),
              const SizedBox(height: 12),
              DropdownButtonFormField<int>(
                initialValue: _motoristaId,
                items: [
                  const DropdownMenuItem<int>(
                    child: Text('Definir pela viagem do dia'),
                  ),
                  ...widget.motoristas.map(
                    (motorista) => DropdownMenuItem<int>(
                      value: motorista.id,
                      child: Text(motorista.nome),
                    ),
                  ),
                ],
                decoration: const InputDecoration(
                  labelText: 'Atalho de motorista (opcional)',
                  helperText:
                      'Nao libera viagens sozinho; a execucao vem dos servicos selecionados.',
                ),
                onChanged: (value) => setState(() => _motoristaId = value),
              ),
              const SizedBox(height: 12),
              _ServicoSelectionBlock(
                servicos: widget.servicos,
                selectedIds: _servicoIds,
                onChanged: (servicoId, selected) {
                  setState(() {
                    if (selected) {
                      _servicoIds.add(servicoId);
                    } else {
                      _servicoIds.remove(servicoId);
                    }
                  });
                },
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
              const _AdminInviteFirstNotice(
                text:
                    'Convite primeiro: quando os convites estiverem ativos, este cadastro deve iniciar por convite de ativação. A senha atual mantém compatibilidade temporária.',
              ),
              const SizedBox(height: 12),
              _buildPasswordField(_senhaController, 'Senha'),
              const SizedBox(height: 12),
              _buildPasswordField(_confirmarSenhaController, 'Confirmar senha'),
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

  Widget _buildPasswordField(TextEditingController controller, String label) {
    return TextFormField(
      controller: controller,
      obscureText: true,
      decoration: InputDecoration(labelText: label),
      validator:
          (value) =>
              value == null || value.trim().isEmpty
                  ? 'Campo obrigatório'
                  : null,
    );
  }
}

class _PassengerAgePreview extends StatelessWidget {
  const _PassengerAgePreview({required this.isMinor});

  final bool isMinor;

  @override
  Widget build(BuildContext context) {
    final color = isMinor ? AdminPortalColors.orange : AdminPortalColors.green;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: color.withValues(alpha: 0.28)),
      ),
      child: Row(
        children: [
          Icon(
            isMinor ? Icons.family_restroom_outlined : Icons.verified_outlined,
            color: color,
            size: 20,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              isMinor ? 'Menor de idade' : 'Adulto',
              style: TextStyle(color: color, fontWeight: FontWeight.w800),
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminInviteFirstNotice extends StatelessWidget {
  const _AdminInviteFirstNotice({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AdminPortalColors.blueSoft.withValues(alpha: 0.26),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: AdminPortalColors.blue.withValues(alpha: 0.24),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.forward_to_inbox_outlined,
            color: AdminPortalColors.blueBright,
            size: 20,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                height: 1.35,
                fontSize: 12,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ServicoSelectionBlock extends StatelessWidget {
  const _ServicoSelectionBlock({
    required this.servicos,
    required this.selectedIds,
    required this.onChanged,
  });

  final List<AdminServicoRecorrenteRecord> servicos;
  final Set<int> selectedIds;
  final void Function(int servicoId, bool selected) onChanged;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: AdminPortalColors.surfaceSoft.withValues(alpha: 0.72),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Serviços liberados no cadastro',
              style: TextStyle(
                color: AdminPortalColors.text,
                fontSize: 13,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 4),
            const Text(
              'O passageiro poderá marcar presença e entrar nas viagens geradas pelos serviços selecionados.',
              style: TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 12,
                height: 1.35,
              ),
            ),
            const SizedBox(height: 10),
            if (servicos.isEmpty)
              const Text(
                'Nenhum serviço recorrente cadastrado ainda.',
                style: TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 12,
                ),
              )
            else
              ...servicos.map((servico) {
                final selected = selectedIds.contains(servico.id);
                return CheckboxListTile(
                  value: selected,
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  controlAffinity: ListTileControlAffinity.leading,
                  title: Text(_servicoCadastroLabel(servico)),
                  subtitle: Text(
                    servico.motoristaPadrao == null
                        ? 'Motorista definido na operação do dia'
                        : 'Motorista padrão: ${servico.motoristaPadrao!.nome}',
                  ),
                  onChanged: (value) => onChanged(servico.id, value ?? false),
                );
              }),
          ],
        ),
      ),
    );
  }

  String _servicoCadastroLabel(AdminServicoRecorrenteRecord servico) {
    final horario =
        servico.horarioPartida == null ? '' : '${servico.horarioPartida} ';
    return '$horario${servico.tipoOperacaoLabel} • ${servico.nome}';
  }
}
