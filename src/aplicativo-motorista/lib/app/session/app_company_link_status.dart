enum AppCompanyLinkStatus {
  noCompany,
  pendingApproval,
  companyLinked;

  static AppCompanyLinkStatus fromBackendValue(String? rawValue) {
    return switch (rawValue?.trim().toUpperCase()) {
      'NO_COMPANY' => AppCompanyLinkStatus.noCompany,
      'PENDING_APPROVAL' => AppCompanyLinkStatus.pendingApproval,
      _ => AppCompanyLinkStatus.companyLinked,
    };
  }
}
