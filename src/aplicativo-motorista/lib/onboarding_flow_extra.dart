part of 'onboarding_flow_components.dart';

class VanRidesBirthDate {
  const VanRidesBirthDate._();

  static DateTime? parse(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return null;

    final brMatch = RegExp(r'^(\d{2})/(\d{2})/(\d{4})$').firstMatch(trimmed);
    if (brMatch != null) {
      return _strictDate(
        int.parse(brMatch.group(3)!),
        int.parse(brMatch.group(2)!),
        int.parse(brMatch.group(1)!),
      );
    }

    final isoMatch = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(trimmed);
    if (isoMatch != null) {
      return _strictDate(
        int.parse(isoMatch.group(1)!),
        int.parse(isoMatch.group(2)!),
        int.parse(isoMatch.group(3)!),
      );
    }

    return null;
  }

  static String? validate(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Data de nascimento obrigatória';
    }

    final date = parse(value);
    if (date == null) return 'Informe a data no formato dd/mm/aaaa';
    if (!_isPast(date)) return 'Informe uma data no passado';
    return null;
  }

  static String? toIsoString(String value) {
    final date = parse(value);
    return date == null ? null : formatIso(date);
  }

  static String formatIso(DateTime date) {
    return '${date.year.toString().padLeft(4, '0')}-'
        '${date.month.toString().padLeft(2, '0')}-'
        '${date.day.toString().padLeft(2, '0')}';
  }

  static bool isMinor(DateTime birthDate, {DateTime? today}) {
    final reference = today ?? DateTime.now();
    var age = reference.year - birthDate.year;
    final birthdayReached =
        reference.month > birthDate.month ||
        (reference.month == birthDate.month && reference.day >= birthDate.day);
    if (!birthdayReached) age--;
    return age < 18;
  }

  static DateTime? _strictDate(int year, int month, int day) {
    final date = DateTime(year, month, day);
    if (date.year != year || date.month != month || date.day != day) {
      return null;
    }
    return date;
  }

  static bool _isPast(DateTime date) {
    final today = DateTime.now();
    final normalizedToday = DateTime(today.year, today.month, today.day);
    final normalizedDate = DateTime(date.year, date.month, date.day);
    return normalizedDate.isBefore(normalizedToday);
  }
}

class OnboardingStepProgress extends StatelessWidget {
  const OnboardingStepProgress({
    super.key,
    required this.steps,
    this.activeIndex = 0,
    this.accentColor = OnboardingPalette.passenger,
  });

  final List<String> steps;
  final int activeIndex;
  final Color accentColor;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (var index = 0; index < steps.length; index++)
          _OnboardingStepChip(
            index: index,
            label: steps[index],
            active: index == activeIndex,
            complete: index < activeIndex,
            accentColor: accentColor,
          ),
      ],
    );
  }
}

class _OnboardingStepChip extends StatelessWidget {
  const _OnboardingStepChip({
    required this.index,
    required this.label,
    required this.active,
    required this.complete,
    required this.accentColor,
  });

  final int index;
  final String label;
  final bool active;
  final bool complete;
  final Color accentColor;

  @override
  Widget build(BuildContext context) {
    final foreground =
        active || complete
            ? OnboardingPalette.background
            : OnboardingPalette.textMuted;
    final background =
        active || complete
            ? accentColor
            : OnboardingPalette.surfaceRaised.withValues(alpha: 0.84);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(
          color:
              active
                  ? accentColor
                  : OnboardingPalette.border.withValues(alpha: 0.6),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            '${index + 1}',
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: foreground,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
              color: foreground,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class OnboardingRoleCard extends StatelessWidget {
  const OnboardingRoleCard({
    super.key,
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.iconColor,
    required this.iconBackground,
    required this.onTap,
    this.cardKey,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final Color iconColor;
  final Color iconBackground;
  final VoidCallback onTap;
  final Key? cardKey;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        key: cardKey,
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Ink(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: OnboardingPalette.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: OnboardingPalette.border.withValues(alpha: 0.7),
            ),
            boxShadow: const [
              BoxShadow(
                color: Color(0x3310161F),
                blurRadius: 20,
                offset: Offset(0, 10),
              ),
            ],
          ),
          child: Row(
            children: [
              Container(
                width: 54,
                height: 54,
                decoration: BoxDecoration(
                  color: iconBackground,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(icon, color: iconColor, size: 28),
              ),
              const SizedBox(width: 18),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        color: OnboardingPalette.textStrong,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subtitle,
                      style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: OnboardingPalette.textMuted,
                        height: 1.45,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              const Icon(
                Icons.chevron_right_rounded,
                color: OnboardingPalette.textMuted,
                size: 24,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class OnboardingPrimaryButton extends StatelessWidget {
  const OnboardingPrimaryButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.buttonKey,
    this.backgroundColor,
    this.gradient,
    this.showArrow = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final Key? buttonKey;
  final Color? backgroundColor;
  final Gradient? gradient;
  final bool showArrow;

  @override
  Widget build(BuildContext context) {
    final foregroundColor = Colors.white;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        key: buttonKey,
        onTap: onPressed,
        borderRadius: BorderRadius.circular(16),
        child: Ink(
          height: 56,
          decoration: BoxDecoration(
            color: backgroundColor,
            gradient: gradient,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: (backgroundColor ?? OnboardingPalette.passenger)
                    .withValues(alpha: 0.22),
                blurRadius: 20,
                offset: const Offset(0, 10),
              ),
            ],
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Flexible(
                  child: Text(
                    label,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: foregroundColor,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                if (showArrow) ...[
                  const SizedBox(width: 8),
                  const Icon(
                    Icons.arrow_forward_rounded,
                    color: Colors.white,
                    size: 20,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class OnboardingFooterPrompt extends StatelessWidget {
  const OnboardingFooterPrompt({
    super.key,
    required this.prefix,
    required this.actionLabel,
    required this.onTap,
  });

  final String prefix;
  final String actionLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final style = Theme.of(context).textTheme.bodyLarge?.copyWith(
      color: OnboardingPalette.textMuted,
      fontWeight: FontWeight.w500,
    );
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(prefix, style: style),
        TextButton(
          onPressed: onTap,
          style: TextButton.styleFrom(
            foregroundColor: OnboardingPalette.passenger,
            textStyle: style?.copyWith(
              color: OnboardingPalette.passenger,
              fontWeight: FontWeight.w800,
            ),
          ),
          child: Text(actionLabel),
        ),
      ],
    );
  }
}

class OnboardingStaticField extends StatelessWidget {
  const OnboardingStaticField({
    super.key,
    required this.label,
    required this.value,
    this.trailing,
  });

  final String label;
  final String value;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        OnboardingFieldLabel(label),
        const SizedBox(height: 6),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          decoration: BoxDecoration(
            color: OnboardingPalette.fieldFill,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  value.isEmpty ? 'Ainda não preenchido' : value,
                  style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    color:
                        value.isEmpty
                            ? OnboardingPalette.textMuted.withValues(
                              alpha: 0.72,
                            )
                            : OnboardingPalette.textStrong,
                  ),
                ),
              ),
              if (trailing != null) trailing!,
            ],
          ),
        ),
      ],
    );
  }
}

class OnboardingPasswordRequirement extends StatelessWidget {
  const OnboardingPasswordRequirement({
    super.key,
    required this.label,
    required this.valid,
  });

  final String label;
  final bool valid;

  @override
  Widget build(BuildContext context) {
    final color =
        valid ? OnboardingPalette.driver : OnboardingPalette.textMuted;
    return Row(
      children: [
        Icon(
          valid
              ? Icons.check_circle_rounded
              : Icons.radio_button_unchecked_rounded,
          size: 16,
          color: color,
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            label,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: color,
              fontWeight: valid ? FontWeight.w700 : FontWeight.w500,
            ),
          ),
        ),
      ],
    );
  }
}
