import http from 'node:http';
import { fileURLToPath } from 'node:url';

import {
  deriveBackendCompanySlug,
  requireCompanyName,
} from './admin_portal_bootstrap_contract.mjs';

const host = process.env.ADMIN_PORTAL_E2E_API_HOST ?? '127.0.0.1';
const port = Number(process.env.ADMIN_PORTAL_E2E_API_PORT ?? '7358');

const motoristas = [
  { id: 1, nome: 'Motorista Portal', email: 'motorista.portal@vanrides.dev', telefone: '51999990001' },
  { id: 2, nome: 'Motorista Reserva', email: 'motorista.reserva@vanrides.dev', telefone: '51999990002' },
];

const passageiros = [
  {
    id: 10,
    nome: 'Passageiro Portal',
    email: 'passageiro.portal@vanrides.dev',
    telefone: '51988880001',
    endereco: 'Rua das Flores, 123',
    bairro: 'Centro',
    cidade: 'Porto Alegre',
    estado: 'RS',
    cep: '90000-000',
    menorDeIdade: true,
    motorista: motoristas[0],
    responsavelCount: 1,
    responsaveis: [],
  },
];

const responsaveis = [
  {
    id: 20,
    nome: 'Responsavel Portal',
    email: 'responsavel.portal@vanrides.dev',
    telefone: '51977770001',
    passageiroCount: 1,
    passageiros: [
      {
        id: 10,
        nome: 'Passageiro Portal',
        email: 'passageiro.portal@vanrides.dev',
        menorDeIdade: true,
        motorista: motoristas[0],
      },
    ],
  },
];

const passageiroMotoristaLinks = [
  {
    passageiroId: 10,
    passageiroNome: 'Passageiro Portal',
    menorDeIdade: true,
    motorista: motoristas[0],
  },
];

const responsavelPassageiroLinks = [
  {
    responsavel: responsaveis[0],
    passageiro: {
      id: 10,
      nome: 'Passageiro Portal',
      email: 'passageiro.portal@vanrides.dev',
      menorDeIdade: true,
      motorista: motoristas[0],
    },
  },
];

function encodeBase64Url(value) {
  return Buffer.from(value)
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

function buildToken(companyId) {
  const header = encodeBase64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const payload = encodeBase64Url(JSON.stringify({ sub: 'admin@vanrides.dev', companyId }));
  return `${header}.${payload}.signature`;
}

function sendJson(res, statusCode, body) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  });
  res.end(JSON.stringify(body));
}

function sendEmpty(res, statusCode = 204) {
  res.writeHead(statusCode, {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  });
  res.end();
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => {
      data += chunk;
    });
    req.on('end', () => {
      try {
        resolve(data ? JSON.parse(data) : {});
      } catch (error) {
        reject(error);
      }
    });
    req.on('error', reject);
  });
}

export function createAdminPortalWebE2EMockServer() {
  return http.createServer(async (req, res) => {
  if (!req.url) {
    sendJson(res, 404, { message: 'Not Found' });
    return;
  }

  const url = new URL(req.url, `http://${host}:${port}`);

  if (req.method === 'OPTIONS') {
    sendEmpty(res, 204);
    return;
  }

  try {
    if (req.method === 'POST' && url.pathname === '/company-admins/bootstrap') {
      const body = await readJson(req);
      let companyName;
      let companySlug;
      try {
        companyName = requireCompanyName(body.companyName);
        companySlug = deriveBackendCompanySlug(companyName);
        if (!companySlug) {
          throw new TypeError('companyName is invalid');
        }
      } catch {
        sendJson(res, 400, { message: 'companyName is required' });
        return;
      }
      sendJson(res, 201, {
        id: 99,
        companyId: 1,
        companySlug,
        nome: body.nome || 'Admin E2E',
        email: body.email || 'admin.portal@vanrides.dev',
        telefone: body.telefone || '51999999999',
      });
      return;
    }

    if (req.method === 'POST' && url.pathname === '/login') {
      const body = await readJson(req);
      sendJson(res, 200, {
        id: 99,
        mensagem: `Bem-vindo, Admin ${body.email ?? 'Portal'}!`,
        tipoUsuario: 'Admin',
        accessToken: buildToken(1),
      });
      return;
    }

    if (req.method === 'GET' && url.pathname === '/motoristas') {
      sendJson(res, 200, motoristas);
      return;
    }

    if (req.method === 'GET' && url.pathname === '/company-admins/passageiros') {
      sendJson(res, 200, passageiros);
      return;
    }

    if (req.method === 'GET' && url.pathname === '/company-admins/responsaveis') {
      sendJson(res, 200, responsaveis);
      return;
    }

    if (req.method === 'GET' && url.pathname === '/company-admins/vinculos/passageiros-motoristas') {
      sendJson(res, 200, passageiroMotoristaLinks);
      return;
    }

    if (req.method === 'GET' && url.pathname === '/company-admins/vinculos/responsaveis-passageiros') {
      sendJson(res, 200, responsavelPassageiroLinks);
      return;
    }

    sendJson(res, 404, { message: 'Not Found' });
  } catch (error) {
    sendJson(res, 500, { message: error instanceof Error ? error.message : 'Unknown mock error' });
  }
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const server = createAdminPortalWebE2EMockServer();
  server.listen(port, host, () => {
    console.log(`ADMIN_PORTAL_E2E_MOCK_READY http://${host}:${port}`);
  });

  process.on('SIGTERM', () => server.close(() => process.exit(0)));
  process.on('SIGINT', () => server.close(() => process.exit(0)));
}
