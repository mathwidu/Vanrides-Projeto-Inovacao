import 'package:flutter/material.dart';

import '../../domain/admin_motorista_record.dart';
import '../../domain/admin_passageiro_record.dart';
import 'admin_portal_dialog.dart';

class AdminPassageiroReassignDriverDialog extends StatefulWidget {
  const AdminPassageiroReassignDriverDialog({
    super.key,
    required this.passageiro,
    required this.motoristas,
  });

  final AdminPassageiroRecord passageiro;
  final List<AdminMotoristaRecord> motoristas;

  @override
  State<AdminPassageiroReassignDriverDialog> createState() =>
      _AdminPassageiroReassignDriverDialogState();
}

class _AdminPassageiroReassignDriverDialogState
    extends State<AdminPassageiroReassignDriverDialog> {
  int? _motoristaId;

  @override
  void initState() {
    super.initState();
    _motoristaId = widget.passageiro.motoristaId;
  }

  @override
  Widget build(BuildContext context) {
    return AdminPortalDialog(
      title: 'Reatribuir motorista',
      maxWidth: 440,
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          onPressed:
              _motoristaId == null
                  ? null
                  : () => Navigator.of(context).pop(_motoristaId),
          child: const Text('Salvar vinculo'),
        ),
      ],
      child: DropdownButtonFormField<int>(
        initialValue: _motoristaId,
        items: widget.motoristas
            .map(
              (motorista) => DropdownMenuItem<int>(
                value: motorista.id,
                child: Text(motorista.nome),
              ),
            )
            .toList(growable: false),
        onChanged: (value) => setState(() => _motoristaId = value),
        decoration: const InputDecoration(labelText: 'Motorista da empresa'),
      ),
    );
  }
}
