part of 'cadastro_passageiro_screen.dart';

class _PassengerCompanyBindingHint extends StatelessWidget {
  const _PassengerCompanyBindingHint();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: OnboardingPalette.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: OnboardingPalette.border.withValues(alpha: 0.7),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.apartment_rounded,
            color: OnboardingPalette.passenger,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              AppStrings.passageiro.passageiroCompanyBindingHint,
              style: const TextStyle(
                color: OnboardingPalette.textMuted,
                fontSize: 14,
                height: 1.5,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PassengerStopGuidanceCard extends StatelessWidget {
  const _PassengerStopGuidanceCard();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: OnboardingPalette.passengerSoft,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: OnboardingPalette.passenger.withValues(alpha: 0.18),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.route_outlined, color: OnboardingPalette.passenger),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Escolha o endereço da parada. Depois confirme visualmente o ponto no mapa antes de criar a conta.',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: OnboardingPalette.textStrong,
                height: 1.45,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PassengerStopMapConfirmation extends StatelessWidget {
  const _PassengerStopMapConfirmation({required this.address});

  final String address;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: OnboardingPalette.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: OnboardingPalette.border.withValues(alpha: 0.72),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 70,
            height: 70,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFF102536), Color(0xFF153F54)],
              ),
            ),
            child: const Center(
              child: Icon(
                Icons.location_on_rounded,
                color: OnboardingPalette.passenger,
                size: 34,
              ),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Ponto no mapa confirmado',
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: OnboardingPalette.textStrong,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  address,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: OnboardingPalette.textMuted,
                    height: 1.4,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _PasswordRequirementsCard extends StatelessWidget {
  const _PasswordRequirementsCard({required this.password});

  final String password;

  bool get _hasUpper => password.contains(RegExp(r'[A-Z]'));
  bool get _hasLower => password.contains(RegExp(r'[a-z]'));
  bool get _hasNumber => password.contains(RegExp(r'[0-9]'));
  bool get _hasSpecial => password.contains(RegExp(r'[!@#$%^&*(),.?":{}|<>]'));
  bool get _hasLength => password.length >= 8;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: OnboardingPalette.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: OnboardingPalette.border.withValues(alpha: 0.75),
        ),
      ),
      child: Column(
        children: [
          OnboardingPasswordRequirement(
            label: AppStrings.passageiro.peloMenos8Caracteres,
            valid: _hasLength,
          ),
          const SizedBox(height: 8),
          OnboardingPasswordRequirement(
            label: AppStrings.passageiro.umaLetraMaiuscula,
            valid: _hasUpper,
          ),
          const SizedBox(height: 8),
          OnboardingPasswordRequirement(
            label: AppStrings.passageiro.umaLetraMinuscula,
            valid: _hasLower,
          ),
          const SizedBox(height: 8),
          OnboardingPasswordRequirement(
            label: AppStrings.passageiro.umNumero,
            valid: _hasNumber,
          ),
          const SizedBox(height: 8),
          OnboardingPasswordRequirement(
            label: AppStrings.passageiro.umCaractereEspecial,
            valid: _hasSpecial,
          ),
        ],
      ),
    );
  }
}
