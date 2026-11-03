import 'package:flutter/material.dart';

import '../../data/admin_vinculo_portal_service.dart';
import '../../domain/admin_passageiro_linked_record.dart';
import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_portal_exception.dart';
import '../../domain/admin_portal_session.dart';
import '../../domain/admin_responsavel_record.dart';
import 'admin_portal_dialog.dart';
import 'admin_portal_layout.dart';

Future<bool> openAdminPassageiroResponsaveisFlow({
  required BuildContext context,
  required AdminPortalSession session,
  required AdminPassageiroRecord passageiro,
  AdminResponsavelPassageiroLinkGateway? gateway,
}) async {
  final linkGateway = gateway ?? AdminVinculoPortalService(session: session);
  try {
    final responsaveis = await linkGateway.fetchResponsaveis();
    if (!context.mounted) return false;
    final action = await showDialog<_GuardianPassengerAction>(
      context: context,
      builder:
          (dialogContext) => _PassageiroResponsaveisDialog(
            passageiro: passageiro,
            responsaveis: responsaveis,
          ),
    );

    if (action == null) return false;

    if (action.type == _GuardianPassengerActionType.link) {
      await linkGateway.linkResponsavelPassageiro(
        responsavelId: action.responsavelId,
        passageiroId: passageiro.id,
      );
      if (context.mounted) {
        _showLinkSnack(context, 'Responsável vinculado ao passageiro.');
      }
      return true;
    }

    await linkGateway.unlinkResponsavelPassageiro(
      responsavelId: action.responsavelId,
      passageiroId: passageiro.id,
    );
    if (context.mounted) {
      _showLinkSnack(context, 'Vínculo do responsável removido.');
    }
    return true;
  } on AdminPortalException catch (error) {
    if (context.mounted) _showLinkSnack(context, error.message);
  } catch (_) {
    if (context.mounted) {
      _showLinkSnack(context, 'Não foi possível atualizar o vínculo.');
    }
  }
  return false;
}

Future<bool> openAdminResponsavelPassageirosFlow({
  required BuildContext context,
  required AdminPortalSession session,
  required AdminResponsavelRecord responsavel,
  AdminResponsavelPassageiroLinkGateway? gateway,
}) async {
  final linkGateway = gateway ?? AdminVinculoPortalService(session: session);
  try {
    final passageiros = await linkGateway.fetchPassageiros();
    if (!context.mounted) return false;
    final action = await showDialog<_GuardianPassengerAction>(
      context: context,
      builder:
          (dialogContext) => _ResponsavelPassageirosDialog(
            responsavel: responsavel,
            passageiros: passageiros,
          ),
    );

    if (action == null) return false;

    if (action.type == _GuardianPassengerActionType.link) {
      await linkGateway.linkResponsavelPassageiro(
        responsavelId: responsavel.id,
        passageiroId: action.passageiroId,
      );
      if (context.mounted) {
        _showLinkSnack(context, 'Passageiro vinculado ao responsável.');
      }
      return true;
    }

    await linkGateway.unlinkResponsavelPassageiro(
      responsavelId: responsavel.id,
      passageiroId: action.passageiroId,
    );
    if (context.mounted) {
      _showLinkSnack(context, 'Vínculo do passageiro removido.');
    }
    return true;
  } on AdminPortalException catch (error) {
    if (context.mounted) _showLinkSnack(context, error.message);
  } catch (_) {
    if (context.mounted) {
      _showLinkSnack(context, 'Não foi possível atualizar o vínculo.');
    }
  }
  return false;
}

class _PassageiroResponsaveisDialog extends StatefulWidget {
  const _PassageiroResponsaveisDialog({
    required this.passageiro,
    required this.responsaveis,
  });

  final AdminPassageiroRecord passageiro;
  final List<AdminResponsavelRecord> responsaveis;

  @override
  State<_PassageiroResponsaveisDialog> createState() =>
      _PassageiroResponsaveisDialogState();
}

class _PassageiroResponsaveisDialogState
    extends State<_PassageiroResponsaveisDialog> {
  int? _selectedResponsavelId;

  @override
  void initState() {
    super.initState();
    final currentIds = widget.passageiro.responsaveis.map((item) => item.id);
    final candidates = widget.responsaveis
        .where((item) => !currentIds.contains(item.id))
        .toList(growable: false);
    _selectedResponsavelId = candidates.isEmpty ? null : candidates.first.id;
  }

  @override
  Widget build(BuildContext context) {
    final current = widget.passageiro.responsaveis;
    final currentIds = current.map((item) => item.id).toSet();
    final candidates = widget.responsaveis
        .where((item) => !currentIds.contains(item.id))
        .toList(growable: false);

    return AdminPortalDialog(
      title: 'Responsáveis de ${widget.passageiro.nome}',
      subtitle:
          'Controle quem pode acompanhar a localização, presença e comunicações deste passageiro.',
      maxWidth: 560,
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Fechar'),
        ),
        FilledButton.icon(
          onPressed:
              _selectedResponsavelId == null
                  ? null
                  : () => Navigator.of(context).pop(
                    _GuardianPassengerAction.link(
                      responsavelId: _selectedResponsavelId!,
                      passageiroId: widget.passageiro.id,
                    ),
                  ),
          icon: const Icon(Icons.link_rounded),
          label: const Text('Vincular responsável'),
        ),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _CurrentGuardiansList(
            responsaveis: current,
            onRemove:
                (responsavel) => Navigator.of(context).pop(
                  _GuardianPassengerAction.unlink(
                    responsavelId: responsavel.id,
                    passageiroId: widget.passageiro.id,
                  ),
                ),
          ),
          const SizedBox(height: 18),
          if (candidates.isEmpty)
            const _LinkEmptyMessage(
              title: 'Nenhum responsável disponível',
              body:
                  'Todos os responsáveis cadastrados já estão vinculados ou ainda não há responsáveis na empresa.',
            )
          else
            DropdownButtonFormField<int>(
              initialValue: _selectedResponsavelId,
              decoration: const InputDecoration(
                labelText: 'Adicionar responsável',
              ),
              items: candidates
                  .map(
                    (item) => DropdownMenuItem<int>(
                      value: item.id,
                      child: Text(item.nome),
                    ),
                  )
                  .toList(growable: false),
              onChanged: (value) => _selectedResponsavelId = value,
            ),
        ],
      ),
    );
  }
}

class _ResponsavelPassageirosDialog extends StatefulWidget {
  const _ResponsavelPassageirosDialog({
    required this.responsavel,
    required this.passageiros,
  });

  final AdminResponsavelRecord responsavel;
  final List<AdminPassageiroRecord> passageiros;

  @override
  State<_ResponsavelPassageirosDialog> createState() =>
      _ResponsavelPassageirosDialogState();
}

class _ResponsavelPassageirosDialogState
    extends State<_ResponsavelPassageirosDialog> {
  int? _selectedPassageiroId;

  @override
  void initState() {
    super.initState();
    final currentIds = widget.responsavel.passageiros.map((item) => item.id);
    final candidates = widget.passageiros
        .where((item) => !currentIds.contains(item.id))
        .toList(growable: false);
    _selectedPassageiroId = candidates.isEmpty ? null : candidates.first.id;
  }

  @override
  Widget build(BuildContext context) {
    final current = widget.responsavel.passageiros;
    final currentIds = current.map((item) => item.id).toSet();
    final candidates = widget.passageiros
        .where((item) => !currentIds.contains(item.id))
        .toList(growable: false);

    return AdminPortalDialog(
      title: 'Passageiros de ${widget.responsavel.nome}',
      subtitle:
          'Mantenha a custódia em contexto, sem abrir a área técnica de vínculos.',
      maxWidth: 560,
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Fechar'),
        ),
        FilledButton.icon(
          onPressed:
              _selectedPassageiroId == null
                  ? null
                  : () => Navigator.of(context).pop(
                    _GuardianPassengerAction.link(
                      responsavelId: widget.responsavel.id,
                      passageiroId: _selectedPassageiroId!,
                    ),
                  ),
          icon: const Icon(Icons.link_rounded),
          label: const Text('Vincular passageiro'),
        ),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _CurrentPassengersList(
            passageiros: current,
            onRemove:
                (passageiro) => Navigator.of(context).pop(
                  _GuardianPassengerAction.unlink(
                    responsavelId: widget.responsavel.id,
                    passageiroId: passageiro.id,
                  ),
                ),
          ),
          const SizedBox(height: 18),
          if (candidates.isEmpty)
            const _LinkEmptyMessage(
              title: 'Nenhum passageiro disponível',
              body:
                  'Todos os passageiros cadastrados já estão vinculados a este responsável.',
            )
          else
            DropdownButtonFormField<int>(
              initialValue: _selectedPassageiroId,
              decoration: const InputDecoration(
                labelText: 'Adicionar passageiro',
              ),
              items: candidates
                  .map(
                    (item) => DropdownMenuItem<int>(
                      value: item.id,
                      child: Text(item.nome),
                    ),
                  )
                  .toList(growable: false),
              onChanged: (value) => _selectedPassageiroId = value,
            ),
        ],
      ),
    );
  }
}

class _CurrentGuardiansList extends StatelessWidget {
  const _CurrentGuardiansList({
    required this.responsaveis,
    required this.onRemove,
  });

  final List<AdminResponsavelRecord> responsaveis;
  final void Function(AdminResponsavelRecord responsavel) onRemove;

  @override
  Widget build(BuildContext context) {
    if (responsaveis.isEmpty) {
      return const _LinkEmptyMessage(
        title: 'Sem responsáveis vinculados',
        body: 'Adicione quem deve acompanhar este passageiro no app.',
      );
    }

    return Column(
      children: responsaveis
          .map(
            (responsavel) => _LinkRow(
              title: responsavel.nome,
              subtitle: responsavel.email,
              icon: Icons.supervisor_account_outlined,
              onRemove: () => onRemove(responsavel),
            ),
          )
          .toList(growable: false),
    );
  }
}

class _CurrentPassengersList extends StatelessWidget {
  const _CurrentPassengersList({
    required this.passageiros,
    required this.onRemove,
  });

  final List<AdminPassageiroLinkedRecord> passageiros;
  final void Function(AdminPassageiroLinkedRecord passageiro) onRemove;

  @override
  Widget build(BuildContext context) {
    if (passageiros.isEmpty) {
      return const _LinkEmptyMessage(
        title: 'Sem passageiros vinculados',
        body: 'Adicione os passageiros que este responsável acompanha.',
      );
    }

    return Column(
      children: passageiros
          .map(
            (passageiro) => _LinkRow(
              title: passageiro.nome,
              subtitle:
                  passageiro.menorDeIdade
                      ? 'Menor de idade'
                      : 'Dependente operacional',
              icon: Icons.person_pin_circle_outlined,
              onRemove: () => onRemove(passageiro),
            ),
          )
          .toList(growable: false),
    );
  }
}

class _LinkRow extends StatelessWidget {
  const _LinkRow({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.onRemove,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AdminPortalColors.statusSurface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Row(
        children: [
          Icon(icon, color: AdminPortalColors.blue, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Text(
                  subtitle.trim().isEmpty ? 'Contato não informado' : subtitle,
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Remover vínculo',
            onPressed: onRemove,
            icon: const Icon(Icons.link_off_rounded),
          ),
        ],
      ),
    );
  }
}

class _LinkEmptyMessage extends StatelessWidget {
  const _LinkEmptyMessage({required this.title, required this.body});

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AdminPortalColors.canvas.withValues(alpha: 0.48),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            body,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 12,
              height: 1.35,
            ),
          ),
        ],
      ),
    );
  }
}

enum _GuardianPassengerActionType { link, unlink }

class _GuardianPassengerAction {
  const _GuardianPassengerAction._({
    required this.type,
    required this.responsavelId,
    required this.passageiroId,
  });

  factory _GuardianPassengerAction.link({
    required int responsavelId,
    required int passageiroId,
  }) {
    return _GuardianPassengerAction._(
      type: _GuardianPassengerActionType.link,
      responsavelId: responsavelId,
      passageiroId: passageiroId,
    );
  }

  factory _GuardianPassengerAction.unlink({
    required int responsavelId,
    required int passageiroId,
  }) {
    return _GuardianPassengerAction._(
      type: _GuardianPassengerActionType.unlink,
      responsavelId: responsavelId,
      passageiroId: passageiroId,
    );
  }

  final _GuardianPassengerActionType type;
  final int responsavelId;
  final int passageiroId;
}

void _showLinkSnack(BuildContext context, String message) {
  final messenger = ScaffoldMessenger.of(context);
  messenger.clearSnackBars();
  messenger.showSnackBar(SnackBar(content: Text(message)));
}
