import 'package:flutter/material.dart';

import '../../domain/admin_motorista_record.dart';
import 'admin_portal_layout.dart';
import 'admin_rotas_widgets.dart';

class AdminRotaFormSummary extends StatelessWidget {
  const AdminRotaFormSummary({
    super.key,
    required this.routeName,
    required this.motoristas,
    required this.motoristaId,
    required this.selectedPassageirosCount,
    required this.diasOperacao,
    required this.horarioPartida,
    required this.destinoFinalNome,
    required this.destinoFinalEndereco,
  });

  final String routeName;
  final List<AdminMotoristaRecord> motoristas;
  final int? motoristaId;
  final int selectedPassageirosCount;
  final Set<String> diasOperacao;
  final TimeOfDay? horarioPartida;
  final String? destinoFinalNome;
  final String? destinoFinalEndereco;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        AdminPortalSurface(
          radius: 40,
          color: AdminPortalColors.navy,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const AdminPortalTag(
                label: 'RESUMO DO BACKEND',
                background: Color(0x1FFFFFFF),
                foreground: Colors.white70,
              ),
              const SizedBox(height: 16),
              Text(
                routeName.trim().isEmpty
                    ? 'Nova rota em preparação'
                    : routeName.trim(),
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 30,
                  fontWeight: FontWeight.w800,
                  height: 1.15,
                ),
              ),
              const SizedBox(height: 24),
              AdminRotasDarkInfoLine(
                label: 'Motorista selecionado',
                value:
                    motoristas
                        .where((item) => item.id == motoristaId)
                        .cast<AdminMotoristaRecord?>()
                        .firstWhere((_) => true, orElse: () => null)
                        ?.nome ??
                    'Aguardando seleção',
              ),
              const SizedBox(height: 16),
              AdminRotasDarkInfoLine(
                label: 'Passageiros legados',
                value: '$selectedPassageirosCount selecionados',
              ),
              const SizedBox(height: 16),
              AdminRotasDarkInfoLine(
                label: 'Dias ativos',
                value:
                    diasOperacao.isEmpty
                        ? 'Nenhum dia definido'
                        : diasOperacao.join(' • '),
              ),
              const SizedBox(height: 16),
              AdminRotasDarkInfoLine(
                label: 'Horário de partida',
                value:
                    horarioPartida == null
                        ? 'Sem horário definido'
                        : '${horarioPartida!.hour.toString().padLeft(2, '0')}:${horarioPartida!.minute.toString().padLeft(2, '0')}',
              ),
              const SizedBox(height: 24),
              const Divider(color: Color(0x26FFFFFF)),
              const SizedBox(height: 24),
              AdminRotasDarkInfoLine(
                label: 'Destino final',
                value:
                    destinoFinalNome == null
                        ? 'Sem destino final definido'
                        : destinoFinalNome!,
              ),
              if (destinoFinalEndereco != null) ...[
                const SizedBox(height: 10),
                Text(
                  destinoFinalEndereco!,
                  style: const TextStyle(
                    color: Colors.white70,
                    fontSize: 13,
                    height: 1.6,
                  ),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 24),
        const AdminPortalSurface(
          radius: 40,
          color: AdminPortalColors.orangeSoft,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.warning_amber_rounded, color: Color(0xFF6E3900)),
                  SizedBox(width: 12),
                  Text(
                    'Alinhamento operacional',
                    style: TextStyle(
                      color: Color(0xFF2F1500),
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ),
              SizedBox(height: 12),
              Text(
                'Para manter o portal confiável, estamos exibindo apenas os campos já confirmados na operação. Isso evita telas que parecem completas, mas ainda não garantem consistência de dados.',
                style: TextStyle(
                  color: Color(0xFF6E3900),
                  fontSize: 14,
                  height: 1.6,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
