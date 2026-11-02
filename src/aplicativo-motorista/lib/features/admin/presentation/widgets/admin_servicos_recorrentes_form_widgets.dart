import 'package:flutter/material.dart';

import '../../domain/admin_motorista_record.dart';
import 'admin_portal_layout.dart';

class AdminServicoFormOperationalSummary extends StatelessWidget {
  const AdminServicoFormOperationalSummary({
    super.key,
    required this.tipoOperacao,
    required this.horarioPartida,
    required this.politicaPresenca,
    required this.geracaoAutomaticaAtiva,
    required this.motoristaPadraoValue,
    required this.motoristas,
    required this.selectedPassengers,
    required this.totalPassengers,
  });

  final String tipoOperacao;
  final String horarioPartida;
  final String politicaPresenca;
  final bool geracaoAutomaticaAtiva;
  final int motoristaPadraoValue;
  final List<AdminMotoristaRecord> motoristas;
  final int selectedPassengers;
  final int totalPassengers;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        AdminPortalTag(
          label: adminServicoFormOperationLabel(tipoOperacao, horarioPartida),
          background: AdminPortalColors.blueSoft,
          foreground: AdminPortalColors.blue,
        ),
        AdminPortalTag(
          label: '$selectedPassengers de $totalPassengers elegíveis',
          background:
              selectedPassengers == 0
                  ? AdminPortalColors.orangeSoft
                  : AdminPortalColors.greenSoft,
          foreground:
              selectedPassengers == 0
                  ? AdminPortalColors.orange
                  : AdminPortalColors.green,
        ),
        AdminPortalTag(
          label:
              adminServicoPoliticaOptions[politicaPresenca] ?? politicaPresenca,
          background: AdminPortalColors.surfaceSoft,
          foreground: AdminPortalColors.textMuted,
        ),
        AdminPortalTag(
          label:
              geracaoAutomaticaAtiva ? 'Gera viagem diária' : 'Montagem manual',
          background:
              geracaoAutomaticaAtiva
                  ? AdminPortalColors.greenSoft
                  : AdminPortalColors.orangeSoft,
          foreground:
              geracaoAutomaticaAtiva
                  ? AdminPortalColors.green
                  : AdminPortalColors.orange,
        ),
        AdminPortalTag(
          label: adminServicoFormDriverLabel(motoristaPadraoValue, motoristas),
          background: AdminPortalColors.surfaceSoft,
          foreground: AdminPortalColors.textMuted,
        ),
      ],
    );
  }
}

class AdminServicoFormSectionTitle extends StatelessWidget {
  const AdminServicoFormSectionTitle(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(
        color: AdminPortalColors.text,
        fontSize: 22,
        fontWeight: FontWeight.w800,
      ),
    );
  }
}

class AdminServicoFormFieldLabel extends StatelessWidget {
  const AdminServicoFormFieldLabel(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(
        color: AdminPortalColors.text,
        fontSize: 13,
        fontWeight: FontWeight.w700,
      ),
    );
  }
}

class AdminServicoFormHintBanner extends StatelessWidget {
  const AdminServicoFormHintBanner({
    super.key,
    required this.icon,
    required this.text,
    required this.color,
    required this.background,
  });

  final IconData icon;
  final String text;
  final Color color;
  final Color background;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                color: color,
                fontSize: 13,
                height: 1.35,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

InputDecoration adminServicoInputDecoration(String label) {
  return InputDecoration(
    labelText: label,
    filled: true,
    fillColor: AdminPortalColors.input,
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: BorderSide.none,
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: BorderSide.none,
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(18),
      borderSide: const BorderSide(color: AdminPortalColors.blue, width: 1.5),
    ),
  );
}

String normalizeAdminServicoSearch(String value) {
  return value.trim().toLowerCase();
}

String? validateAdminServicoOptionalTime(String? value) {
  final raw = value?.trim() ?? '';
  if (raw.isEmpty) return null;
  final pattern = RegExp(r'^\d{2}:\d{2}$');
  if (!pattern.hasMatch(raw)) return 'Use o formato HH:mm.';
  final hour = int.tryParse(raw.substring(0, 2));
  final minute = int.tryParse(raw.substring(3, 5));
  if (hour == null || minute == null || hour > 23 || minute > 59) {
    return 'Informe um horário válido.';
  }
  return null;
}

String? validateAdminServicoOptionalPositiveInt(String? value) {
  final raw = value?.trim() ?? '';
  if (raw.isEmpty) return null;
  final parsed = int.tryParse(raw);
  if (parsed == null || parsed < 1) {
    return 'Informe um número maior que zero.';
  }
  return null;
}

String? emptyToNullAdminServicoValue(String value) {
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}

String adminServicoTextOrDash(String? value) {
  final trimmed = value?.trim() ?? '';
  return trimmed.isEmpty ? '--:--' : trimmed;
}

String adminServicoFormOperationLabel(
  String tipoOperacao,
  String horarioPartida,
) {
  final tipo = adminServicoTipoOperacaoOptions[tipoOperacao] ?? tipoOperacao;
  final horario = horarioPartida.trim();
  return horario.isEmpty ? tipo : '$horario $tipo';
}

String adminServicoFormDriverLabel(
  int motoristaPadraoValue,
  List<AdminMotoristaRecord> motoristas,
) {
  if (motoristaPadraoValue == 0) return 'Escala por viagem';
  final motorista = motoristas.cast<AdminMotoristaRecord?>().firstWhere(
    (item) => item?.id == motoristaPadraoValue,
    orElse: () => null,
  );
  return motorista?.nome ?? 'Motorista padrão';
}

List<String> orderedAdminServicoDays(Set<String> days) {
  return adminServicoDayOptions.where(days.contains).toList(growable: false);
}

const adminServicoDayOptions = [
  'SEG',
  'TER',
  'QUA',
  'QUI',
  'SEX',
  'SAB',
  'DOM',
];

const adminServicoTipoOperacaoOptions = {
  'IDA': 'Ida',
  'VOLTA': 'Volta',
  'CIRCULAR': 'Circular',
  'FRETADO': 'Fretado',
};

const adminServicoPoliticaOptions = {
  'INCLUIR_POR_PADRAO': 'Incluir por padrão',
  'EXIGE_CONFIRMACAO': 'Exige confirmação',
};
