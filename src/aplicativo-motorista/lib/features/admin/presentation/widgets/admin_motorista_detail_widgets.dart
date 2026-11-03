import 'package:flutter/material.dart';

import '../../domain/admin_motorista_record.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import '../../domain/admin_viagem_record.dart';
import 'admin_motorista_assignments_panel.dart';
import 'admin_person_avatar.dart';
import 'admin_person_detail_components.dart';
import 'admin_portal_layout.dart';
import 'admin_profile_checklist.dart';

class AdminMotoristaDetailPanel extends StatelessWidget {
  const AdminMotoristaDetailPanel({
    super.key,
    required this.motorista,
    this.assignedServicos = const [],
    this.assignedViagens = const [],
    this.loadingAssignments = false,
    this.assignmentError,
    this.onRefreshAssignments,
    this.onEdit,
    this.onDelete,
  });

  final AdminMotoristaRecord? motorista;
  final List<AdminServicoRecorrenteRecord> assignedServicos;
  final List<AdminViagemRecord> assignedViagens;
  final bool loadingAssignments;
  final String? assignmentError;
  final VoidCallback? onRefreshAssignments;
  final VoidCallback? onEdit;
  final VoidCallback? onDelete;

  bool get _showsAssignments {
    return loadingAssignments ||
        assignmentError != null ||
        assignedServicos.isNotEmpty ||
        assignedViagens.isNotEmpty ||
        onRefreshAssignments != null;
  }

  @override
  Widget build(BuildContext context) {
    if (motorista == null) {
      return const AdminPortalSurface(
        radius: 40,
        padding: EdgeInsets.all(24),
        child: SizedBox(
          height: 260,
          child: Center(
            child: Text('Selecione um motorista para ver mais detalhes.'),
          ),
        ),
      );
    }

    final selected = motorista!;
    final nextAction = _driverNextAction(
      motorista: selected,
      assignedServicos: assignedServicos,
      assignedViagens: assignedViagens,
      assignmentError: assignmentError,
      onEdit: onEdit,
      onRefreshAssignments: onRefreshAssignments,
    );

    return AdminPortalSurface(
      radius: 40,
      padding: const EdgeInsets.all(28),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          LayoutBuilder(
            builder: (context, constraints) {
              final compact = constraints.maxWidth < 300;
              return compact
                  ? Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _DriverAvatar(motorista: selected),
                      const SizedBox(height: 16),
                      const AdminPortalTag(
                        label: 'VINCULADO',
                        background: AdminPortalColors.greenSoft,
                        foreground: AdminPortalColors.green,
                      ),
                    ],
                  )
                  : Row(
                    children: [
                      _DriverAvatar(motorista: selected),
                      const Spacer(),
                      const AdminPortalTag(
                        label: 'VINCULADO',
                        background: AdminPortalColors.greenSoft,
                        foreground: AdminPortalColors.green,
                      ),
                    ],
                  );
            },
          ),
          const SizedBox(height: 20),
          Text(
            selected.nome,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 20,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            'Pode ser escalado em serviços e viagens da empresa.',
            style: TextStyle(color: AdminPortalColors.textMuted, fontSize: 14),
          ),
          const SizedBox(height: 20),
          AdminPersonNextActionCard(
            actionLabel: nextAction.label,
            body: nextAction.body,
            icon: nextAction.icon,
            accent: nextAction.accent,
            onAction: nextAction.onAction,
          ),
          if (onEdit != null &&
              _hasMotoristaProfileChecklistItems(selected)) ...[
            const SizedBox(height: 20),
            AdminProfileChecklist(
              readyItems: _motoristaReadyProfileItems(selected),
              requiredItems: _motoristaRequiredProfileItems(selected),
              recommendedItems: _motoristaRecommendedProfileItems(selected),
              actionHint: _motoristaChecklistActionHint(selected),
              editLabel: 'Editar cadastro',
              onEdit:
                  _motoristaRequiredProfileItems(selected).isEmpty
                      ? null
                      : onEdit,
            ),
          ],
          const SizedBox(height: 20),
          if (_showsAssignments) ...[
            AdminMotoristaAssignmentsPanel(
              servicos: assignedServicos,
              viagens: assignedViagens,
              loading: loadingAssignments,
              errorMessage: assignmentError,
              onRefresh: onRefreshAssignments,
            ),
            const SizedBox(height: 20),
          ],
          _DriverAdminDataSection(
            motorista: selected,
            onEdit: onEdit,
            onDelete: onDelete,
          ),
        ],
      ),
    );
  }
}

class _DriverAdminDataSection extends StatelessWidget {
  const _DriverAdminDataSection({
    required this.motorista,
    required this.onEdit,
    required this.onDelete,
  });

  final AdminMotoristaRecord motorista;
  final VoidCallback? onEdit;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    return AdminDetailDisclosureSection(
      title: 'Dados administrativos',
      subtitle: 'Contato, nascimento e permissão operacional.',
      icon: Icons.badge_outlined,
      accent: AdminPortalColors.textMuted,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _MotoristaInfoRow(
            icon: Icons.badge_outlined,
            text: 'ID: ${motorista.id}',
          ),
          const SizedBox(height: 12),
          _MotoristaInfoRow(icon: Icons.mail_outline, text: motorista.email),
          const SizedBox(height: 12),
          _MotoristaInfoRow(
            icon: Icons.call_outlined,
            text: motorista.telefone,
          ),
          const SizedBox(height: 12),
          _MotoristaInfoRow(
            icon: Icons.cake_outlined,
            text: _birthLine(motorista.dataNascimento),
          ),
          const SizedBox(height: 18),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AdminPortalColors.surfaceSoft,
              borderRadius: BorderRadius.circular(22),
            ),
            child: const Text(
              'A conta do app continua sendo do motorista. A empresa apenas ganha permissão para escalá-lo nas operações.',
              style: TextStyle(
                color: AdminPortalColors.text,
                fontSize: 13,
                fontWeight: FontWeight.w600,
                height: 1.35,
              ),
            ),
          ),
          const SizedBox(height: 18),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              AdminPortalSecondaryButton(
                label: 'Editar dados',
                icon: Icons.edit_outlined,
                onPressed: onEdit,
              ),
              AdminPortalSecondaryButton(
                label: 'Remover motorista',
                icon: Icons.delete_outline,
                onPressed: onDelete,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _DriverNextAction {
  const _DriverNextAction({
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

_DriverNextAction _driverNextAction({
  required AdminMotoristaRecord motorista,
  required List<AdminServicoRecorrenteRecord> assignedServicos,
  required List<AdminViagemRecord> assignedViagens,
  required String? assignmentError,
  required VoidCallback? onEdit,
  required VoidCallback? onRefreshAssignments,
}) {
  if (_motoristaRequiredProfileItems(motorista).isNotEmpty) {
    return _DriverNextAction(
      label: 'Completar cadastro',
      body: 'Revise contato e nascimento antes de escalar o motorista.',
      icon: Icons.playlist_add_check_circle_outlined,
      accent: AdminPortalColors.orange,
      onAction: onEdit,
    );
  }

  if (assignmentError != null) {
    return _DriverNextAction(
      label: 'Atualizar atribuições',
      body: 'Não foi possível confirmar serviços e viagens desse motorista.',
      icon: Icons.refresh_rounded,
      accent: AdminPortalColors.orange,
      onAction: onRefreshAssignments,
    );
  }

  if (assignedServicos.isEmpty && assignedViagens.isEmpty) {
    return const _DriverNextAction(
      label: 'Escalar em Serviços ou Viagens',
      body: 'O motorista está apto, mas ainda não aparece em operação.',
      icon: Icons.route_outlined,
      accent: AdminPortalColors.blue,
    );
  }

  return _DriverNextAction(
    label: 'Escala operacional pronta',
    body:
        '${assignedServicos.length} serviço(s) padrão e ${assignedViagens.length} viagem(ns) do dia vinculados.',
    icon: Icons.verified_user_outlined,
    accent: AdminPortalColors.green,
  );
}

class AdminMotoristaErrorState extends StatelessWidget {
  const AdminMotoristaErrorState({
    super.key,
    required this.message,
    required this.onRetry,
  });

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: AdminPortalSurface(
          radius: 40,
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.error_outline,
                size: 40,
                color: AdminPortalColors.red,
              ),
              const SizedBox(height: 16),
              Text(
                message,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 16,
                ),
              ),
              const SizedBox(height: 20),
              AdminPortalPrimaryButton(
                label: 'Tentar novamente',
                onPressed: onRetry,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DriverAvatar extends StatelessWidget {
  const _DriverAvatar({required this.motorista});

  final AdminMotoristaRecord motorista;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: AdminPortalColors.blueSoft, width: 4),
      ),
      child: AdminPersonAvatar(
        name: motorista.nome,
        photoUrl: motorista.fotoPerfilUrl,
        radius: 38,
        background: AdminPortalColors.blueSoft,
        foreground: AdminPortalColors.text,
      ),
    );
  }
}

class _MotoristaInfoRow extends StatelessWidget {
  const _MotoristaInfoRow({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 32,
          height: 32,
          decoration: BoxDecoration(
            color: AdminPortalColors.surfaceSoft,
            borderRadius: BorderRadius.circular(999),
          ),
          child: Icon(icon, size: 16, color: AdminPortalColors.textMuted),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 14,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
      ],
    );
  }
}

String _birthLine(String? dataNascimento) {
  final date = DateTime.tryParse(dataNascimento?.trim() ?? '');
  if (date == null) {
    return 'Data de nascimento não informada';
  }
  final age = _ageInYears(date);
  return 'Nascimento: ${date.day.toString().padLeft(2, '0')}/'
      '${date.month.toString().padLeft(2, '0')}/${date.year}, $age anos';
}

bool _hasMotoristaProfileChecklistItems(AdminMotoristaRecord motorista) {
  return _motoristaReadyProfileItems(motorista).isNotEmpty ||
      _motoristaRequiredProfileItems(motorista).isNotEmpty ||
      _motoristaRecommendedProfileItems(motorista).isNotEmpty;
}

List<String> _motoristaReadyProfileItems(AdminMotoristaRecord motorista) {
  final items = <String>[];
  if (_hasText(motorista.nome) &&
      _hasText(motorista.email) &&
      _hasText(motorista.telefone)) {
    items.add('Dados pessoais informados');
  }
  if (_hasText(motorista.email)) {
    items.add('E-mail para convite informado');
  }
  items.add('Empresa vinculada ao portal');
  return items;
}

List<String> _motoristaRequiredProfileItems(AdminMotoristaRecord motorista) {
  final items = <String>[];
  if (!_hasText(motorista.nome) ||
      !_hasText(motorista.email) ||
      !_hasText(motorista.telefone)) {
    items.add('Informar nome, telefone e e-mail');
  }
  return items;
}

List<String> _motoristaRecommendedProfileItems(AdminMotoristaRecord motorista) {
  final items = <String>[];
  if (!_hasText(motorista.fotoPerfilUrl)) {
    items.add('Foto recomendada no app');
  }
  return items;
}

String _motoristaChecklistActionHint(AdminMotoristaRecord motorista) {
  if (_motoristaRequiredProfileItems(motorista).isNotEmpty) {
    return 'Ação: complete os dados cadastrais do motorista.';
  }
  if (!_hasText(motorista.fotoPerfilUrl)) {
    return 'Ação: orientar o motorista a atualizar a foto no app para facilitar identificação.';
  }
  return 'Ação: revise a escala apenas quando serviço ou viagem mudar.';
}

bool _hasText(String? value) => (value ?? '').trim().isNotEmpty;

int _ageInYears(DateTime birthDate) {
  final now = DateTime.now();
  var age = now.year - birthDate.year;
  final hadBirthday =
      now.month > birthDate.month ||
      (now.month == birthDate.month && now.day >= birthDate.day);
  if (!hadBirthday) {
    age--;
  }
  return age < 0 ? 0 : age;
}
