import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_surface.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile_update.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';

class EditAccountProfileSheet extends StatefulWidget {
  const EditAccountProfileSheet({
    super.key,
    required this.initialName,
    required this.initialPhone,
    required this.initialBirthDate,
    required this.onSave,
  });

  final String initialName;
  final String initialPhone;
  final String? initialBirthDate;
  final Future<void> Function(AccountProfileUpdate update) onSave;

  @override
  State<EditAccountProfileSheet> createState() =>
      _EditAccountProfileSheetState();
}

class _EditAccountProfileSheetState extends State<EditAccountProfileSheet> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  late final TextEditingController _phoneController;
  late final TextEditingController _birthDateController;
  String? _error;
  bool _saving = false;

  void _updateState(VoidCallback callback) {
    if (mounted) setState(callback);
  }

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.initialName);
    _phoneController = TextEditingController(text: widget.initialPhone);
    _birthDateController = TextEditingController(
      text: widget.initialBirthDate ?? '',
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    _phoneController.dispose();
    _birthDateController.dispose();
    super.dispose();
  }

  Future<void> _selectBirthDate() async {
    final initialDate =
        _parseBirthDateOrNull() ??
        parseAccountProfileDateInput(widget.initialBirthDate ?? '') ??
        DateTime(DateTime.now().year - 18, 1, 1);
    final today = DateTime.now();
    final selected = await showDatePicker(
      context: context,
      initialDate: initialDate.isAfter(today) ? today : initialDate,
      firstDate: DateTime(today.year - 110),
      lastDate: today,
    );
    if (selected == null || !mounted) {
      return;
    }
    _birthDateController.text = formatAccountProfileDate(selected) ?? '';
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    _updateState(() {
      _saving = true;
      _error = null;
    });

    try {
      final update = AccountProfileUpdate(
        nome: _nameController.text,
        telefone: _phoneController.text,
        dataNascimento: _birthDateController.text,
      );
      await widget.onSave(update);
      if (mounted) {
        Navigator.of(context).pop();
      }
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() => _error = error.toString());
    } finally {
      if (mounted) {
        _updateState(() => _saving = false);
      }
    }
  }

  DateTime? _parseBirthDateOrNull() {
    try {
      return parseAccountProfileDateInput(_birthDateController.text);
    } on FormatException {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          bottom: MediaQuery.viewInsetsOf(context).bottom + 16,
        ),
        child: SingleChildScrollView(
          child: VanRidesLiquidSurface.dialog(
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Editar perfil',
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      color: VanRidesMapTokens.textPrimary,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 18),
                  TextFormField(
                    key: const Key('account.profile.nameField'),
                    controller: _nameController,
                    enabled: !_saving,
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(labelText: 'Nome'),
                    validator:
                        (value) =>
                            value == null || value.trim().isEmpty
                                ? 'Informe o nome.'
                                : null,
                  ),
                  const SizedBox(height: 14),
                  TextFormField(
                    key: const Key('account.profile.phoneField'),
                    controller: _phoneController,
                    enabled: !_saving,
                    keyboardType: TextInputType.phone,
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(labelText: 'Telefone'),
                    validator:
                        (value) =>
                            value == null || value.trim().isEmpty
                                ? 'Informe o telefone.'
                                : null,
                  ),
                  const SizedBox(height: 14),
                  TextFormField(
                    key: const Key('account.profile.birthDateField'),
                    controller: _birthDateController,
                    enabled: !_saving,
                    keyboardType: TextInputType.datetime,
                    decoration: InputDecoration(
                      labelText: 'Data de nascimento',
                      hintText: 'AAAA-MM-DD',
                      suffixIcon: IconButton(
                        tooltip: 'Escolher data',
                        onPressed: _saving ? null : _selectBirthDate,
                        icon: const Icon(Icons.calendar_month_rounded),
                      ),
                    ),
                    validator: (value) {
                      try {
                        final parsed = parseAccountProfileDateInput(
                          value ?? '',
                        );
                        if (parsed != null && parsed.isAfter(DateTime.now())) {
                          return 'Informe uma data no passado.';
                        }
                        return null;
                      } on FormatException catch (error) {
                        return error.message;
                      }
                    },
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 14),
                    Text(
                      _error!,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: VanRidesMapTokens.redDanger,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                  const SizedBox(height: 22),
                  FilledButton.icon(
                    key: const Key('account.profile.saveButton'),
                    onPressed: _saving ? null : _submit,
                    icon:
                        _saving
                            ? const SizedBox.square(
                              dimension: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                            : const Icon(Icons.check_rounded),
                    label: Text(_saving ? 'Salvando...' : 'Salvar perfil'),
                    style: FilledButton.styleFrom(
                      minimumSize: const Size.fromHeight(54),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
