import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import '../../../service_presence/domain/passenger_available_service_record.dart';
import 'admin_passageiro_guardians_section.dart';
import 'admin_passageiro_info_status_pill.dart';
import 'admin_passageiro_presence_today_section.dart';
import 'admin_passageiro_profile_checklist.dart';
import 'admin_passageiro_services_section.dart';
import 'admin_person_detail_components.dart';
import 'admin_portal_layout.dart';

class AdminPassageirosActivitySection extends StatelessWidget {
  const AdminPassageirosActivitySection({
    super.key,
    this.selectedPassageiro,
    this.servicos = const [],
    this.presenceServices = const [],
    this.isLoadingPresenceToday = false,
    this.presenceTodayError,
    this.onEditProfile,
    this.onEditServicos,
    this.onManageResponsaveis,
    this.onReviewTrips,
  });

  final AdminPassageiroRecord? selectedPassageiro;
  final List<AdminServicoRecorrenteRecord> servicos;
  final List<PassengerAvailableServiceRecord> presenceServices;
  final bool isLoadingPresenceToday;
  final String? presenceTodayError;
  final Future<void> Function()? onEditProfile;
  final Future<void> Function()? onEditServicos;
  final Future<void> Function()? onManageResponsaveis;
  final VoidCallback? onReviewTrips;

  @override
  Widget build(BuildContext context) {
    final selected = selectedPassageiro;
    final enabledServicos =
        selected == null
            ? const <AdminServicoRecorrenteRecord>[]
            : servicos
                .where((servico) => servico.passageiroIds.contains(selected.id))
                .toList(growable: false);
    final List<Widget> cards =
        selected == null
            ? _emptyInfoCards()
            : _infoCardsFor(selected, enabledServicos);
    final showProfileChecklist =
        selected != null &&
        hasAdminPassageiroProfileChecklistItems(
          selected,
          enabledServicosCount: enabledServicos.length,
        );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Ficha do passageiro',
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
            color: AdminPortalColors.text,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 16),
        LayoutBuilder(
          builder: (context, constraints) {
            if (constraints.maxWidth < 980) {
              return Column(
                children: [
                  for (var i = 0; i < cards.length; i++) ...[
                    cards[i],
                    if (i < cards.length - 1) const SizedBox(height: 16),
                  ],
                ],
              );
            }

            return Row(
              children: [
                for (var i = 0; i < cards.length; i++) ...[
                  Expanded(child: cards[i]),
                  if (i < cards.length - 1) const SizedBox(width: 16),
                ],
              ],
            );
          },
        ),
        const SizedBox(height: 16),
        if (showProfileChecklist) ...[
          AdminPassageiroProfileChecklist(
            passageiro: selected,
            enabledServicosCount: enabledServicos.length,
            hasServicosCatalog: servicos.isNotEmpty,
            onEditProfile: onEditProfile,
            onEditServicos: onEditServicos,
            onManageResponsaveis: onManageResponsaveis,
          ),
          const SizedBox(height: 16),
        ],
        if (selected != null) ...[
          _PassengerAdminDataSection(passageiro: selected),
          const SizedBox(height: 16),
        ],
        AdminPassageiroPresenceTodaySection(
          passageiro: selected,
          services: presenceServices,
          isLoading: isLoadingPresenceToday,
          errorMessage: presenceTodayError,
          onReviewTrips: onReviewTrips,
        ),
        const SizedBox(height: 16),
        AdminPassageiroServicosSection(
          passageiro: selected,
          servicos: servicos,
          onEdit: onEditServicos,
        ),
        const SizedBox(height: 16),
        AdminPassageiroResponsaveisSection(
          passageiro: selected,
          onManage: onManageResponsaveis,
        ),
      ],
    );
  }

  List<AdminPassageiroInfoCard> _emptyInfoCards() {
    return const [
      AdminPassageiroInfoCard(
        accent: AdminPortalColors.textMuted,
        icon: Icons.person_search_outlined,
        title: 'SELEÇÃO PENDENTE',
        statusLabel: 'SEM SELEÇÃO',
        body: 'Selecione um passageiro para ver vínculo, contato e endereço.',
        footer: 'Os detalhes aparecem conforme o registro carregado.',
      ),
    ];
  }

  List<Widget> _infoCardsFor(
    AdminPassageiroRecord passageiro,
    List<AdminServicoRecorrenteRecord> enabledServicos,
  ) {
    final motoristaNome =
        passageiro.motorista?.nome ?? passageiro.motoristaNome;
    final hasMotorista = passageiro.motoristaId != null;
    final nextAction = _passengerNextAction(
      passageiro: passageiro,
      enabledServicosCount: enabledServicos.length,
      hasServicosCatalog: servicos.isNotEmpty,
      onEditProfile: onEditProfile,
      onEditServicos: onEditServicos,
      onManageResponsaveis: onManageResponsaveis,
    );

    return [
      AdminPassageiroInfoCard(
        accent: AdminPortalColors.blue,
        icon: Icons.person_outline,
        title: 'Identidade',
        statusLabel:
            passageiro.menorDeIdade ? 'Menor de idade' : 'Maior de idade',
        body: passageiro.nome,
        footer: _responsavelSummary(passageiro),
      ),
      AdminPassageiroInfoCard(
        accent: hasMotorista ? AdminPortalColors.green : AdminPortalColors.blue,
        icon: Icons.directions_bus_filled_outlined,
        title: 'Operação',
        statusLabel: hasMotorista ? 'ATIVO' : 'SEM ATALHO',
        body:
            hasMotorista
                ? 'Motorista: ${_valueOrFallback(motoristaNome, 'Não informado')}'
                : 'Sem motorista de referência',
        footer:
            hasMotorista
                ? 'Atalho ativo para operação e reatribuição.'
                : 'Serviços liberados definem a viagem do dia.',
      ),
      AdminPersonNextActionCard(
        actionLabel: nextAction.label,
        body: nextAction.body,
        icon: nextAction.icon,
        accent: nextAction.accent,
        onAction: nextAction.onAction,
      ),
    ];
  }
}

class AdminPassageiroInfoCard extends StatelessWidget {
  const AdminPassageiroInfoCard({
    super.key,
    required this.accent,
    required this.icon,
    required this.title,
    required this.body,
    required this.footer,
    this.statusLabel,
  });

  final Color accent;
  final IconData icon;
  final String title;
  final String body;
  final String? footer;
  final String? statusLabel;

  @override
  Widget build(BuildContext context) {
    final bodyLines = body.split('\n');

    return Container(
      decoration: BoxDecoration(
        color: AdminPortalColors.elevatedSurface.withValues(alpha: 0.82),
        borderRadius: BorderRadius.circular(32),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: accent, size: 18),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title,
                  style: TextStyle(
                    color: accent,
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0,
                  ),
                ),
              ),
              if (statusLabel != null)
                AdminPassageiroInfoStatusPill(label: statusLabel!),
            ],
          ),
          const SizedBox(height: 14),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (var i = 0; i < bodyLines.length; i++) ...[
                Text(
                  bodyLines[i],
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 14,
                    height: 1.45,
                  ),
                ),
                if (i < bodyLines.length - 1) const SizedBox(height: 2),
              ],
            ],
          ),
          if (footer != null) ...[
            const SizedBox(height: 16),
            Text(
              footer!,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 11,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _PassengerAdminDataSection extends StatelessWidget {
  const _PassengerAdminDataSection({required this.passageiro});

  final AdminPassageiroRecord passageiro;

  @override
  Widget build(BuildContext context) {
    return AdminDetailDisclosureSection(
      title: 'Dados administrativos',
      subtitle: 'Contato, idade, endereço e georreferência do passageiro.',
      icon: Icons.contact_mail_outlined,
      accent: AdminPortalColors.textMuted,
      child: Column(
        children: [
          _PassengerDetailRow(
            icon: Icons.mail_outline,
            label: 'E-mail',
            value: _valueOrFallback(passageiro.email, 'E-mail não informado'),
          ),
          const SizedBox(height: 12),
          _PassengerDetailRow(
            icon: Icons.call_outlined,
            label: 'Telefone',
            value: _valueOrFallback(
              passageiro.telefone,
              'Telefone não informado',
            ),
          ),
          const SizedBox(height: 12),
          _PassengerDetailRow(
            icon: Icons.cake_outlined,
            label: 'Nascimento',
            value: _profileBirthLine(passageiro),
          ),
          const SizedBox(height: 12),
          _PassengerDetailRow(
            icon: Icons.home_work_outlined,
            label: 'Endereço',
            value: _addressLabel(passageiro),
          ),
          const SizedBox(height: 12),
          _PassengerDetailRow(
            icon: Icons.my_location_outlined,
            label: 'Roteirização',
            value:
                _hasCoordinates(passageiro)
                    ? 'Coordenadas salvas para roteirização'
                    : 'Sem coordenadas salvas',
          ),
        ],
      ),
    );
  }
}

class _PassengerDetailRow extends StatelessWidget {
  const _PassengerDetailRow({
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
          width: 34,
          height: 34,
          decoration: BoxDecoration(
            color: AdminPortalColors.statusSurface,
            borderRadius: BorderRadius.circular(17),
            border: Border.all(color: AdminPortalColors.outline),
          ),
          child: Icon(icon, color: AdminPortalColors.blue, size: 17),
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

class _PassengerNextAction {
  const _PassengerNextAction({
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

_PassengerNextAction _passengerNextAction({
  required AdminPassageiroRecord passageiro,
  required int enabledServicosCount,
  required bool hasServicosCatalog,
  required Future<void> Function()? onEditProfile,
  required Future<void> Function()? onEditServicos,
  required Future<void> Function()? onManageResponsaveis,
}) {
  if (_hasRequiredPassengerIssue(passageiro)) {
    return _PassengerNextAction(
      label: 'Completar cadastro',
      body: 'Corrija os dados obrigatórios antes de depender da operação.',
      icon: Icons.playlist_add_check_circle_outlined,
      accent: AdminPortalColors.orange,
      onAction:
          onEditProfile == null
              ? null
              : () {
                onEditProfile();
              },
    );
  }

  if (passageiro.menorDeIdade &&
      passageiro.responsavelCount == 0 &&
      passageiro.responsaveis.isEmpty) {
    return _PassengerNextAction(
      label: 'Vincular responsável',
      body: 'Passageiros menores precisam de um responsável ativo.',
      icon: Icons.supervisor_account_outlined,
      accent: AdminPortalColors.orange,
      onAction:
          onManageResponsaveis == null
              ? null
              : () {
                onManageResponsaveis();
              },
    );
  }

  if (!hasServicosCatalog || enabledServicosCount == 0) {
    return _PassengerNextAction(
      label: hasServicosCatalog ? 'Liberar serviços' : 'Criar serviços',
      body: 'Sem serviço recorrente liberado, o passageiro não entra no dia.',
      icon: Icons.route_outlined,
      accent: AdminPortalColors.blue,
      onAction:
          onEditServicos == null
              ? null
              : () {
                onEditServicos();
              },
    );
  }

  return _PassengerNextAction(
    label: 'Pronto para viagens recorrentes',
    body:
        enabledServicosCount == 1
            ? '1 serviço liberado. Revise apenas se houver exceção.'
            : '$enabledServicosCount serviços liberados. Revise apenas se houver exceção.',
    icon: Icons.verified_user_outlined,
    accent: AdminPortalColors.green,
  );
}

bool _hasRequiredPassengerIssue(AdminPassageiroRecord passageiro) {
  return !_hasText(passageiro.nome) ||
      !_hasText(passageiro.email) ||
      !_hasText(passageiro.telefone) ||
      DateTime.tryParse(passageiro.dataNascimento?.trim() ?? '') == null ||
      !_hasText(passageiro.endereco) ||
      !_hasText(passageiro.cidade) ||
      !_hasText(passageiro.estado);
}

String _responsavelSummary(AdminPassageiroRecord passageiro) {
  final count =
      passageiro.responsavelCount == 0
          ? passageiro.responsaveis.length
          : passageiro.responsavelCount;
  return count == 1
      ? '1 responsável vinculado'
      : '$count responsáveis vinculados';
}

String _addressLabel(AdminPassageiroRecord passageiro) {
  final parts =
      [
        passageiro.endereco,
        passageiro.bairro,
        passageiro.cidade,
        passageiro.estado,
        passageiro.cep,
      ].map((item) => item.trim()).where((item) => item.isNotEmpty).toList();

  return parts.isEmpty ? 'Endereço não informado' : parts.join(', ');
}

String _valueOrFallback(String? value, String fallback) {
  final trimmed = value?.trim() ?? '';
  return trimmed.isEmpty ? fallback : trimmed;
}

String _profileBirthLine(AdminPassageiroRecord passageiro) {
  final birth = _formatIsoDate(passageiro.dataNascimento);
  if (birth == null) {
    return passageiro.menorDeIdade
        ? 'Nascimento pendente, marcado como menor'
        : 'Nascimento pendente';
  }
  final age = _ageInYears(DateTime.parse(passageiro.dataNascimento!));
  final minorLabel = passageiro.menorDeIdade ? 'menor' : 'maior';
  return 'Nascimento: $birth, $age anos ($minorLabel)';
}

String? _formatIsoDate(String? value) {
  final date = DateTime.tryParse(value?.trim() ?? '');
  if (date == null) {
    return null;
  }
  return '${date.day.toString().padLeft(2, '0')}/'
      '${date.month.toString().padLeft(2, '0')}/'
      '${date.year}';
}

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

bool _hasCoordinates(AdminPassageiroRecord passageiro) =>
    passageiro.latitude != null && passageiro.longitude != null;

bool _hasText(String? value) => (value ?? '').trim().isNotEmpty;
