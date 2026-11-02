import 'package:flutter/material.dart';

class AdminVinculosInviteCreationConfig {
  const AdminVinculosInviteCreationConfig({this.maxUses, this.expiresAt});

  final int? maxUses;
  final DateTime? expiresAt;
}

class AdminVinculosCreateInviteCodeDialog extends StatefulWidget {
  const AdminVinculosCreateInviteCodeDialog({super.key});

  @override
  State<AdminVinculosCreateInviteCodeDialog> createState() =>
      _AdminVinculosCreateInviteCodeDialogState();
}

class _AdminVinculosCreateInviteCodeDialogState
    extends State<AdminVinculosCreateInviteCodeDialog> {
  final _maxUsesController = TextEditingController(text: '1');
  final _daysController = TextEditingController(text: '7');

  @override
  void dispose() {
    _maxUsesController.dispose();
    _daysController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Novo convite'),
      content: SizedBox(
        width: 360,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextFormField(
              controller: _maxUsesController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Número máximo de usos',
              ),
            ),
            const SizedBox(height: 14),
            TextFormField(
              controller: _daysController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Expira em quantos dias',
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          onPressed: () {
            final maxUses = int.tryParse(_maxUsesController.text.trim());
            final days = int.tryParse(_daysController.text.trim());
            Navigator.of(context).pop(
              AdminVinculosInviteCreationConfig(
                maxUses: maxUses,
                expiresAt:
                    days == null
                        ? null
                        : DateTime.now().add(Duration(days: days)),
              ),
            );
          },
          child: const Text('Gerar convite'),
        ),
      ],
    );
  }
}
