# VR-SES-003-EMAIL-CONFIRM-INTENT-001 — Exigir intenção explícita para confirmar e-mail

## Resultado esperado

Abrir, pré-visualizar, fazer prefetch ou executar `HEAD/GET` no link de e-mail
nunca confirma a conta nem consome o token. A mutação ocorre somente após ação
explícita do usuário por `POST`, com proteção contra replay e vazamento.

## Estado

`waiting_human` — o backend final limpo `b2dd00c`, tree `f79e34a`, passou gate,
smoke same-JAR, wrapper oficial e browser automatizado na mesma versão. Restam
somente Tab, Shift+Tab, Enter, Espaço e VoiceOver humanos no JAR de SHA-256
`0e5ea6c...`; piloto e produção permanecem em `HOLD`.

Revalidação read-only em `2026-07-18T14:23:44Z`: o JAR preservado continua em
`src/backend/output/qa-evidence/email-intent-20260718T054947Z-5031/runtime.jar`,
com 57.809.844 bytes, modo `0400` e SHA-256 integral
`0e5ea6c61ca0277d74736dbc74fc6149de598d88693217420ebdc66087946e7f`.
O commit `b2dd00c...` continua ancestral do backend atual. O arquivo está em
`output/` ignorado e não é artifact durável; sua perda exige repetir gate,
smoke, browser e handoff afetados.

## Risco ou objetivo associado

`R-019`, épico `VR-SES-003` e gate `G1 — TRUSTED`.

## Contexto

`GET /auth/email-verifications/confirm?token=...` hoje executa a confirmação.
Scanners de e-mail, previews, proxies e prefetchers podem abrir o link antes do
usuário e validar/consumir a credencial sem intenção humana.

## Impact Analysis

### Mudanças técnicas

- emitir links novos como `.../confirm#token=<urlencoded>` para que o segredo
  não chegue no request-target do servidor;
- tornar `GET`/`HEAD` estritamente não mutantes e sem chamada a service/banco;
- exibir landing inline, sem terceiros ou subrequests automáticos, que só envia
  o JSON `POST` same-origin esperado após ativação do botão nativo;
- ler o fragmento somente em memória, limpar URL antes de qualquer estado e
  nunca copiar token para DOM, cookie, storage, console ou erro;
- links query legados nunca mutam nem reutilizam o token; exibem recuperação e
  reenvio. A exposição residual no request-target expira em até um TTL após o
  primeiro deploy e continua bloqueando promoção sem prova do ingress;
- preservar consumo único, expiração e erro genérico de replay;
- redigir `LoggingEmailDeliveryGateway`: não registrar destinatário, corpos ou
  segredo;
- remover `GET/HEAD` da quota `EMAIL_CONFIRM` e manter somente a mutação POST;
- adicionar headers no-store/referrer/robots/nosniff/frame/permissions e CSP
  com hashes exatos para CSS/JS inline;
- preservar o invariante de que `email_verification_last_sent_at` só muda após
  um envio efetivo; confirmação e auto-verificação preservam o valor existente
  ou mantêm `null`;
- testar prefetch, `HEAD`, `OPTIONS`, query legada, repetição, concorrência,
  browser, teclado, foco, reflow, contraste e leitor de tela.

### Expansão justificada do escopo backend — 2026-07-18

- o primeiro smoke same-JAR revelou que `EmailVerificationState.markVerified`
  sobrescrevia `email_verification_last_sent_at` durante a confirmação;
- a correção fica restrita ao estado de verificação e a testes unitários dos
  fluxos público de motorista/passageiro, além das provas PostgreSQL já
  autorizadas; não muda schema, DTO, payload, status HTTP, TTL ou locks;
- os quatro paths correspondentes foram acrescentados ao escopo autorizado no
  programa antes da conclusão do card.

### Contratos afetados

- `POST /auth/email-verifications/confirm` permanece a operação mutante;
- `GET .../confirm`, com ou sem query, retorna a mesma landing não mutante;
- `HEAD` devolve os mesmos headers sem body; `OPTIONS` continua preflight;
- app e Maps não possuem consumidor de confirmação e ficam fora do escopo;
- POST JSON continua compatível para clientes externos não versionados.

### Riscos e mitigação

- link antigo deixar usuário sem saída: landing explica recuperação e novo
  envio, mas nunca volta a mutar por GET;
- token novo vazar em logs/referrer: fragmento, headers, redaction e nenhum
  terceiro na página;
- CSRF/click automático: operação sem cookie/sessão, JSON bearer explícito,
  `credentials: omit`, nenhum auto-fetch e framing bloqueado;
- CORS wildcard sistêmico: não alterar sem origins reais; extrair card próprio;
- acessibilidade da landing: foco, nome acessível, teclado, reflow e leitores de
  tela entram no aceite, não como correção posterior.
- o P2 do app em fluxos de auto-verificação será tratado em card próprio; ele
  bloqueia o E2E de release/promoção, mas não amplia nem bloqueia este patch de
  backend.

## Arquitetura congelada

- `AuthController` recebe renderer dedicado e o handler GET não aceita token;
- `EmailVerificationLandingPageRenderer` é dono único de HTML, CSS, JavaScript
  e hashes CSP, sem template engine ou arquivos externos;
- `EmailVerificationService` só muda emissão do link e escape do href; locks e
  confirmação POST permanecem autoridade transacional;
- query antiga é detectada apenas no browser para exibir recuperação, depois de
  `history.replaceState`; nunca vira payload POST;
- logger local registra apenas evento agregado sem PII ou conteúdo;
- `PublicRuntimeConfigurationGuard` rejeita em ambiente publicado base URL sem
  HTTPS, com loopback/wildcard conhecido, userinfo, query, fragmento ou path
  divergente. Alcance público real, rede privada e placeholder são verificados
  operacionalmente antes da promoção;
- não há mudança de DTO, schema, Flyway, app, Maps ou CORS global.

## Headers defensivos

- `Cache-Control: no-store, max-age=0`, `Pragma: no-cache`, `Expires: 0`;
- `Referrer-Policy: no-referrer`;
- CSP `default-src 'none'`, hashes inline exatos, `connect-src 'self'`,
  `base-uri 'none'`, `form-action 'none'`, `frame-ancestors 'none'`;
- `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`;
- `X-Robots-Tag: noindex, nofollow, noarchive, nosnippet`;
- `Permissions-Policy` sem câmera, microfone, geolocalização ou payment.

## Acessibilidade congelada — WCAG 2.2 AA

- `lang=pt-BR`, título, `main`, um `h1` e botão HTML nativo;
- Tab, Shift+Tab, Enter e Espaço, foco visível 3:1 e alvo mínimo 24x24 CSS px;
- `aria-busy`, live region/status e alert; processamento preserva o fluxo de
  foco do gesto, estados terminais movem foco uma vez ao heading, e
  legado/ausente são anunciados no carregamento sem salto artificial;
- texto 4,5:1, controles 3:1 e nenhuma informação apenas por cor;
- reflow 320 CSS px, zoom 200/400% e text spacing sem perda;
- estados inicial, processando, sucesso, inválido/replay, link legado/ausente e
  rede/5xx claros e recuperáveis;
- nenhuma animação necessária e suporte a `prefers-reduced-motion`;
- axe é apoio; teclado e VoiceOver no mesmo JAR são prova obrigatória.

## Critérios de aceite

- [x] `GET`, `HEAD`, `OPTIONS`, prefetch e preview não alteram conta, identidade,
  sessão ou token.
- [x] GET limpo e query legada devolvem os mesmos bytes/headers; query nunca é
  reutilizada e oferece recuperação segura.
- [x] Links novos não enviam token no request-target e a landing não faz
  subrequests de terceiros.
- [x] A landing não faz auto-fetch; clique e double-click automatizados emitem
  exatamente um `POST`, e somente uma confirmação concorrente vence.
- [ ] Tab/Shift+Tab posicionam foco corretamente e Enter/Espaço emitem
  exatamente um `POST` por gesto humano no mesmo JAR.
- [x] Replay, expirado e inválido usam erro genérico e não verificam/mutam conta,
  identidade ou sessão.
- [x] Links novos não expõem token em logs, DOM, storage, analytics, referrer,
  cache ou URL/request-target de subrequests; a única ocorrência de rede é o
  corpo JSON do POST same-origin esperado após o gesto. O logger local não
  registra PII/corpos.
- [x] Landing possui todos os headers defensivos e passa automação de browser,
  reflow 320 CSS px, contraste e estados/foco/live regions programáticos.
- [ ] VoiceOver humano confirma nome, instrução, mudanças de estado, alertas e
  foco terminal no mesmo JAR.
- [x] Unitários, WebMvc, PostgreSQL ratcheteado, browser e smoke same-JAR passam
  na mesma versão limpa.
- [x] Contrato, compatibilidade, runbook, backlog e QA Log ficam sincronizados.

## Dependências

- `VR-SEC-005-RATE-LIMIT-TRUST-001` concluído;
- inventário local concluído: backend/e-mail é o único consumidor; app e Maps
  não implementam confirmação;
- ingress/CORS real e janela residual de links antigos bloqueiam promoção, não
  implementação local.

## Definition of Ready

- [x] risco, resultado observável e contratos GET/HEAD/OPTIONS/POST definidos;
- [x] consumidores backend, app, Maps e documentos inventariados;
- [x] compatibilidade de POST e recuperação de query legada congeladas;
- [x] renderer, fragmento, scrub, CSP e redaction de logs definidos;
- [x] critérios WCAG 2.2 AA, estados, teclado e prova assistiva definidos;
- [x] concorrência, ratchet PostgreSQL, smoke same-JAR e browser definidos;
- [x] paths abaixo congelados antes de implementação;
- [x] rollback nunca restaura mutação em GET;
- [x] nenhum blocker humano impede implementação/prova automatizada local.

## Escopo autorizado

- controller, renderer, emissão do link, logger seguro, política sensível e
  guard de configuração pública;
- testes dedicados para não estourar arquivos já no teto de 350 linhas;
- três provas PostgreSQL adicionais, ratchet, wiring e smoke same-JAR;
- browser/teclado/axe/VoiceOver no mesmo JAR;
- estado de verificação e testes dos registros públicos necessários para
  preservar `email_verification_last_sent_at` fora de envios efetivos;
- env/stacks apenas se o contrato de base URL exigir sincronização;
- contratos, RBAC, QA Playbook, backlog, QA Log e evidência coordenadora.
- reconciliação operacional do namespace de e-mail, que ainda descreve o
  `GET` legado como mutação.

## Fora de escopo

- restringir CORS global sem origins públicos aprovados;
- configurar access-log/redaction do Traefik vivo, deploy ou invalidar tokens
  remotos;
- deep link mobile, mudança no app/Maps ou correção do banner Flutter;
- mudar DTO POST, payload de sucesso, schema, TTL ou locks atuais;
- push, staging, piloto ou produção.

## Plano sequencial

1. congelar RED para GET/HEAD/OPTIONS/prefetch e logger sem segredo;
2. implementar renderer inline/CSP e handler não mutante;
3. emitir fragmento, recusar query legada e redigir o gateway de log;
4. ajustar quota/guard e provar POST/replay/expiração/concorrência;
5. criar smoke same-JAR e executar browser/acessibilidade no mesmo runtime;
6. sincronizar docs, gate limpo, Graphify, revisão independente e evidência;
7. registrar o SHA documental final e repetir gate, smoke e browser na mesma
   versão; marcar o card `DONE` somente após teclado e VoiceOver no mesmo JAR.

## Checkpoint automatizado final — 2026-07-18

- backend final limpo `b2dd00cf1e4e7cbfe6a7d50d606ee948d8b7f871`,
  tree `f79e34a1c96939979046626e7700628fc7b09875`;
- gate final `integration-20260718T054807Z-4357`: `944/944` e PostgreSQL
  `49/49`; smoke `email-intent-20260718T054947Z-5031`: `PASS` no JAR
  imutável de SHA-256 `0e5ea6c...`;
- wrapper oficial `integration-20260718T055307Z-7735` repetiu `944/944`,
  `49/49` e HTTP no mesmo JAR; browser real e facade controlada concluíram as
  provas automatizadas de não mutação, gesto único, fail-closed, reflow,
  contraste, foco e live regions;
- Tab, Shift+Tab, Enter, Espaço e VoiceOver continuam explicitamente pendentes,
  sem waiver ou aprovação implícita;
- evidência versionada em
  `program/evidence/VR-SES-003-EMAIL-CONFIRM-INTENT-001.md`;
- revisão documental independente final: zero P1/P2; `R-019` permanece
  `mitigando`
  e piloto/produção em `HOLD`.

## Checkpoint técnico intermediário — 2026-07-18

- backend técnico `f3b7b9d` com gate limpo, smoke same-JAR e browser
  automatizado verdes;
- o resultado é intermediário: o commit documental final ainda precisa receber
  as repetições na mesma versão e a prova manual de teclado/VoiceOver;
- o P2 de auto-verificação do app segue para card separado e bloqueia o E2E de
  release, sem alterar o escopo deste patch;
- `R-019` continua aberto e piloto/produção permanecem em `HOLD`.

## Checkpoint de readiness — 2026-07-17

- inventário e decisões completas em
  `program/evidence/VR-SES-003-EMAIL-CONFIRM-INTENT-001-DOR.md`;
- backend limpo `6efd2e2f66cb`, app `b53296b67dd0` e Maps `a0c2a659eee2`;
- nenhum código do fluxo foi alterado durante a análise;
- `R-019` permanece aberto até prova final; piloto/produção em `HOLD`.

## Ativação — 2026-07-17

- WIP global assumido exclusivamente por este card;
- baseline coordenação: `cddf56fe5e4a0235eb1d8c9de10f08309423b6ca`;
- baseline backend: `6efd2e2f66cb5ac3784085d317ba98174750c815`;
- baseline app preservado: `b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c`;
- baseline Maps preservado: `a0c2a659eee262758191dff83300ef5b1045ef08`;
- Impact Analysis, contratos, dependências e paths autorizados reconfirmados;
- rollback mantém o `POST` e uma landing de recuperação, sem restaurar mutação
  em `GET` ou `HEAD`;
- nenhum push, deploy, banco remoto ou promoção foi autorizado.

## Rollback

Se a landing falhar, manter POST e servir recuperação/indisponibilidade, depois
corrigir e reenviar. Reativar mutação em GET reabre `R-019` e bloqueia promoção.
