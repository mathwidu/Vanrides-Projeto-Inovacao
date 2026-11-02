import 'package:flutter/material.dart';

import '../../domain/admin_responsavel_record.dart';
import 'admin_person_avatar.dart';
import 'admin_person_detail_components.dart';
import 'admin_portal_layout.dart';
import 'admin_profile_checklist.dart';
import 'admin_responsavel_passenger_links_section.dart';

class AdminResponsavelDetailPanel extends StatelessWidget {
  const AdminResponsavelDetailPanel({
    super.key,
    required this.isLoading,
    required this.responsavel,
    required this.onManagePassageiros,
    required this.onEdit,
    required this.onDelete,
    this.referenceDate,
  });

  final bool isLoading;
  final AdminResponsavelRecord? responsavel;
  final Future<void> Function() onManagePassageiros;
  final Future<void> Function() onEdit;
  final Future<void> Function() onDelete;
  final DateTime? referenceDate;

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const AdminPortalSurface(
        radius: 48,
        child: SizedBox(
          height: 420,
          child: Center(child: CircularProgressIndicator()),
        ),
      );
    }

    final selected = responsavel;
    if (selected == null) {
      return const AdminPortalSurface(
        radius: 48,
        child: SizedBox(
          height: 420,
          child: Center(
            child: Text(
              'Selecione um responsável para ver o detalhe administrativo.',
            ),
          ),
        ),
      );
    }

    final nextAction = _responsavelNextAction(
      responsavel: selected,
      onManagePassageiros: onManagePassageiros,
      onEdit: onEdit,
    );
    final checklistAction = _responsavelChecklistAction(
      responsavel: selected,
      onManagePassageiros: onManagePassageiros,
      onEdit: onEdit,
    );

    return Column(
      children: [
        AdminPortalSurface(
          radius: 44,
          color: AdminPortalColors.elevatedSurface,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  AdminPersonAvatar(
                    name: selected.nome,
                    photoUrl: selected.fotoPerfilUrl,
                    radius: 40,
                    background: AdminPortalColors.blueSoft,
                    foreground: AdminPortalColors.blueBright,
                  ),
                  const Spacer(),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      AdminPortalTag(
                        label:
                            selected.acessoConfigurado
                                ? 'ACESSO ATIVO'
                                : 'ACESSO PENDENTE',
                        background:
                            selected.acessoConfigurado
                                ? AdminPortalColors.greenSoft
                                : AdminPortalColors.orangeSoft,
                        foreground:
                            selected.acessoConfigurado
                                ? AdminPortalColors.successText
                                : AdminPortalColors.warningText,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'ID: #RE-${selected.id}',
                        style: const TextStyle(
                          color: AdminPortalColors.textMuted,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Text(
                selected.nome,
                style: const TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                selected.acessoConfigurado
                    ? 'Portal configurado para acesso'
                    : 'Portal pendente de configuração',
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 14,
                ),
              ),
              const SizedBox(height: 20),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  _ResponsavelHeroPill(
                    icon: Icons.phone_outlined,
                    text: _responsavelDisplayValue(
                      selected.telefone,
                      fallback: 'Telefone não informado',
                    ),
                  ),
                  _ResponsavelHeroPill(
                    icon: Icons.verified_user_outlined,
                    text:
                        selected.acessoConfigurado
                            ? 'Acesso ao app configurado'
                            : 'Acesso ao app pendente',
                  ),
                ],
              ),
              const SizedBox(height: 20),
              AdminPersonNextActionCard(
                actionLabel: nextAction.label,
                body: nextAction.body,
                icon: nextAction.icon,
                accent: nextAction.accent,
                onAction: nextAction.onAction,
              ),
            ],
          ),
        ),
        if (_hasResponsavelProfileChecklistItems(selected)) ...[
          const SizedBox(height: 24),
          AdminProfileChecklist(
            readyItems: _responsavelReadyProfileItems(selected),
            requiredItems: _responsavelRequiredProfileItems(selected),
            recommendedItems: _responsavelRecommendedProfileItems(selected),
            actionHint: checklistAction.hint,
            editLabel: checklistAction.label,
            onEdit: checklistAction.onPressed,
          ),
        ],
        const SizedBox(height: 24),
        AdminResponsavelPassengerLinksSection(
          responsavel: selected,
          onManagePassageiros: onManagePassageiros,
        ),
        const SizedBox(height: 24),
        AdminDetailDisclosureSection(
          title: 'Dados administrativos',
          subtitle: 'Contato, nascimento, acesso e ações da conta.',
          icon: Icons.admin_panel_settings_outlined,
          accent: AdminPortalColors.textMuted,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Column(
                children: [
                  _AdminResponsavelInfoRow(
                    icon: Icons.mail_outline,
                    label: 'E-mail',
                    value: _responsavelDisplayValue(
                      selected.email,
                      fallback: 'E-mail não informado',
                    ),
                  ),
                  const SizedBox(height: 12),
                  _AdminResponsavelInfoRow(
                    icon: Icons.phone_outlined,
                    label: 'Telefone',
                    value: _responsavelDisplayValue(
                      selected.telefone,
                      fallback: 'Telefone não informado',
                    ),
                  ),
                  const SizedBox(height: 12),
                  _AdminResponsavelInfoRow(
                    icon: Icons.cake_outlined,
                    label: 'Nascimento',
                    value: _birthLine(
                      selected.dataNascimento,
                      referenceDate: referenceDate,
                    ),
                  ),
                  const SizedBox(height: 12),
                  _AdminResponsavelInfoRow(
                    icon: Icons.group_outlined,
                    label: 'Passageiros vinculados',
                    value: _passageiroCountLabel(selected.passageiroCount),
                  ),
                  const SizedBox(height: 12),
                  _AdminResponsavelInfoRow(
                    icon: Icons.verified_user_outlined,
                    label: 'Acesso ao app',
                    value:
                        selected.acessoConfigurado ? 'Configurado' : 'Pendente',
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  AdminPortalPrimaryButton(
                    label: 'Editar responsável',
                    icon: Icons.edit_outlined,
                    onPressed: onEdit,
                  ),
                  AdminPortalSecondaryButton(
                    label: 'Remover responsável',
                    icon: Icons.delete_outline,
                    onPressed: onDelete,
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _AdminResponsavelInfoRow extends StatelessWidget {
  const _AdminResponsavelInfoRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: AdminPortalColors.statusSurface,
            border: Border.all(color: AdminPortalColors.outline),
            borderRadius: BorderRadius.circular(18),
          ),
          child: Icon(icon, color: AdminPortalColors.blue, size: 18),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                value,
                style: const TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  height: 1.3,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _ResponsavelNextAction {
  const _ResponsavelNextAction({
    required this.label,
    required this.body,
    required this.icon,
    required this.accent,
    this.onAction,
  });

  final String label;
  final String body;
  final IconData icon;
  final Color accent;
  final VoidCallback? onAction;
}

_ResponsavelNextAction _responsavelNextAction({
  required AdminResponsavelRecord responsavel,
  required Future<void> Function() onManagePassageiros,
  required Future<void> Function() onEdit,
}) {
  if (_hasCoreResponsavelProfileIssue(responsavel)) {
    return _ResponsavelNextAction(
      label: 'Completar cadastro',
      body: 'Revise contato e nascimento antes de depender deste perfil.',
      icon: Icons.playlist_add_check_circle_outlined,
      accent: AdminPortalColors.orange,
      onAction: () {
        onEdit();
      },
    );
  }

  if (responsavel.passageiroCount == 0 && responsavel.passageiros.isEmpty) {
    return _ResponsavelNextAction(
      label: 'Vincular passageiro',
      body: 'Sem passageiro vinculado, o responsável não acompanha operação.',
      icon: Icons.link_rounded,
      accent: AdminPortalColors.orange,
      onAction: () {
        onManagePassageiros();
      },
    );
  }

  if (!responsavel.acessoConfigurado) {
    return _ResponsavelNextAction(
      label: 'Configurar acesso',
      body: 'Finalize o acesso ao app para liberar acompanhamento.',
      icon: Icons.verified_user_outlined,
      accent: AdminPortalColors.blue,
      onAction: () {
        onEdit();
      },
    );
  }

  return _ResponsavelNextAction(
    label: 'Revisar passageiros vinculados',
    body: 'Vínculos prontos. Abra a lista apenas para ajustes operacionais.',
    icon: Icons.supervisor_account_outlined,
    accent: AdminPortalColors.green,
    onAction: () {
      onManagePassageiros();
    },
  );
}

String _responsavelDisplayValue(String value, {required String fallback}) {
  final trimmed = value.trim();
  return trimmed.isEmpty ? fallback : trimmed;
}

String _passageiroCountLabel(int count) {
  if (count == 1) {
    return '1 passageiro vinculado';
  }
  return '$count passageiros vinculados';
}

String _birthLine(String? dataNascimento, {DateTime? referenceDate}) {
  final date = DateTime.tryParse(dataNascimento?.trim() ?? '');
  if (date == null) {
    return 'Data de nascimento não informada';
  }
  final age = _ageInYears(date, referenceDate ?? DateTime.now());
  return '${date.day.toString().padLeft(2, '0')}/'
      '${date.month.toString().padLeft(2, '0')}/${date.year}, $age anos';
}

bool _hasResponsavelProfileChecklistItems(AdminResponsavelRecord responsavel) {
  return _responsavelReadyProfileItems(responsavel).isNotEmpty ||
      _responsavelRequiredProfileItems(responsavel).isNotEmpty ||
      _responsavelRecommendedProfileItems(responsavel).isNotEmpty;
}

List<String> _responsavelReadyProfileItems(AdminResponsavelRecord item) {
  final items = <String>[];
  final hasPassenger = item.passageiroCount > 0 || item.passageiros.isNotEmpty;
  if (_hasText(item.nome) && _hasText(item.email) && _hasText(item.telefone)) {
    items.add('Dados pessoais informados');
  }
  if (DateTime.tryParse(item.dataNascimento?.trim() ?? '') != null) {
    items.add('Nascimento informado');
  }
  if (hasPassenger) {
    items.add('Passageiro vinculado');
  }
  if (item.acessoConfigurado) {
    items.add('Acesso de acompanhamento configurado');
  }
  return items;
}

List<String> _responsavelRequiredProfileItems(AdminResponsavelRecord item) {
  final items = <String>[];
  if (!_hasText(item.nome) ||
      !_hasText(item.email) ||
      !_hasText(item.telefone)) {
    items.add('Informar nome, telefone e e-mail');
  }
  if (DateTime.tryParse(item.dataNascimento?.trim() ?? '') == null) {
    items.add('Informar data de nascimento');
  }
  if (item.passageiroCount == 0 && item.passageiros.isEmpty) {
    items.add('Vincular passageiro acompanhado');
  }
  if (!item.acessoConfigurado) {
    items.add('Configurar acesso de acompanhamento');
  }
  return items;
}

bool _hasCoreResponsavelProfileIssue(AdminResponsavelRecord item) {
  return !_hasText(item.nome) ||
      !_hasText(item.email) ||
      !_hasText(item.telefone) ||
      DateTime.tryParse(item.dataNascimento?.trim() ?? '') == null;
}

List<String> _responsavelRecommendedProfileItems(AdminResponsavelRecord item) {
  return const [];
}

bool _hasText(String? value) => (value ?? '').trim().isNotEmpty;

_ResponsavelChecklistAction _responsavelChecklistAction({
  required AdminResponsavelRecord responsavel,
  required Future<void> Function() onManagePassageiros,
  required Future<void> Function() onEdit,
}) {
  if (_hasCoreResponsavelProfileIssue(responsavel)) {
    return _ResponsavelChecklistAction(
      label: 'Editar cadastro',
      hint: 'Ação: complete os dados cadastrais do responsável.',
      onPressed: () {
        onEdit();
      },
    );
  }
  if (responsavel.passageiroCount == 0 && responsavel.passageiros.isEmpty) {
    return _ResponsavelChecklistAction(
      label: 'Vincular passageiro',
      hint: 'Ação: vincule um passageiro acompanhado nesta ficha.',
      onPressed: () {
        onManagePassageiros();
      },
    );
  }
  if (!responsavel.acessoConfigurado) {
    return _ResponsavelChecklistAction(
      label: 'Configurar acesso',
      hint: 'Ação: defina uma senha para liberar acompanhamento no app.',
      onPressed: () {
        onEdit();
      },
    );
  }
  return _ResponsavelChecklistAction(
    label: 'Revisar passageiros',
    hint:
        'Ação: revise vínculos apenas quando o contexto de acompanhamento mudar.',
    onPressed: () {
      onManagePassageiros();
    },
  );
}

class _ResponsavelChecklistAction {
  const _ResponsavelChecklistAction({
    required this.label,
    required this.hint,
    required this.onPressed,
  });

  final String label;
  final String hint;
  final VoidCallback onPressed;
}

int _ageInYears(DateTime birthDate, DateTime now) {
  var age = now.year - birthDate.year;
  final hadBirthday =
      now.month > birthDate.month ||
      (now.month == birthDate.month && now.day >= birthDate.day);
  if (!hadBirthday) {
    age--;
  }
  return age < 0 ? 0 : age;
}

class _ResponsavelHeroPill extends StatelessWidget {
  const _ResponsavelHeroPill({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AdminPortalColors.statusSurface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: AdminPortalColors.blueBright, size: 18),
          const SizedBox(width: 10),
          Text(
            text,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}
