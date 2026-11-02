part of 'confirmar_presenca_screen.dart';

class _ServicePresenceBody extends StatelessWidget {
  const _ServicePresenceBody({
    required this.services,
    required this.processing,
    required this.onConfirm,
    required this.onAbsent,
    required this.onClear,
  });

  final List<PassengerAvailableServiceRecord> services;
  final bool processing;
  final ValueChanged<PassengerAvailableServiceRecord> onConfirm;
  final ValueChanged<PassengerAvailableServiceRecord> onAbsent;
  final ValueChanged<PassengerAvailableServiceRecord> onClear;

  @override
  Widget build(BuildContext context) {
    if (services.isNotEmpty) {
      return ListView(
        key: ConfirmarPresencaScreen.serviceListKey,
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 32),
        children: [
          PresenceTodayList(
            services: services,
            processing: processing,
            onConfirm: onConfirm,
            onAbsent: onAbsent,
            onClear: onClear,
          ),
        ],
      );
    }

    return ListView(
      key: ConfirmarPresencaScreen.serviceListKey,
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 32),
      children: [
        const _PresenceHeader(
          eyebrow: 'Hoje',
          title: 'Presença de hoje',
          subtitle: 'Você decide apenas os horários que exigem confirmação.',
        ),
        const SizedBox(height: 22),
        _EmptyPresenceCard(
          message: AppStrings.passageiro.nenhumServicoLiberadoHoje,
        ),
      ],
    );
  }
}

class _LegacyCalendarPresenceBody extends StatelessWidget {
  const _LegacyCalendarPresenceBody({
    required this.today,
    required this.confirmedDays,
    required this.selectedDays,
    required this.processing,
    required this.isSameDay,
    required this.onDaySelected,
    required this.onConfirm,
    required this.onRemove,
  });

  final DateTime today;
  final Set<DateTime> confirmedDays;
  final Set<DateTime> selectedDays;
  final bool processing;
  final bool Function(DateTime a, DateTime b) isSameDay;
  final ValueChanged<DateTime> onDaySelected;
  final VoidCallback onConfirm;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 32),
      children: [
        const _PresenceHeader(
          eyebrow: 'Agenda',
          title: 'Confirmar presença',
          subtitle:
              'Selecione os dias e mantenha a operação avisada sem ruído.',
        ),
        const SizedBox(height: 22),
        _LegacyPresenceActions(
          processing: processing,
          onConfirm: onConfirm,
          onRemove: onRemove,
        ),
        const SizedBox(height: 18),
        VanRidesLiquidSurface.card(
          borderRadius: 28,
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 16),
          child: TableCalendar(
            key: ConfirmarPresencaScreen.calendarKey,
            locale: 'pt_BR',
            firstDay: today.subtract(const Duration(days: 30)),
            lastDay: today.add(const Duration(days: 60)),
            focusedDay: today,
            selectedDayPredicate:
                (day) => selectedDays.any((d) => isSameDay(d, day)),
            onDaySelected: (day, _) => onDaySelected(day),
            headerStyle: const HeaderStyle(
              formatButtonVisible: false,
              titleCentered: true,
              leftChevronIcon: Icon(
                Icons.chevron_left_rounded,
                color: MapFirstPalette.textStrong,
              ),
              rightChevronIcon: Icon(
                Icons.chevron_right_rounded,
                color: MapFirstPalette.textStrong,
              ),
              titleTextStyle: TextStyle(
                color: MapFirstPalette.textStrong,
                fontSize: 17,
                fontWeight: FontWeight.w800,
              ),
            ),
            daysOfWeekStyle: const DaysOfWeekStyle(
              weekdayStyle: TextStyle(
                color: MapFirstPalette.textMuted,
                fontWeight: FontWeight.w700,
              ),
              weekendStyle: TextStyle(
                color: MapFirstPalette.textMuted,
                fontWeight: FontWeight.w700,
              ),
            ),
            calendarStyle: CalendarStyle(
              outsideDaysVisible: false,
              todayDecoration: BoxDecoration(
                color: MapFirstPalette.blue.withValues(alpha: 0.18),
                border: Border.all(
                  color: MapFirstPalette.blue.withValues(alpha: 0.44),
                ),
                shape: BoxShape.circle,
              ),
              todayTextStyle: const TextStyle(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w800,
              ),
              defaultTextStyle: const TextStyle(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w700,
              ),
              weekendTextStyle: const TextStyle(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w700,
              ),
              selectedDecoration: const BoxDecoration(
                color: MapFirstPalette.brandBlue,
                shape: BoxShape.circle,
              ),
              selectedTextStyle: const TextStyle(
                color: VanRidesDesignTokens.onAccent,
                fontWeight: FontWeight.w900,
              ),
            ),
            calendarBuilders: CalendarBuilders(
              defaultBuilder: (context, day, _) {
                return _PresenceDayCell(
                  day: day,
                  confirmed: confirmedDays.any((d) => isSameDay(d, day)),
                  selected: selectedDays.any((d) => isSameDay(d, day)),
                );
              },
              todayBuilder: (context, day, _) {
                return _PresenceDayCell(
                  day: day,
                  today: true,
                  confirmed: confirmedDays.any((d) => isSameDay(d, day)),
                  selected: selectedDays.any((d) => isSameDay(d, day)),
                );
              },
              selectedBuilder: (context, day, _) {
                return _PresenceDayCell(
                  day: day,
                  confirmed: confirmedDays.any((d) => isSameDay(d, day)),
                  selected: true,
                );
              },
            ),
          ),
        ),
      ],
    );
  }
}

class _PresenceHeader extends StatelessWidget {
  const _PresenceHeader({
    required this.eyebrow,
    required this.title,
    required this.subtitle,
  });

  final String eyebrow;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.sheet(
      borderRadius: 30,
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            eyebrow,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: MapFirstPalette.brandBlue,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            title,
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.42,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyPresenceCard extends StatelessWidget {
  const _EmptyPresenceCard({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 54),
      child: VanRidesLiquidSurface.card(
        padding: const EdgeInsets.all(24),
        borderRadius: 28,
        child: Column(
          children: [
            Container(
              width: 62,
              height: 62,
              decoration: BoxDecoration(
                color: MapFirstPalette.blue.withValues(alpha: 0.12),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.event_available_outlined,
                color: MapFirstPalette.brandBlue,
                size: 30,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              message,
              key: ConfirmarPresencaScreen.noServicesTextKey,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w800,
                height: 1.35,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LegacyPresenceActions extends StatelessWidget {
  const _LegacyPresenceActions({
    required this.processing,
    required this.onConfirm,
    required this.onRemove,
  });

  final bool processing;
  final VoidCallback onConfirm;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.card(
      borderRadius: 26,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            AppStrings.passageiro.selecioneAte14Dias,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.4,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 14),
          LayoutBuilder(
            builder: (context, constraints) {
              final compact = constraints.maxWidth < 420;
              final actions = [
                Expanded(
                  child: _PresenceActionButton(
                    key: ConfirmarPresencaScreen.confirmarButtonKey,
                    label: AppStrings.guardian.confirmarSelecionados,
                    icon: Icons.check_rounded,
                    color: MapFirstPalette.green,
                    processing: processing,
                    onPressed: processing ? null : onConfirm,
                  ),
                ),
                Expanded(
                  child: _PresenceActionButton(
                    key: ConfirmarPresencaScreen.removerButtonKey,
                    label: AppStrings.guardian.removerSelecionados,
                    icon: Icons.delete_outline_rounded,
                    color: MapFirstPalette.red,
                    processing: processing,
                    onPressed: processing ? null : onRemove,
                  ),
                ),
              ];

              if (compact) {
                return Column(
                  children: [
                    actions.first,
                    const SizedBox(height: 10),
                    actions.last,
                  ],
                );
              }

              return Row(
                children: [
                  actions.first,
                  const SizedBox(width: 12),
                  actions.last,
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _PresenceDayCell extends StatelessWidget {
  const _PresenceDayCell({
    required this.day,
    this.today = false,
    this.confirmed = false,
    this.selected = false,
  });

  final DateTime day;
  final bool today;
  final bool confirmed;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final color =
        selected
            ? MapFirstPalette.blue
            : confirmed
            ? MapFirstPalette.green
            : today
            ? MapFirstPalette.amber
            : MapFirstPalette.surfaceRaised;
    final foreground =
        selected || confirmed
            ? VanRidesDesignTokens.onAccent
            : MapFirstPalette.textStrong;

    return Container(
      margin: const EdgeInsets.all(5),
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color:
            selected || confirmed
                ? color
                : color.withValues(alpha: today ? 0.18 : 0.52),
        shape: BoxShape.circle,
        border: Border.all(
          color: color.withValues(alpha: selected || confirmed ? 0.0 : 0.28),
        ),
      ),
      child: Text(
        '${day.day}',
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
          color: foreground,
          fontWeight: FontWeight.w900,
        ),
      ),
    );
  }
}

class _PresenceActionButton extends StatelessWidget {
  const _PresenceActionButton({
    super.key,
    required this.label,
    required this.icon,
    required this.color,
    required this.processing,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final Color color;
  final bool processing;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.action(
      padding: EdgeInsets.zero,
      borderRadius: 18,
      child: FilledButton.icon(
        onPressed: onPressed,
        style: FilledButton.styleFrom(
          backgroundColor: color,
          disabledBackgroundColor: MapFirstPalette.surfaceRaised,
          foregroundColor: VanRidesDesignTokens.onAccent,
          disabledForegroundColor: MapFirstPalette.textMuted,
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
          textStyle: Theme.of(
            context,
          ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w900),
        ),
        icon:
            processing
                ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(
                    color: MapFirstPalette.textMuted,
                    strokeWidth: 2,
                  ),
                )
                : Icon(icon, size: 19),
        label: Text(label, overflow: TextOverflow.ellipsis),
      ),
    );
  }
}
