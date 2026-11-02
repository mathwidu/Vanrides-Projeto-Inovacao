import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import 'admin_portal_layout.dart';

part 'admin_servicos_recorrentes_readiness_widgets.dart';

class AdminServicoDetailHero extends StatelessWidget {
  const AdminServicoDetailHero({
    super.key,
    required this.servico,
    this.onReviewTrips,
  });

  final AdminServicoRecorrenteRecord servico;
  final VoidCallback? onReviewTrips;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: AdminPortalColors.navy,
        borderRadius: BorderRadius.circular(32),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    AdminPortalTag(
                      label: servico.ativo ? 'Ativo' : 'Inativo',
                      background:
                          servico.ativo
                              ? AdminPortalColors.greenSoft
                              : AdminPortalColors.redSoft,
                      foreground:
                          servico.ativo
                              ? AdminPortalColors.green
                              : AdminPortalColors.red,
                    ),
                    AdminPortalTag(
                      label: servico.tipoOperacaoLabel,
                      background: AdminPortalColors.blueSoft,
                      foreground: AdminPortalColors.blue,
                    ),
                    AdminPortalTag(
                      label: _readinessLabel(servico),
                      background: _readinessBackground(servico),
                      foreground: _readinessForeground(servico),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                Text(
                  _serviceDestinationLabel(servico),
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    height: 1.2,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  '${_textOrDash(servico.horarioPartida)} • ${servico.diasOperacao.isEmpty ? 'sem dias definidos' : servico.diasOperacao.join(', ')}',
                  style: const TextStyle(
                    color: Color(0xCCDDE6FF),
                    fontSize: 14,
                    height: 1.4,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 24),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                servico.quantidadePassageiros.toString(),
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 36,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const Text(
                'elegíveis',
                style: TextStyle(color: Color(0xCCDDE6FF), fontSize: 13),
              ),
              const SizedBox(height: 16),
              Text(
                servico.motoristaPadrao?.nome ?? 'Escala por viagem',
                textAlign: TextAlign.end,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                ),
              ),
              if (onReviewTrips != null) ...[
                const SizedBox(height: 18),
                AdminPortalPrimaryButton(
                  label: 'Montar viagem de hoje',
                  icon: Icons.departure_board_outlined,
                  onPressed: onReviewTrips,
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class AdminServicoRulesSurface extends StatelessWidget {
  const AdminServicoRulesSurface({super.key, required this.servico});

  final AdminServicoRecorrenteRecord servico;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _AdminServicoDetailSectionTitle('Regra operacional'),
          const SizedBox(height: 18),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              _RuleDatum(label: 'Tipo', value: servico.tipoOperacaoLabel),
              _RuleDatum(
                label: 'Presença',
                value:
                    _politicaOptions[servico.politicaPresenca] ??
                    servico.politicaPresenca,
              ),
              _RuleDatum(
                label: 'Horário',
                value: _textOrDash(servico.horarioPartida),
              ),
              _RuleDatum(
                label: 'Destino',
                value: _serviceDestinationLabel(servico),
              ),
              _RuleDatum(
                label: 'Capacidade',
                value:
                    servico.capacidadePlanejada == null
                        ? 'Não informada'
                        : '${servico.capacidadePlanejada} lugares',
              ),
              _RuleDatum(
                label: 'Origem do modelo',
                value:
                    servico.rotaBaseLegadaId == null
                        ? 'Serviço recorrente'
                        : 'Rota base #${servico.rotaBaseLegadaId}',
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _RuleDatum extends StatelessWidget {
  const _RuleDatum({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 210,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label.toUpperCase(),
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 11,
              fontWeight: FontWeight.w800,
              letterSpacing: 1,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            value,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 15,
              fontWeight: FontWeight.w700,
              height: 1.3,
            ),
          ),
        ],
      ),
    );
  }
}

class AdminServicoEligiblePassengersSurface extends StatelessWidget {
  const AdminServicoEligiblePassengersSurface({
    super.key,
    required this.passageiros,
    required this.totalIds,
  });

  final List<AdminPassageiroRecord> passageiros;
  final int totalIds;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      padding: const EdgeInsets.all(0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 14),
            child: Row(
              children: [
                const Expanded(
                  child: _AdminServicoDetailSectionTitle(
                    'Passageiros elegíveis',
                  ),
                ),
                AdminPortalTag(
                  label: '$totalIds vínculos',
                  background: AdminPortalColors.surfaceSoft,
                  foreground: AdminPortalColors.textMuted,
                ),
              ],
            ),
          ),
          if (passageiros.isEmpty)
            const Padding(
              padding: EdgeInsets.fromLTRB(24, 0, 24, 24),
              child: Text(
                'Nenhum passageiro encontrado para os vínculos deste serviço.',
                style: TextStyle(color: AdminPortalColors.textMuted),
              ),
            )
          else
            for (final passageiro in passageiros)
              _EligiblePassengerRow(passageiro: passageiro),
        ],
      ),
    );
  }
}

class _EligiblePassengerRow extends StatelessWidget {
  const _EligiblePassengerRow({required this.passageiro});

  final AdminPassageiroRecord passageiro;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      decoration: const BoxDecoration(
        border: Border(top: BorderSide(color: AdminPortalColors.divider)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(
                      passageiro.nome,
                      style: const TextStyle(
                        color: AdminPortalColors.text,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    if (passageiro.menorDeIdade)
                      const AdminPortalTag(
                        label: 'Menor',
                        background: AdminPortalColors.orangeSoft,
                        foreground: AdminPortalColors.orange,
                      ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  passageiro.email,
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 13,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 16),
          Text(
            passageiro.endereco.trim().isEmpty
                ? 'Sem endereço'
                : 'Endereço cadastrado',
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminServicoDetailSectionTitle extends StatelessWidget {
  const _AdminServicoDetailSectionTitle(this.text);

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

String _textOrDash(String? value) {
  final trimmed = value?.trim() ?? '';
  return trimmed.isEmpty ? '--:--' : trimmed;
}

String _serviceDestinationLabel(AdminServicoRecorrenteRecord servico) {
  final nome = servico.destinoFinalNome?.trim() ?? '';
  if (nome.isNotEmpty) return nome;

  final endereco = servico.destinoFinalEndereco?.trim() ?? '';
  if (endereco.isNotEmpty) return endereco;

  return 'Destino não publicado';
}
