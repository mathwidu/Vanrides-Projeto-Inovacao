import { chromium } from 'playwright';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  buildAdminBootstrapPayload,
  requireBootstrapResponseCompanySlug,
  shouldBootstrapAdmin,
} from './admin_portal_bootstrap_contract.mjs';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(scriptDir, '..');

const appUrl = process.env.ADMIN_PORTAL_E2E_URL ?? 'http://127.0.0.1:7357';
const apiBaseUrl =
  process.env.VANRIDES_API_BASE_URL ?? 'http://127.0.0.1:8080';
const runId = process.env.ADMIN_PORTAL_E2E_RUN_ID ?? `manual-${Date.now()}`;
const reportDir = process.env.ADMIN_PORTAL_E2E_REPORT_DIR
  ? path.resolve(rootDir, process.env.ADMIN_PORTAL_E2E_REPORT_DIR)
  : path.resolve(rootDir, 'output/admin-portal-e2e', runId);
const companyName =
  process.env.ADMIN_PORTAL_E2E_COMPANY_NAME ?? `Portal E2E ${runId}`;
const actorKey =
  runId
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '.')
    .replace(/^\.+|\.+$/g, '')
    .slice(0, 48) || `${Date.now()}`;
const usesExistingAdminFixture = !shouldBootstrapAdmin(process.env);
const adminName = process.env.ADMIN_PORTAL_E2E_NAME ?? 'Admin E2E';
const adminEmail =
  process.env.ADMIN_PORTAL_E2E_EMAIL ??
  process.env.admin_email ??
  `admin.${actorKey}@vanrides.dev`;
const adminPhone = process.env.ADMIN_PORTAL_E2E_PHONE ?? '51999999999';
const adminPassword =
  process.env.ADMIN_PORTAL_E2E_PASSWORD ??
  process.env.admin_password ??
  'Admin@1234';
const screenshotDir = process.env.ADMIN_PORTAL_E2E_SCREENSHOT_DIR
  ? path.resolve(rootDir, process.env.ADMIN_PORTAL_E2E_SCREENSHOT_DIR)
  : path.join(reportDir, 'screenshots');
const hasOperationalFixture =
  Boolean(process.env.service_morning_id) &&
  Boolean(process.env.service_afternoon_id) &&
  Boolean(process.env.service_night_id) &&
  Boolean(process.env.trip_morning_id) &&
  Boolean(process.env.trip_afternoon_id) &&
  Boolean(process.env.trip_night_id);
const fixtureMorningServiceName =
  process.env.service_morning_name ?? 'Linha Manhã - Escola';
const fixtureAfternoonServiceName =
  process.env.service_afternoon_name ?? 'Linha Tarde - Retorno';
const fixtureNightServiceName =
  process.env.service_night_name ?? 'Linha Noite - Atividade';
const fixtureDriver2Name = process.env.driver_2_name ?? 'M2 Reserva';

const report = {
  schemaVersion: 1,
  runId,
  status: 'RUNNING',
  startedAt: new Date().toISOString(),
  endedAt: null,
  appUrl,
  apiBaseUrl,
  actor: {
    adminEmail: maskEmail(adminEmail),
    companyName,
    companySlug: usesExistingAdminFixture
      ? (process.env.company_slug ?? null)
      : null,
  },
  fixture: {
    mode: hasOperationalFixture ? 'operational' : 'bootstrap-only',
    source: process.env.ADMIN_PORTAL_E2E_FIXTURE_SOURCE ?? null,
    ids: {
      serviceMorningId: process.env.service_morning_id ?? null,
      serviceAfternoonId: process.env.service_afternoon_id ?? null,
      serviceNightId: process.env.service_night_id ?? null,
      tripMorningId: process.env.trip_morning_id ?? null,
      tripAfternoonId: process.env.trip_afternoon_id ?? null,
      tripNightId: process.env.trip_night_id ?? null,
    },
  },
  steps: [],
  screenshots: [],
  limits: hasOperationalFixture
    ? []
    : ['Sem fixture operacional completa; validacao limitada a bootstrap/login/navegacao comum.'],
  pending: [],
  failures: {
    setup: [],
    uxContract: [],
  },
};

function maskEmail(value) {
  const [local, domain] = value.split('@');
  if (!domain) {
    return 'invalid-email';
  }
  return `${local.slice(0, 2)}***@${domain}`;
}

function relativeToRoot(target) {
  return path.relative(rootDir, target);
}

function relativeToReport(target) {
  return path.relative(reportDir, target);
}

async function ensureReportDirs() {
  await fs.mkdir(reportDir, { recursive: true });
  await fs.mkdir(screenshotDir, { recursive: true });
}

function serializeError(error) {
  return {
    message: error?.message ?? String(error),
    stack: error?.stack ? String(error.stack).split('\n').slice(0, 8) : [],
  };
}

async function runStep(name, kind, action) {
  const startedAt = Date.now();
  try {
    await action();
    report.steps.push({
      name,
      kind,
      status: 'PASS',
      durationMs: Date.now() - startedAt,
    });
  } catch (error) {
    const failure = {
      step: name,
      kind,
      ...serializeError(error),
    };
    if (kind === 'setup') {
      report.failures.setup.push(failure);
    } else {
      report.failures.uxContract.push(failure);
    }
    report.steps.push({
      name,
      kind,
      status: 'FAIL',
      durationMs: Date.now() - startedAt,
      error: failure.message,
    });
    throw error;
  }
}

async function writeReports(status) {
  report.status = status;
  report.endedAt = new Date().toISOString();
  await fs.writeFile(
    path.join(reportDir, 'manifest.json'),
    `${JSON.stringify(report, null, 2)}\n`,
  );
  await fs.writeFile(path.join(reportDir, 'SUMMARY.md'), buildMarkdownSummary());
}

function buildMarkdownSummary() {
  const lines = [
    '# Admin Portal Web E2E',
    '',
    `Status: ${report.status}`,
    `Run ID: ${report.runId}`,
    `API: ${report.apiBaseUrl}`,
    `App: ${report.appUrl}`,
    `Fixture: ${report.fixture.mode}`,
    '',
    '## Passos',
    ...report.steps.map((step) => `- [${step.status === 'PASS' ? 'x' : ' '}] ${step.name} (${step.kind}, ${step.durationMs}ms)`),
    '',
    '## Screenshots',
    ...(report.screenshots.length === 0
      ? ['- Nenhum screenshot capturado.']
      : report.screenshots.map((shot) => `- ${shot.name}: \`${shot.path}\``)),
    '',
    '## Falhas de setup',
    ...(report.failures.setup.length === 0
      ? ['- Nenhuma.']
      : report.failures.setup.map((failure) => `- ${failure.step}: ${failure.message}`)),
    '',
    '## Falhas de UX/contrato',
    ...(report.failures.uxContract.length === 0
      ? ['- Nenhuma.']
      : report.failures.uxContract.map((failure) => `- ${failure.step}: ${failure.message}`)),
    '',
    '## Limites',
    ...(report.limits.length === 0 ? ['- Nenhum limite operacional registrado.'] : report.limits.map((item) => `- ${item}`)),
    '',
    '## Pendencias',
    ...(report.pending.length === 0 ? ['- Nenhuma pendencia registrada.'] : report.pending.map((item) => `- ${item}`)),
    '',
  ];
  return `${lines.join('\n')}\n`;
}

async function fillByLabelOrFallback(page, label, inputIndex, value) {
  const byLabel = page.getByLabel(label, { exact: false });
  if ((await byLabel.count()) > 0) {
    await fillFlutterInput(page, byLabel.first(), value);
    return;
  }

  await fillFlutterInput(page, page.locator('input').nth(inputIndex), value);
}

async function fillFlutterInput(page, locator, value) {
  await locator.focus();
  await page.waitForTimeout(150);
  await page.keyboard.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A');
  await page.keyboard.insertText(value);
  await page.waitForTimeout(150);
}

async function bootstrapFirstAdmin() {
  if (!shouldBootstrapAdmin(process.env)) {
    console.log('Usando admin da fixture operacional existente');
    return;
  }

  const response = await fetch(`${apiBaseUrl}/company-admins/bootstrap`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(
      buildAdminBootstrapPayload({
        companyName,
        nome: adminName,
        email: adminEmail,
        telefone: adminPhone,
        senha: adminPassword,
      }),
    ),
  });

  if (response.status !== 201) {
    const body = await response.text();
    throw new Error(`Bootstrap HTTP ${response.status}: ${body}`);
  }

  const body = await response.json();
  report.actor.companySlug = requireBootstrapResponseCompanySlug(body);
}

async function clickByText(page, text) {
  await page.getByRole('button', { name: text, exact: true }).click();
}

async function clickFirstAvailableButton(page, names, timeout = 30000) {
  const escapedNames = names.map((name) =>
    name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'),
  );
  await page
    .getByRole('button', { name: new RegExp(`^(${escapedNames.join('|')})$`) })
    .first()
    .click({ timeout });
}

async function clickNavButton(page, text) {
  await page.getByRole('button', { name: text, exact: true }).first().click();
}

async function waitForExactText(page, text, timeout = 30000) {
  await page.getByText(text, { exact: true }).first().waitFor({ timeout });
}

async function waitForText(page, text, timeout = 30000) {
  await page.getByText(text).first().waitFor({ timeout });
}

async function waitForButtonNamed(page, names, timeout = 30000) {
  const escapedNames = names.map((name) =>
    name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'),
  );
  await page
    .getByRole('button', { name: new RegExp(`^(${escapedNames.join('|')})$`) })
    .first()
    .waitFor({ timeout });
}

async function scrollPageDown(page, amount = 900) {
  const viewport = page.viewportSize() ?? { width: 1440, height: 1080 };
  await page.mouse.move(Math.round(viewport.width * 0.65), Math.round(viewport.height * 0.78));
  await page.mouse.wheel(0, amount);
  await page.waitForTimeout(500);
}

async function capture(page, name) {
  await page.waitForTimeout(800);
  if ((await page.getByText('Link desconhecido.', { exact: true }).count()) > 0) {
    await page.waitForTimeout(11500);
  }

  const screenshotPath = path.join(screenshotDir, `${name}.png`);
  await page.screenshot({
    path: screenshotPath,
    fullPage: true,
  });
  report.screenshots.push({
    name,
    path: relativeToReport(screenshotPath),
    rootPath: relativeToRoot(screenshotPath),
  });
}

async function validateOperationalFixturePortal(page) {
  console.log('Validando Serviços recorrentes da fixture operacional');
  await clickNavButton(page, 'Serviços');
  await waitForExactText(page, 'Serviços recorrentes');
  await waitForExactText(page, fixtureMorningServiceName);
  await waitForExactText(page, fixtureAfternoonServiceName);
  await waitForExactText(page, fixtureNightServiceName);
  await waitForExactText(page, 'Escala por viagem');

  const directReviewButton = page.getByRole('button', {
    name: `Abrir ${fixtureNightServiceName}`,
    exact: true,
  });
  if ((await directReviewButton.count()) > 0) {
    await directReviewButton.first().click();
  } else {
    await page.getByRole('button', { name: 'Abrir', exact: true }).nth(2).click();
  }
  await waitForExactText(page, 'Prontidão operacional');
  await waitForExactText(page, 'Passageiros elegíveis');
  await waitForExactText(page, 'Motorista');

  console.log('Validando Viagens operacionais da fixture');
  await clickNavButton(page, 'Viagens do dia');
  await waitForExactText(page, 'Viagens do dia');
  await waitForExactText(page, fixtureMorningServiceName);
  await waitForExactText(page, fixtureAfternoonServiceName);
  await waitForExactText(page, fixtureNightServiceName);
  await waitForExactText(page, fixtureDriver2Name);
}

await ensureReportDirs();
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1080 } });

page.on('console', (msg) => {
  if (msg.type() === 'error') {
    console.error(`[browser:${msg.type()}] ${msg.text()}`);
  }
});

try {
  await runStep('Bootstrap admin/fixture', 'setup', async () => {
    console.log(`Bootstrapando admin em ${apiBaseUrl}`);
    await bootstrapFirstAdmin();
  });

  await runStep('Login no portal admin', 'setup', async () => {
    console.log(`Abrindo app web em ${appUrl}`);
    await page.goto(appUrl, { waitUntil: 'networkidle' });

    await clickFirstAvailableButton(page, ['Abrir gestao', 'Abrir gestão', 'Abrir portal']);
    await page
      .getByRole('button', {
        name: 'Primeiro acesso? Criar admin da empresa',
        exact: true,
      })
      .waitFor();
    await fillByLabelOrFallback(page, 'E-mail administrativo', 0, adminEmail);
    await fillByLabelOrFallback(page, 'Senha', 1, adminPassword);

    await clickByText(page, 'Entrar no portal');
    await page.getByText('Dashboard Operacional', { exact: true }).waitFor({ timeout: 30000 });
    await capture(page, 'admin-dashboard');
  });

  await runStep('Viagens do dia', 'ux_contract', async () => {
    await clickNavButton(page, 'Viagens do dia');
    await waitForExactText(page, 'Viagens do dia');
    await capture(page, 'admin-trips');
  });

  await runStep('Serviços recorrentes', 'ux_contract', async () => {
    await clickNavButton(page, 'Serviços');
    await waitForExactText(page, 'Serviços recorrentes');
    await capture(page, 'admin-services');
  });

  await runStep('Motoristas', 'ux_contract', async () => {
    await clickNavButton(page, 'Motoristas');
    await waitForButtonNamed(page, [
      'Convidar por link',
      'Cadastrar manualmente',
      'Novo link',
      'Convidar motorista',
    ]);
    await capture(page, 'admin-drivers');
  });

  await runStep('Passageiros e diálogos contextuais', 'ux_contract', async () => {
    await clickNavButton(page, 'Passageiros');
    await page.getByRole('button', { name: 'Cadastrar Passageiro', exact: true }).waitFor({ timeout: 30000 });
    await capture(page, 'admin-passengers');
    await waitForExactText(page, 'Entrada de passageiros');
    await page.getByText('Entrada de passageiros', { exact: true }).scrollIntoViewIfNeeded();
    await capture(page, 'admin-passenger-entry');
    await scrollPageDown(page, 1100);
    await waitForExactText(page, 'Ficha do passageiro');
    await capture(page, 'admin-passenger-detail');
    const editPassengerServicesButton = page
      .getByRole('button', { name: 'Editar serviços', exact: true })
      .first();
    await editPassengerServicesButton.scrollIntoViewIfNeeded();
    await capture(page, 'admin-passenger-services');
    await editPassengerServicesButton.click();
    await waitForButtonNamed(page, ['Salvar serviços']);
    await capture(page, 'admin-passenger-services-dialog');
    await clickByText(page, 'Cancelar');
    await page.waitForTimeout(500);
    await scrollPageDown(page, 900);
    await waitForButtonNamed(page, ['Gerenciar responsáveis']);
    await capture(page, 'admin-passenger-guardians');
    await page
      .getByRole('button', { name: 'Gerenciar responsáveis', exact: true })
      .first()
      .click();
    await waitForButtonNamed(page, ['Vincular responsável']);
    await capture(page, 'admin-passenger-guardians-dialog');
    await clickByText(page, 'Fechar');
  });

  await runStep('Responsáveis e diálogos contextuais', 'ux_contract', async () => {
    await clickNavButton(page, 'Responsáveis');
    await page.getByRole('button', { name: 'Novo Responsável', exact: true }).waitFor({ timeout: 30000 });
    await capture(page, 'admin-guardians');
    await waitForButtonNamed(page, ['Gerenciar passageiros']);
    await page.getByRole('button', { name: 'Gerenciar passageiros', exact: true }).scrollIntoViewIfNeeded();
    await capture(page, 'admin-guardian-passengers');
    await page.getByRole('button', { name: 'Gerenciar passageiros', exact: true }).click();
    await waitForButtonNamed(page, ['Vincular passageiro']);
    await capture(page, 'admin-guardian-passengers-dialog');
    await clickByText(page, 'Fechar');
  });

  await runStep('Navegação comum sem vínculos avançados', 'ux_contract', async () => {
    const advancedLinks = await page.getByRole('button', { name: 'Vínculos avançados', exact: true }).count();
    if (advancedLinks !== 0) {
      throw new Error('Vínculos avançados não deve aparecer na navegação comum do admin');
    }
  });

  await runStep('Conta administrativa', 'ux_contract', async () => {
    await clickNavButton(page, 'Conta');
    await page
      .getByRole('button', { name: 'Editar perfil', exact: true })
      .waitFor({ timeout: 30000 });
    await capture(page, 'admin-account');
  });

  if (hasOperationalFixture) {
    await runStep('Fixture operacional completa', 'ux_contract', async () => {
      await validateOperationalFixturePortal(page);
    });
  }

  await writeReports('PASS');
  console.log(`PASS: bootstrap + login + navegação comum do portal admin`);
  console.log(`Relatório visual: ${relativeToRoot(reportDir)}`);
} catch (error) {
  if (report.failures.setup.length > 0) {
    report.pending.push('Corrigir setup antes de usar as evidencias visuais como aceitacao de UX.');
  }
  if (report.failures.uxContract.length > 0) {
    report.pending.push('Corrigir tela, contrato ou seletor apontado no passo com falha.');
  }
  await writeReports('FAIL');
  console.error(`FAIL: relatório visual salvo em ${relativeToRoot(reportDir)}`);
  throw error;
} finally {
  await browser.close();
}
