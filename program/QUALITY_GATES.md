# Gates do Programa

Os gates são cumulativos. Um gate superior só pode passar se todos os anteriores
continuarem válidos.

## G0 — CONTROLLED

Objetivo: tornar o trabalho rastreável e impedir estados contraditórios.

Critérios:

- fonte única de verdade versionada;
- inventário do monorepo, dos módulos e do workspace, com origem histórica preservada;
- cards com DoR, DoD, dependências e evidências;
- limite de WIP verificado automaticamente;
- riscos prioritários registrados;
- planos antigos classificados, sem exclusão de histórico;
- dashboard e harness alinhados ao programa.

## G1 — TRUSTED

Objetivo: impedir acesso ou mutação indevida e controlar sessões.

Critérios:

- autorização por objeto para localização, viagem e tempo real;
- nenhuma inscrição STOMP ampla por papel ou tenant;
- convite, vínculo e bootstrap atômicos;
- refresh rotation, revogação e logout de servidor;
- armazenamento seguro de sessão por plataforma;
- rate limits e controles de abuso;
- regressão automatizada cross-tenant e same-tenant.

## G2 — CONTRACTED

Objetivo: estabilizar contratos e tornar falhas previsíveis.

Critérios:

- default deny na camada HTTP;
- DTOs e validação de entrada consistentes;
- erro padronizado e OpenAPI atualizado;
- cliente app tipado com tratamento uniforme;
- clientes externos com timeout, retry controlado e circuit breaker;
- uploads duráveis e jobs críticos fora de memória;
- compatibilidade N/N-1 comprovada.

## G3 — INCLUSIVE

Objetivo: garantir uso responsivo e acessível nas jornadas críticas.

Critérios:

- bloqueadores de reflow eliminados;
- teclado, foco, semântica, contraste e target size validados;
- matriz manual VoiceOver/TalkBack/Web concluída;
- Dynamic Type/text scaling e redução de movimento;
- jornadas críticas em larguras e orientações suportadas;
- textos migrados para localização e linguagem clara.

## G4 — NAVIGABLE

Objetivo: disponibilizar navegação segura e operacional.

Critérios:

- plataforma de mapas atrás de boundary autenticado e limitado;
- provider primário e fallback observáveis;
- semântica real de segurança de rota validada;
- cobertura e pacotes offline com promoção controlada;
- telemetria minimizada e consentida;
- testes de campo e critérios de degradação aprovados.

## G5 — OPERABLE

Objetivo: operar, diagnosticar e recuperar o produto.

Critérios:

- logs estruturados, métricas, traces e correlação;
- SLOs e alertas acionáveis;
- backup automático e restore ensaiado;
- imagens non-root, SBOM, scan e assinatura;
- configuração e segredos auditáveis;
- estado horizontalizado ou explicitamente isolado;
- runbooks de incidente, rollback e continuidade.

## G6 — PILOT READY

Objetivo: autorizar um piloto externo controlado.

Critérios:

- G0 a G5 aprovados;
- journeys E2E por papel e dispositivo;
- regressão visual, acessibilidade, desempenho e carga;
- privacidade, consentimento e tratamento de menores aprovados;
- suporte, kill switch e métricas do piloto;
- release manifest imutável;
- nenhum risco alto aberto sem mitigação aceita;
- decisão humana `GO`.

## G7 — PRODUCTION READY

Objetivo: operar com segurança fora do piloto.

Critérios:

- evidência do piloto dentro dos limites;
- capacidade, custos e suporte dimensionados;
- incident response e comunicação exercitados;
- rollout progressivo e rollback testado;
- retenção, exportação e exclusão de dados operacionais;
- observação pós-release definida;
- decisão humana `GO`.
