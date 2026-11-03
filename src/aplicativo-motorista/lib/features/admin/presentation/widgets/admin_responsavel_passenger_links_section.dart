import 'package:flutter/material.dart';

import '../../domain/admin_responsavel_record.dart';
import 'admin_person_avatar.dart';
import 'admin_portal_layout.dart';

class AdminResponsavelPassengerLinksSection extends StatelessWidget {
  const AdminResponsavelPassengerLinksSection({
    super.key,
    required this.responsavel,
    required this.onManagePassageiros,
  });

  final AdminResponsavelRecord responsavel;
  final Future<void> Function() onManagePassageiros;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 40,
      color: AdminPortalColors.surfaceSoft,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Expanded(
                child: Text(
                  'Vínculos atuais',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              AdminPortalTag(
                label: _passageiroCountLabel(responsavel.passageiroCount),
                background: AdminPortalColors.blueSoft,
                foreground: AdminPortalColors.blue,
              ),
            ],
          ),
          const SizedBox(height: 16),
          if (responsavel.passageiros.isEmpty)
            const Text('Nenhum passageiro vinculado ainda.')
          else
            ...responsavel.passageiros.map(
              (passageiro) => Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: Row(
                  children: [
                    AdminPersonAvatar(
                      name: passageiro.nome,
                      photoUrl: passageiro.fotoPerfilUrl,
                      radius: 22,
                      background: AdminPortalColors.blueSoft,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            passageiro.nome,
                            style: const TextStyle(
                              color: AdminPortalColors.text,
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          Text(
                            '${passageiro.menorDeIdade ? 'Filho' : 'Dependente'} • acompanha serviços liberados',
                            style: const TextStyle(
                              color: AdminPortalColors.textMuted,
                              fontSize: 11,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const Icon(
                      Icons.arrow_forward,
                      color: AdminPortalColors.textMuted,
                      size: 18,
                    ),
                  ],
                ),
              ),
            ),
          OutlinedButton.icon(
            onPressed: onManagePassageiros,
            icon: const Icon(Icons.link_rounded),
            label: const Text('Gerenciar passageiros'),
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(48),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(999),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

String _passageiroCountLabel(int count) {
  if (count == 1) {
    return '1 passageiro vinculado';
  }
  return '$count passageiros vinculados';
}
