# VR-SEC-005-RATE-LIMIT-TRUST-001 — Confiar proxy e limitar cardinalidade do rate limit

## Resultado esperado

Um cliente anônimo não consegue escolher sua chave de rate limit nem aumentar
sem limite a memória do processo; login, bootstrap, cadastros e redemption
mantêm proteção determinística na topologia real de ingress. Troca de e-mail
também é protegida antes de autenticação cara ou qualquer mutação. Corpos
maiores com `Content-Length` conhecido e JSON/raw sensível são interrompidos
antes de Jackson, BCrypt, consultas, filas ou controllers. Em rotas não
sensíveis com tamanho desconhecido, auth/handler pode anteceder a leitura, mas
`N+1` não alcança controller nem mutação.

## Estado

`done` — implementação e prova local concluídas no backend limpo
`46cd5e8a8dc7d2a3f76771fb7d65aba286a71d06`. Confiança de proxy permanece
vazia por padrão; inspeção da topologia real, allowlist do ingress e smoke
externo continuam bloqueadores de promoção, não do encerramento local do card.

## Risco ou objetivo associado

`R-016`, épico `VR-SEC-005` e gate `G1 — TRUSTED`.

## Contexto

No baseline `4824f54`, `BasicRateLimitFilter` confia no primeiro
`X-Forwarded-For` mesmo sem provar que
a conexão veio de proxy confiável. O cliente pode rotacionar o header para
contornar limites. Cada chave entra em `ConcurrentHashMap` sem expiração ou
remoção global, permitindo crescimento até exaustão de heap. Os endpoints
públicos também não possuem um teto uniforme de request body antes do parser.

## Impact Analysis

- usar `remoteAddr` por padrão e interpretar forwarded headers somente quando o
  peer pertence à allowlist de proxies da infraestrutura;
- normalizar e limitar comprimento/formato da chave;
- substituir o mapa ilimitado por store local particionado por família, com
  cardinalidade máxima, expiração monotônica e rejeição fail-closed quando a
  partição estiver cheia; contador ativo não será expulso para reiniciar quota;
- instalar `RequestBodyLimitFilter` independente do rate limit: rejeitar
  `Content-Length` conhecido acima do teto; pré-ler JSON/raw sensível até
  `máximo+1` depois da quota e antes de auth; limitar as demais rotas de forma
  lazy, com lease curto por leitura; admitir somente `multipart/form-data` no
  parser do container e devolver `415` para form-urlencoded/variantes multipart;
- dimensionar o bulkhead dentro do pool Tomcat, reservar threads para requests
  que não leem body e fechar HTTP/1.1 sem swallow quando a aplicação abandonar
  o corpo;
- incluir `POST /auth/email-verifications/change-email`,
  `GET /driver-onboarding/invites/{token}/preview` e redemptions na matriz
  sensível, normalizando preview para uma família sem token e aplicando `429`
  antes de auth, hash, consulta cara, envio de e-mail ou mutação;
- proibir e-mail, senha, token ou convite bruto na chave e na telemetria;
- testar spoofing, cadeia de proxies, IPv4/IPv6, eviction, expiração e carga;
- documentar comportamento single-node e o caminho para coordenação distribuída.

Ordem efetiva após revisão adversarial: correlation/request ID,
CORS/preflight, rate limit, body limit e autenticação. Assim a identidade é
limitada antes da leitura de JSON, enquanto `413` continua anterior a
auth/Jackson/controller nos casos conhecidos/sensíveis. CORS preflight permanece
livre de consumo de body e quota; respostas antecipadas `413/415/429/503`
preservam CORS e request ID.

## Decisões congeladas após revisão independente

- `server.forward-headers-strategy=none`; `remoteAddr` é a única autoridade
  enquanto a allowlist estiver vazia;
- apenas uma conexão cujo peer imediato pertença a CIDR exato configurado pode
  fornecer uma única cadeia XFF; `Forwarded` e `X-Real-IP` são ignorados;
- XFF é limitado em bytes e hops, aceita somente IP literal estrito e é
  percorrido da direita para a esquerda; cadeia inválida em peer confiável
  falha com erro genérico, sem criar uma identidade alternativa;
- `HEAD` de confirmação de e-mail e preview de convite é classificado na mesma
  família de `GET`, impedindo bypass pelo dispatch implícito do Spring MVC;
- IPv4 usa `/32`; clientes IPv6 são agregados por `/64`; a chave guarda somente
  digest de 128 bits com salt aleatório por processo e família fechada, nunca
  IP, URI dinâmica, e-mail, senha, token ou convite;
- a capacidade é independente por família. Ao lotar, uma identidade nova
  recebe `429` até a expiração mais próxima; nenhuma entrada ativa é expulsa;
- a janela é fixa e monotônica, o contador satura em `N+1` e `Retry-After`
  representa o tempo restante arredondado para cima;
- Micrometer registra apenas tags fechadas de família, origem e resultado.
  Actuator não será publicado no router; nos stacks, a porta de management fica
  vinculada ao loopback do container para inspeção operacional explícita;
- ambientes públicos falham no startup se o rate limit estiver desligado ou se
  limite, janela, capacidade, tamanho de body, CIDR ou limites de XFF forem
  inválidos;
- o teto inicial é `1 MiB`. JSON/raw sensível sem tamanho é pré-lido até
  `máximo+1` e reproduzido antes de auth/Jackson; nas demais rotas, o wrapper
  lazy adquire lease por leitura e o advice drena antes do controller;
- somente `multipart/form-data` usa o parser do container, sob admissão externa,
  com avatar `512 KiB` e request `1 MiB`. Form-urlencoded e outras variantes
  multipart retornam `415 UNSUPPORTED_BODY_MEDIA_TYPE` sem parsing;
- capacidade padrão `32`, reserva mínima `16` e pool HTTP Tomcat `200`; startup
  exige `capacidade + reserva <= pool`;
- respostas JSON antecipadas são serializadas em bytes sem fechar/commitar o
  output antes da valve; body abandonado desabilita swallow e fecha uma vez
  HTTP/1.1 com `Connection: close`, preservando status async; body consumido
  preserva keep-alive. HTTP/2 é rejeitado no
  startup até suporte por stream. O deadline total do ingress continua gate de
  promoção porque a valve só age após abandono do body;
- o store é por processo: uma réplica é condição de promoção até existir
  rate limit equivalente no edge ou store compartilhado.

## Contratos e consumidores

- payloads de sucesso permanecem inalterados;
- excesso de body retorna `413` JSON com código `PAYLOAD_TOO_LARGE`; tamanho
  conhecido e JSON/raw sensível falham antes de auth, e stream lazy falha antes
  de controller/mutação;
- media type rejeitado retorna `415 UNSUPPORTED_BODY_MEDIA_TYPE`; bulkhead
  saturado retorna `503 REQUEST_BODY_CAPACITY` com `Retry-After: 1`;
- excesso de tentativas retorna `429` JSON com código `RATE_LIMITED` e
  `Retry-After`; `N+1` não executa auth, BCrypt, lookup, fila ou mutação;
- CORS preflight não consome quota e `413/415/429/503` continuam legíveis
  pelo app/web cross-origin;
- Flutter não precisa alterar parser produtivo: já trata status/erro genérico;
  o contrato será congelado em testes HTTP e documentação.

Famílias protegidas: login, refresh, confirmação/reenvio/troca de e-mail,
bootstrap, cadastro de passageiro/motorista, preview e redemption de convite de
motorista e redemption de código de passageiro. Tokens de path/query/body nunca
entram na chave.

## Riscos e mitigação

- **CIDR amplo ou hop desconhecido:** default vazio; overlay compartilhada não
  será aceita como fronteira; promoção exige inspeção real do Traefik/rede;
- **eviction como bypass:** store não remove contador ativo; capacidade cheia
  rejeita identidade nova e expõe métrica agregada;
- **DoS de body:** rate limit anterior ao eager sensível; limite terminal `N+1`,
  leases curtos no lazy, admissão externa de multipart, reserva do pool, rejeição
  de media type fora do contrato e fechamento sem swallow para body abandonado;
- **slow upload ainda em leitura:** valve não é deadline; promoção exige
  `readTimeout` total e política de buffering provados no ingress real;
- **protocolo:** fechamento atual é de conexão HTTP/1.1; HTTP/2 falha no startup
  até existir encerramento/teste por stream;
- **erro antes do MVC sem CORS:** CORS migra para filtro Servlet anterior aos
  controles;
- **desligamento acidental:** guard de startup em DEV/staging/prod;
- **escala horizontal:** stacks fixam uma réplica e `stop-first`; mais de uma
  réplica permanece `HOLD` sem controle distribuído/edge equivalente;
- **NAT compartilhado e burst de fronteira:** limitações aceitas da janela
  fixa/IP, observadas por métricas e candidatas a algoritmo distribuído futuro.

## Escopo autorizado

- política de rotas sensíveis, parser de IP/CIDR, resolução de peer/XFF,
  digest, store limitado, métricas, filtros de body/rate/auth/CORS, advice,
  exceptions de body, writer de erro antecipado, bulkhead e valve Tomcat;
- guardrails de startup e propriedades/envs dos três stacks versionados;
- testes unitários, concorrentes, WebMvc, CORS, multipart e ordem efetiva;
- smoke same-JAR, carga local limitada, wiring obrigatório no gate, docs,
  matrizes, QA Log e evidência coordenadora.

O allowlist foi ampliado antes de edição para componentes pequenos e testáveis:
resolver/IP, política, store, métricas, replay/lazy de body, admission controller,
advice/exceptions, valve/context customizer, guard de runtime, ordem/CORS, testes
WebMvc/Tomcat/context e scripts de smoke/carga. Qualquer path adicional exige
nova reanálise e atualização canônica antes de ser tocado.

## Fora de escopo

- adivinhar ou alterar CIDRs/configuração viva do Traefik, Cloudflare, Swarm ou
  firewall;
- push, deploy, publicação de porta, smoke externo ou promoção;
- Redis/store distribuído, WAF/CDN rate limit ou múltiplas réplicas;
- habilitar HTTP/2 ou implementar timeout/buffering no ingress vivo;
- redesenhar intenção do link GET de confirmação (`VR-SES-003`), entropia e
  consumo atômico de convites (`VR-SEC-004-INVITE-REDEMPTION-001`);
- padronizar todos os erros HTTP do backend.

## Critérios de aceite

- [x] XFF de cliente direto não altera a identidade usada no limite.
- [x] Apenas proxy configurado pode fornecer endereço encaminhado validado.
- [x] Cardinalidade possui teto e entradas expiradas são removidas.
- [x] Rotação de headers não contorna o limite nem cresce memória sem teto.
- [x] `Content-Length` acima do teto falha imediatamente; JSON/raw sensível sem
  tamanho é pré-lido até `máximo+1` depois da quota e antes de auth.
- [x] JSON/raw não sensível usa wrapper lazy com lease por leitura; `404` e auth
  inválida não esperam body, e overflow/capacity terminal não consome bytes
  adicionais nem chega ao controller.
- [x] Somente `multipart/form-data` entra no parser sob admissão externa;
  form-urlencoded e demais variantes multipart retornam `415` sem parsing.
- [x] Bulkhead `32`, reserva `16` e pool Tomcat `200` obedecem a inequação de
  startup; saturação retorna `503` sem espera e sem corromper response committed.
- [x] Exatamente o limite passa e `limite+1` retorna `413 PAYLOAD_TOO_LARGE`
  antes de controller, serviço, BCrypt, lookup ou fila.
- [x] Body fixo/chunked abandonado responde JSON com `Connection: close` e
  encerra HTTP/1.1 sem swallow uma única vez, inclusive em async/race; body
  consumido sync/async preserva keep-alive; HTTP/2 não pode ser habilitado
  silenciosamente.
- [x] `change-email`, preview e redemption recebem `429` antes de efeitos.
- [x] `HEAD` de confirmação/preview compartilha quota com `GET` e não contorna
  a política.
- [x] `N+1` responde com o mesmo `429`/`Retry-After`, CORS preflight permanece
  livre e tokens distintos de preview não aumentam a cardinalidade.
- [x] Chaves, logs e métricas não contêm e-mail, senha, token ou convite bruto.
- [x] Métricas permitem observar bloqueios, cardinalidade e evictions sem IP bruto.
- [x] Gate, testes de carga controlados e smoke same-JAR local passam; promoção
  continua `HOLD` até prova separada da topologia, allowlist, porta direta,
  buffering e `readTimeout` total do ingress.

## Dependências

- `VR-SEC-001-BE-READ` concluído;
- `VR-SEC-004-EMAIL-NAMESPACE-001` concluído;
- cadeia de proxies do ambiente confirmada antes de promoção.

## Definition of Ready

- [x] resultado, contratos, critérios e não-escopo definidos;
- [x] dependências locais concluídas e blocker externo separado da implementação;
- [x] backend limpo em `4824f54abab0ad978502cb2e056115c5c9348a28`, tree
  `56eeceea0122c6fc86406f2a74a49b4750e4fcd2`;
- [x] coordenação em `65e2826362fe145f3a051ca47ab67146e313e4b3`;
- [x] app preexistente em `b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c`
  preservado com 51 alterações fora do card; Maps limpo em `a0c2a659eee2`;
- [x] análise independente de ameaça, topologia e superfície de testes concluída;
- [x] ordem, defaults fail-closed, cardinalidade, privacidade, métricas, body,
  rollback e plano sequencial congelados;
- [x] paths autorizados expandidos antes de qualquer edição backend.

## Plano sequencial

1. congelar propriedades, guard público, CORS e ordem explícita dos filtros;
2. implementar IP/CIDR estrito, resolução XFF, política fechada, digest e store;
3. instrumentar métricas sem identificadores e limitar/reproduzir request body;
4. provar rotas, concorrência, capacidade, expiração, CORS, multipart e efeitos
   não executados em testes unitários/WebMvc;
5. alinhar stacks, env example, runbooks, matrizes e backlog;
6. criar smoke same-JAR e carga local bounded, ratchetear no gate e executar
   gate limpo/Graphify/evidência;
7. manter promoção em `HOLD` até a cadeia real de ingress e smoke externo.

## Ativação — 2026-07-17

- único card ativo no programa; WIP `1/1`;
- rollback local: reverter somente commits deste card e voltar o estado para
  `ready`; nenhum schema ou dado persistente é alterado;
- dirty paths conhecidos da coordenação e do app continuam excluídos;
- a auditoria versionada encontrou Traefik esperado sobre
  `network_swarm_public`, mas não encontrou sua configuração estática, versão,
  argumentos, subnet nem peers; nenhuma allowlist real será inferida;
- `vanrides.port-8081.yml` permanece caminho direto e nunca confiará XFF;
- PROD/piloto/deploy/push: `HOLD`.

## Encerramento local — 2026-07-17

- backend final: `46cd5e8a8dc7d2a3f76771fb7d65aba286a71d06`, tree
  `eb504aee161f3529c1da105ad6681c2e0667c80e`, limpo;
- gate completo: 914/914; integração PostgreSQL estrita: 43/43;
- smoke same-JAR: `rate-limit-20260718T012827Z-53057`, resultado e cleanup
  `PASS`, sem mutação remota;
- revisão independente final: `GO`, sem P1/P2;
- evidência canônica:
  `program/evidence/VR-SEC-005-RATE-LIMIT-TRUST-001.md`;
- nenhum push, deploy, banco remoto ou configuração viva foi alterado;
- `R-016` permanece `mitigando` e a promoção permanece `HOLD`.

## HOLD de promoção

Confirmar a cadeia de proxies confiáveis de DEV/staging/produção antes da
promoção, junto com porta direta, buffering, deadline total e HTTP/2. A versão
local usa default seguro sem confiar em headers.

## Rollback

Rollback reabre bypass e DoS de memória; por isso nenhuma versão anterior pode
ser promovida como equivalente. O rollback técnico é seguro por não envolver
schema, mas piloto e produção permanecem `HOLD`.
