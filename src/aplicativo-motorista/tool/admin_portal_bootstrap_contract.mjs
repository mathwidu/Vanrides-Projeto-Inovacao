export function requireCompanyName(value) {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new TypeError('companyName is required');
  }
  return value.trim();
}

export function deriveBackendCompanySlug(value) {
  const normalized = requireCompanyName(value)
    .toLowerCase()
    .replace(/[^a-z0-9-]/g, '-')
    .replace(/-{2,}/g, '-')
    .replace(/^-|-$/g, '');

  return normalized;
}

export function buildAdminBootstrapPayload({
  companyName,
  nome,
  email,
  telefone,
  senha,
}) {
  return {
    companyName: requireCompanyName(companyName),
    nome,
    email,
    telefone,
    senha,
  };
}

export function shouldBootstrapAdmin(environment) {
  return !(
    typeof environment?.admin_email === 'string' &&
    environment.admin_email.length > 0 &&
    typeof environment?.admin_password === 'string' &&
    environment.admin_password.length > 0
  );
}

export function requireBootstrapResponseCompanySlug(body) {
  const companySlug = body?.companySlug;
  if (typeof companySlug !== 'string' || companySlug.trim().length === 0) {
    throw new TypeError('bootstrap response companySlug is required');
  }
  return companySlug.trim();
}
