class GuardianExperienceException implements Exception {
  const GuardianExperienceException(this.message);

  final String message;

  @override
  String toString() => message;
}
