class AdminPortalException implements Exception {
  const AdminPortalException(this.message, {this.code, this.email});

  final String message;
  final String? code;
  final String? email;

  bool get isEmailNotVerified => code == 'EMAIL_NOT_VERIFIED';

  @override
  String toString() => message;
}
