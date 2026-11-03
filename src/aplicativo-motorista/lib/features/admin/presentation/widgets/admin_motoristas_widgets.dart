import 'package:flutter/material.dart';

import '../../domain/admin_motorista_record.dart';
import 'admin_person_avatar.dart';
import 'admin_portal_layout.dart';

class AdminMotoristasListPanel extends StatelessWidget {
  const AdminMotoristasListPanel({
    super.key,
    required this.motoristas,
    required this.selectedMotorista,
    required this.onSelect,
    required this.onCreate,
    required this.onRefresh,
  });

  final List<AdminMotoristaRecord> motoristas;
  final AdminMotoristaRecord? selectedMotorista;
  final ValueChanged<AdminMotoristaRecord> onSelect;
  final VoidCallback onCreate;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 40,
      padding: const EdgeInsets.all(8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 18, 24, 18),
            child: Row(
              children: [
                const Expanded(
                  child: Text(
                    'Motoristas da empresa',
                    style: TextStyle(
                      color: AdminPortalColors.text,
                      fontSize: 24,
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
          Container(height: 1, color: AdminPortalColors.divider),
          const Padding(
            padding: EdgeInsets.fromLTRB(24, 12, 24, 12),
            child: Row(
              children: [
                Expanded(
                  flex: 3,
                  child: Text(
                    'MOTORISTA',
                    style: TextStyle(
                      color: AdminPortalColors.textMuted,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1,
                    ),
                  ),
                ),
                Expanded(
                  flex: 3,
                  child: Text(
                    'CONTATO',
                    style: TextStyle(
                      color: AdminPortalColors.textMuted,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1,
                    ),
                  ),
                ),
                Expanded(
                  flex: 2,
                  child: Text(
                    'STATUS',
                    style: TextStyle(
                      color: AdminPortalColors.textMuted,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1,
                    ),
                  ),
                ),
                SizedBox(
                  width: 116,
                  child: Text(
                    'PRÓXIMO PASSO',
                    textAlign: TextAlign.right,
                    style: TextStyle(
                      color: AdminPortalColors.textMuted,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: AdminPortalColors.divider),
          if (motoristas.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 48, horizontal: 24),
              child: Center(
                child: Column(
                  children: [
                    const Text(
                      'Nenhum motorista vinculado à empresa.',
                      style: TextStyle(
                        color: AdminPortalColors.text,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 12),
                    AdminPortalPrimaryButton(
                      label: 'Cadastrar manualmente',
                      icon: Icons.person_add_alt_1_rounded,
                      onPressed: onCreate,
                    ),
                  ],
                ),
              ),
            )
          else
            ListView.separated(
              itemCount: motoristas.length,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              separatorBuilder:
                  (_, __) => const Divider(
                    height: 1,
                    color: AdminPortalColors.divider,
                  ),
              itemBuilder: (context, index) {
                final motorista = motoristas[index];
                final isSelected = motorista.id == selectedMotorista?.id;
                return InkWell(
                  onTap: () => onSelect(motorista),
                  child: Container(
                    color:
                        isSelected
                            ? AdminPortalColors.canvas
                            : Colors.transparent,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 24,
                      vertical: 18,
                    ),
                    child: Row(
                      children: [
                        Expanded(
                          flex: 3,
                          child: Row(
                            children: [
                              AdminPersonAvatar(
                                name: motorista.nome,
                                photoUrl: motorista.fotoPerfilUrl,
                                radius: 20,
                                background: AdminPortalColors.blueSoft,
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      motorista.nome,
                                      style: const TextStyle(
                                        color: AdminPortalColors.text,
                                        fontSize: 16,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                    const SizedBox(height: 4),
                                    AdminPortalTag(
                                      label:
                                          isSelected
                                              ? 'SELECIONADO'
                                              : 'VINCULADO',
                                      background:
                                          isSelected
                                              ? AdminPortalColors.blueSoft
                                              : AdminPortalColors.greenSoft,
                                      foreground:
                                          isSelected
                                              ? AdminPortalColors.blue
                                              : AdminPortalColors.green,
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                        Expanded(
                          flex: 3,
                          child: _MotoristaContactInfo(
                            phone: _formatPhone(motorista.telefone),
                            email: motorista.email,
                          ),
                        ),
                        const Expanded(
                          flex: 2,
                          child: Row(
                            children: [
                              Icon(
                                Icons.check_circle_outline_rounded,
                                size: 14,
                                color: AdminPortalColors.green,
                              ),
                              SizedBox(width: 8),
                              Flexible(
                                child: Text(
                                  'Pronto para escala',
                                  style: TextStyle(
                                    color: AdminPortalColors.textMuted,
                                    fontSize: 14,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                        SizedBox(
                          width: 116,
                          child: Align(
                            alignment: Alignment.centerRight,
                            child: _MotoristaTableAction(
                              label: 'Ver ficha',
                              icon: Icons.badge_outlined,
                              onPressed: () => onSelect(motorista),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
            child: Text(
              '${motoristas.length} motorista(s) vinculado(s) à empresa',
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 12,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MotoristaTableAction extends StatelessWidget {
  const _MotoristaTableAction({
    required this.label,
    required this.icon,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return TextButton.icon(
      onPressed: onPressed,
      icon: Icon(icon, size: 16),
      label: Text(label),
      style: TextButton.styleFrom(
        foregroundColor: AdminPortalColors.textMuted,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        minimumSize: const Size(0, 36),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800),
      ),
    );
  }
}

class _MotoristaContactInfo extends StatelessWidget {
  const _MotoristaContactInfo({required this.phone, required this.email});

  final String phone;
  final String email;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          phone,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            color: AdminPortalColors.textMuted,
            fontSize: 14,
            height: 1.3,
          ),
        ),
        const SizedBox(height: 3),
        Text(
          email,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            color: AdminPortalColors.textMuted,
            fontSize: 14,
            height: 1.3,
          ),
        ),
      ],
    );
  }
}

String _formatPhone(String phone) {
  final digits = phone.replaceAll(RegExp(r'\D'), '');
  if (digits.length < 10) {
    return phone;
  }
  final area = digits.substring(0, 2);
  final first = digits.substring(2, digits.length - 4);
  final last = digits.substring(digits.length - 4);
  return '($area) $first-$last';
}
