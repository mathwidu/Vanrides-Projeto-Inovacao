# Baseline de Readiness

> Histórico preservado da coordenação anterior à migração de 2026-11-02. Prioridade e estado atuais: `program/program.json`; evolução fora do MVP não está concluída por esta importação. Topologia e entrada atuais: [programa no monorepo](../docs/desenvolvimento/programa.md).

Data da fotografia: 2026-07-16.

Esta baseline consolida a auditoria local que originou o programa. Ela não
declara produção pronta e não substitui nova evidência quando o código mudar.

## Topologia real

| Superfície | Branch | SHA local | Situação |
|---|---|---|---|
| Workspace | `codex/vanrides-next-quality-phase` | `3376b72a88433ad5ad43c5f6d3b41bb935160f80` | coordenação; arquivos preexistentes não rastreados |
| Backend | `dev` | `138558662c61468036840e7381fdbd67b2c73ff6` | limpo; 41 commits à frente do upstream |
| App | `quality/app-quality-stack-2026-06-13` | `11eb09bfdc54143b8c7866830b193b40a4c64406` | 173 commits à frente; trabalho de vídeo preexistente |
| Mapas | `main` | `a0c2a659eee262758191dff83300ef5b1045ef08` | limpo; sem remote/upstream |

Esses SHAs são baselines locais. Eles não possuem, por si só, prova de CI remoto
ou autorização de promoção.

## Resultado executivo

- base local funcional e com boa quantidade de testes;
- produto ainda em `HOLD` para piloto externo;
- quatro riscos altos de autorização em localização/tempo real;
- ciclo de sessão incompleto no app;
- acessibilidade manual ainda não comprovada;
- bloqueadores responsivos em jornadas críticas;
- maps platform ainda não promovível;
- lacunas de privacidade, backup, observabilidade e escala.

## Segurança

A auditoria consolidou 24 achados:

- 4 altos;
- 15 médios;
- 5 baixos;
- 6 itens permaneceram diferidos para validação posterior.

Os quatro altos são:

1. motorista pode forjar GPS de outro motorista do mesmo tenant via REST;
2. passageiro pode consultar GPS de motorista não relacionado do mesmo tenant;
3. membro do tenant pode assinar tópico de viagem não relacionada;
4. tópico STOMP amplo por empresa expõe GPS da frota a papéis do tenant.

## Sessão

- access token do backend: 15 minutos;
- refresh token: 30 dias;
- app persiste tokens, mas não coordena refresh em runtime;
- logout do app não revoga sessão no servidor;
- armazenamento e links precisam de hardening por plataforma.

## Experiência e acessibilidade

Bloqueadores responsivos identificados:

- seletor de experiência;
- bootstrap/admin;
- cadastro de passageiro.

Testes automatizados ajudam, mas a matriz manual com teclado, VoiceOver,
TalkBack, text scaling, contraste e reflow ainda precisa ser produzida.

## Mapas

- serviço atualmente público, sem autenticação e sem limites suficientes;
- fallback e ausência de dados ainda impedem promoção;
- cobertura, offline, semântica de segurança e teste de campo são obrigatórios;
- dados de GPS, menores e providers exigem minimização e finalidade explícitas.

## Lacunas da esteira anterior

- harness modelava apenas backend e app;
- dashboard podia parecer verde enquanto o piloto permanecia em `HOLD`;
- smoke remoto do backend podia testar DEV diferente do SHA local;
- campanha de release não fixava todos os SHAs/digests/schema;
- E2E mobile completo era opcional;
- mapas não participavam da campanha;
- credenciais de QA podiam aparecer em summary versionável;
- deploy de produção não exigia todos os controles de promoção e rollback.

## Limitações desta baseline

Não foram usados dados reais de produção, usuários externos ou telemetria
operacional de longo prazo. Cada gate exige evidência renovada na versão exata
que será promovida.
