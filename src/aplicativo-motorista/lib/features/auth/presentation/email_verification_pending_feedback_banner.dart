part of 'email_verification_pending_screen.dart';

class _FeedbackBanner extends StatelessWidget {
  const _FeedbackBanner({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
      decoration: BoxDecoration(
        color: OnboardingPalette.driver.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(8),
        border: const Border(
          left: BorderSide(color: OnboardingPalette.driver, width: 4),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.info_outline_rounded,
            color: OnboardingPalette.driver,
            size: 18,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              message,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: OnboardingPalette.driver,
                height: 1.35,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
