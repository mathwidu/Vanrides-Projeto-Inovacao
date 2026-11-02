import 'package:flutter/material.dart';

import 'admin_portal_layout.dart';

class AdminProfileChecklist extends StatelessWidget {
  const AdminProfileChecklist({
    super.key,
    this.readyItems = const [],
    required this.requiredItems,
    required this.recommendedItems,
    this.actionHint,
    this.editLabel = 'Completar cadastro',
    this.onEdit,
  });

  final List<String> readyItems;
  final List<String> requiredItems;
  final List<String> recommendedItems;
  final String? actionHint;
  final String editLabel;
  final VoidCallback? onEdit;

  @override
  Widget build(BuildContext context) {
    final complete = requiredItems.isEmpty;
    final hasActionHint = actionHint?.trim().isNotEmpty == true;

    if (readyItems.isEmpty &&
        requiredItems.isEmpty &&
        recommendedItems.isEmpty &&
        !hasActionHint) {
      return const SizedBox.shrink();
    }

    return AdminPortalSurface(
      radius: 28,
      padding: const EdgeInsets.all(18),
      color: AdminPortalColors.elevatedSurface.withValues(alpha: 0.82),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 520;
          final content = Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _ProfileChecklistIcon(complete: complete),
              const SizedBox(width: 14),
              Expanded(
                child: _ProfileChecklistBody(
                  complete: complete,
                  readyItems: readyItems,
                  requiredItems: requiredItems,
                  recommendedItems: recommendedItems,
                  actionHint: actionHint,
                ),
              ),
            ],
          );

          if (onEdit == null) {
            return content;
          }

          if (compact) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                content,
                const SizedBox(height: 14),
                SizedBox(
                  width: double.infinity,
                  child: AdminPortalSecondaryButton(
                    label: editLabel,
                    icon: Icons.edit_outlined,
                    onPressed: onEdit,
                  ),
                ),
              ],
            );
          }

          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: content),
              const SizedBox(width: 16),
              AdminPortalSecondaryButton(
                label: editLabel,
                icon: Icons.edit_outlined,
                onPressed: onEdit,
              ),
            ],
          );
        },
      ),
    );
  }
}

class _ProfileChecklistIcon extends StatelessWidget {
  const _ProfileChecklistIcon({required this.complete});

  final bool complete;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 42,
      height: 42,
      decoration: BoxDecoration(
        color:
            complete
                ? AdminPortalColors.greenSoft
                : AdminPortalColors.orangeSoft,
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Icon(
        complete
            ? Icons.verified_user_outlined
            : Icons.playlist_add_check_circle_outlined,
        color:
            complete ? AdminPortalColors.green : AdminPortalColors.warningText,
      ),
    );
  }
}

class _ProfileChecklistBody extends StatelessWidget {
  const _ProfileChecklistBody({
    required this.complete,
    required this.readyItems,
    required this.requiredItems,
    required this.recommendedItems,
    required this.actionHint,
  });

  final bool complete;
  final List<String> readyItems;
  final List<String> requiredItems;
  final List<String> recommendedItems;
  final String? actionHint;

  @override
  Widget build(BuildContext context) {
    final requiredCount = requiredItems.length;
    final recommendedCount = recommendedItems.length;
    final statusCopy =
        requiredCount == 0
            ? recommendedCount == 0
                ? 'Dados obrigatórios prontos para presença, rota e atendimento.'
                : 'Dados obrigatórios prontos. Há recomendação para deixar a operação mais fácil de reconhecer.'
            : requiredCount == 1
            ? 'Falta 1 item obrigatório antes de depender deste perfil na operação.'
            : 'Faltam $requiredCount itens obrigatórios antes de depender deste perfil na operação.';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          complete
              ? 'Perfil operacional completo'
              : 'Perfil operacional incompleto',
          style: const TextStyle(
            color: AdminPortalColors.text,
            fontSize: 18,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 5),
        Text(
          statusCopy,
          style: const TextStyle(
            color: AdminPortalColors.textMuted,
            fontSize: 12,
            height: 1.35,
          ),
        ),
        const SizedBox(height: 14),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final item in readyItems)
              _ProfileChecklistChip(
                label: item,
                kind: _ProfileChecklistChipKind.ready,
              ),
            for (final item in requiredItems)
              _ProfileChecklistChip(
                label: item,
                kind: _ProfileChecklistChipKind.required,
              ),
            for (final item in recommendedItems)
              _ProfileChecklistChip(
                label: item,
                kind: _ProfileChecklistChipKind.recommended,
              ),
          ],
        ),
        if (actionHint?.trim().isNotEmpty == true) ...[
          const SizedBox(height: 12),
          Text(
            actionHint!.trim(),
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 12,
              fontWeight: FontWeight.w700,
              height: 1.35,
            ),
          ),
        ],
      ],
    );
  }
}

enum _ProfileChecklistChipKind { ready, required, recommended }

class _ProfileChecklistChip extends StatelessWidget {
  const _ProfileChecklistChip({required this.label, required this.kind});

  final String label;
  final _ProfileChecklistChipKind kind;

  @override
  Widget build(BuildContext context) {
    final color = switch (kind) {
      _ProfileChecklistChipKind.ready => AdminPortalColors.green,
      _ProfileChecklistChipKind.required => AdminPortalColors.warningText,
      _ProfileChecklistChipKind.recommended => AdminPortalColors.blue,
    };
    final foreground =
        kind == _ProfileChecklistChipKind.required
            ? AdminPortalColors.warningText
            : AdminPortalColors.text;
    final background = switch (kind) {
      _ProfileChecklistChipKind.ready => AdminPortalColors.greenSoft,
      _ProfileChecklistChipKind.required => AdminPortalColors.orange.withValues(
        alpha: 0.16,
      ),
      _ProfileChecklistChipKind.recommended => AdminPortalColors.blue
          .withValues(alpha: 0.16),
    };
    final icon = switch (kind) {
      _ProfileChecklistChipKind.ready => Icons.check_circle_outline_rounded,
      _ProfileChecklistChipKind.required => Icons.priority_high_rounded,
      _ProfileChecklistChipKind.recommended => Icons.auto_awesome_rounded,
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: background.withValues(alpha: 0.82),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.38)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color, size: 15),
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: foreground,
                fontSize: 12,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
