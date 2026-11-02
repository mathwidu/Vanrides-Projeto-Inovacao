import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_portal_exception.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import 'admin_vinculos_service_widgets.dart';

Future<bool> openAdminPassageiroServicosFlow({
  required BuildContext context,
  required AdminPassageiroRecord passageiro,
  required List<AdminServicoRecorrenteRecord> servicos,
  required Future<void> Function(List<int> servicoIds) onSave,
  VoidCallback? onOpenViagens,
}) async {
  if (servicos.isEmpty) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Crie um serviço recorrente antes de liberar acessos.'),
      ),
    );
    return false;
  }

  final initialServicoIds =
      servicos
          .where((servico) => servico.passageiroIds.contains(passageiro.id))
          .map((servico) => servico.id)
          .toSet();

  final selectedIds = await showDialog<List<int>>(
    context: context,
    builder:
        (context) => AdminVinculosPassageiroServicosDialog(
          passageiro: passageiro,
          servicos: servicos,
          initialServicoIds: initialServicoIds,
        ),
  );

  if (selectedIds == null) {
    return false;
  }

  try {
    await onSave(selectedIds);
    if (!context.mounted) return true;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('Serviços do passageiro atualizados com sucesso.'),
        action:
            onOpenViagens == null
                ? null
                : SnackBarAction(
                  label: 'Revisar viagens',
                  onPressed: onOpenViagens,
                ),
      ),
    );
    return true;
  } on AdminPortalException catch (error) {
    if (!context.mounted) return false;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(error.message)));
  } catch (_) {
    if (!context.mounted) return false;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Nao foi possivel atualizar os serviços agora.'),
      ),
    );
  }
  return false;
}
