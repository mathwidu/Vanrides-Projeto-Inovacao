import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_record.dart';
import 'admin_person_avatar.dart';
import 'admin_portal_layout.dart';

export 'admin_passageiros_info_widgets.dart'
    show AdminPassageiroInfoCard, AdminPassageirosActivitySection;

class AdminPassageirosListPanel extends StatelessWidget {
  const AdminPassageirosListPanel({
    super.key,
    required this.passageiros,
    required this.selectedPassageiro,
    required this.onRefresh,
    required this.onCreate,
    required this.onEdit,
    required this.onReassign,
    required this.onSelect,
  });

  final List<AdminPassageiroRecord> passageiros;
  final AdminPassageiroRecord? selectedPassageiro;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onCreate;
  final Future<void> Function() onEdit;
  final Future<void> Function() onReassign;
  final ValueChanged<AdminPassageiroRecord> onSelect;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 48,
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 20),
            child: Row(
              children: [
                const Expanded(
                  child: Text(
                    'Lista de Passageiros',
                    style: TextStyle(
                      color: AdminPortalColors.text,
                      fontSize: 28,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                TextButton.icon(
                  onPressed: onRefresh,
                  icon: const Icon(Icons.refresh_rounded, size: 18),
                  label: const Text('Atualizar'),
                  style: TextButton.styleFrom(
                    foregroundColor: AdminPortalColors.textMuted,
                    textStyle: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
              ],
            ),
          ),
          Container(
            decoration: const BoxDecoration(
              color: AdminPortalColors.surfaceSoft,
              border: Border(
                top: BorderSide(color: AdminPortalColors.divider),
                bottom: BorderSide(color: AdminPortalColors.divider),
              ),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
            child: const Row(
              children: [
                SizedBox(width: 34),
                Expanded(
                  flex: 4,
                  child: Text(
                    'PASSAGEIRO',
                    style: adminPassageirosTableHeaderStyle,
                  ),
                ),
                Expanded(
                  flex: 3,
                  child: Text(
                    'ATALHO OPERACIONAL',
                    style: adminPassageirosTableHeaderStyle,
                  ),
                ),
                SizedBox(
                  width: 124,
                  child: Text(
                    'STATUS',
                    style: adminPassageirosTableHeaderStyle,
                    textAlign: TextAlign.center,
                  ),
                ),
                SizedBox(
                  width: 188,
                  child: Text(
                    'PRÓXIMO PASSO',
                    style: adminPassageirosTableHeaderStyle,
                    textAlign: TextAlign.right,
                  ),
                ),
              ],
            ),
          ),
          if (passageiros.isEmpty)
            Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Nenhum passageiro encontrado para esta empresa.'),
                  const SizedBox(height: 16),
                  AdminPortalPrimaryButton(
                    label: 'Cadastrar passageiro',
                    icon: Icons.person_add_alt_1,
                    onPressed: onCreate,
                  ),
                ],
              ),
            )
          else
            ...passageiros.map(
              (passageiro) => _AdminPassageiroTableRow(
                passageiro: passageiro,
                selected: passageiro.id == selectedPassageiro?.id,
                onSelect: () => onSelect(passageiro),
                onEdit: onEdit,
                onReassign: onReassign,
              ),
            ),
          AdminPassageirosListFooter(total: passageiros.length),
        ],
      ),
    );
  }
}

class AdminPassageirosListFooter extends StatelessWidget {
  const AdminPassageirosListFooter({super.key, required this.total});

  final int total;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 20, 24, 24),
      child: Row(
        children: [
          Text(
            'Mostrando $total ${total == 1 ? 'passageiro' : 'passageiros'}',
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 12,
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminPassageiroTableRow extends StatelessWidget {
  const _AdminPassageiroTableRow({
    required this.passageiro,
    required this.selected,
    required this.onSelect,
    required this.onEdit,
    required this.onReassign,
  });

  final AdminPassageiroRecord passageiro;
  final bool selected;
  final VoidCallback onSelect;
  final Future<void> Function() onEdit;
  final Future<void> Function() onReassign;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onSelect,
      child: Container(
        decoration: BoxDecoration(
          color:
              selected
                  ? AdminPortalColors.surfaceSoft
                  : AdminPortalColors.elevatedSurface.withValues(alpha: 0.64),
          border: const Border(
            bottom: BorderSide(color: AdminPortalColors.divider),
          ),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 18),
        child: Row(
          children: [
            Icon(
              Icons.radio_button_unchecked,
              size: 18,
              color:
                  selected ? AdminPortalColors.blue : AdminPortalColors.outline,
            ),
            const SizedBox(width: 16),
            Expanded(flex: 4, child: _PassengerIdentityCell(passageiro)),
            Expanded(flex: 3, child: _PassengerOperationalShortcut(passageiro)),
            SizedBox(
              width: 124,
              child: Center(
                child: AdminPortalTag(
                  label: passageiro.motoristaId == null ? 'PENDENTE' : 'ATIVO',
                  background:
                      passageiro.motoristaId == null
                          ? AdminPortalColors.orangeSoft
                          : AdminPortalColors.greenSoft,
                  foreground:
                      passageiro.motoristaId == null
                          ? AdminPortalColors.warningText
                          : AdminPortalColors.green,
                ),
              ),
            ),
            SizedBox(
              width: 188,
              child: Wrap(
                alignment: WrapAlignment.end,
                spacing: 4,
                runSpacing: 4,
                children: [
                  _PassengerTableAction(
                    label: 'Editar',
                    icon: Icons.edit_outlined,
                    onPressed: selected ? onEdit : null,
                  ),
                  _PassengerTableAction(
                    label: 'Motorista',
                    icon: Icons.link_outlined,
                    onPressed: selected ? onReassign : null,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PassengerTableAction extends StatelessWidget {
  const _PassengerTableAction({
    required this.label,
    required this.icon,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return TextButton.icon(
      onPressed: onPressed,
      icon: Icon(icon, size: 16),
      label: Text(label),
      style: TextButton.styleFrom(
        foregroundColor: AdminPortalColors.textMuted,
        disabledForegroundColor: AdminPortalColors.textFaint,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        minimumSize: const Size(0, 36),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800),
      ),
    );
  }
}

class _PassengerIdentityCell extends StatelessWidget {
  const _PassengerIdentityCell(this.passageiro);

  final AdminPassageiroRecord passageiro;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        AdminPersonAvatar(
          name: passageiro.nome,
          photoUrl: passageiro.fotoPerfilUrl,
          radius: 18,
          background:
              passageiro.menorDeIdade
                  ? AdminPortalColors.orangeSoft
                  : AdminPortalColors.blueSoft,
          foreground: AdminPortalColors.text,
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                passageiro.nome,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _passengerSubtitle(passageiro),
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 12,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _PassengerOperationalShortcut extends StatelessWidget {
  const _PassengerOperationalShortcut(this.passageiro);

  final AdminPassageiroRecord passageiro;

  @override
  Widget build(BuildContext context) {
    if (passageiro.motoristaId == null) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: AdminPortalColors.statusSurface,
          borderRadius: BorderRadius.circular(999),
          border: Border.all(color: AdminPortalColors.outline),
        ),
        child: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Sem atalho',
              style: TextStyle(
                color: AdminPortalColors.text,
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
            Text(
              'Libere serviços',
              style: TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 11,
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AdminPortalColors.blue,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Rota operacional',
            style: TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
          Text(
            'Motorista: ${passageiro.motoristaNome ?? '—'}',
            style: const TextStyle(color: Colors.white70, fontSize: 11),
          ),
        ],
      ),
    );
  }
}

const adminPassageirosTableHeaderStyle = TextStyle(
  color: AdminPortalColors.textMuted,
  fontSize: 12,
  fontWeight: FontWeight.w700,
  letterSpacing: 0.8,
);

String _passengerSubtitle(AdminPassageiroRecord passageiro) {
  final profile = _birthProfileLabel(
    passageiro.dataNascimento,
    minor: passageiro.menorDeIdade,
  );
  if (profile == null) {
    return passageiro.email;
  }
  return '$profile • ${passageiro.email}';
}

String? _birthProfileLabel(String? dataNascimento, {required bool minor}) {
  final date = DateTime.tryParse(dataNascimento?.trim() ?? '');
  final age = date == null ? null : _ageInYears(date);
  final ageLabel = age == null ? null : '$age anos';
  final minorLabel = minor ? 'menor de idade' : 'maior de idade';
  return ageLabel == null ? minorLabel : '$ageLabel, $minorLabel';
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
