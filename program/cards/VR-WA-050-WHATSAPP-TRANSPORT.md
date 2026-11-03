# VR-WA-050-WHATSAPP-TRANSPORT — Implementar adapter Evolution e inbox/outbox base

## Resultado esperado

Eventos do canal viram registros duráveis e respostas elegíveis podem ser enviadas pelo adapter.

## Estado

`done`. Pacote P03, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

### IA de execução — 31/10/2026

Dependência040 concluída: backend técnico `8b5676b1868b30785497cf81a7ff1d67db1d0332`,
HEAD documental limpo `a25978a529a797d045ed6898791e24a1bf8459f6`; coordenação
`318f4fa1543871e27d292e4ef31c8607532a49e3`. App limpo em `b89e0fa532e4`;
nenhuma alteração nativa nesta fatia.030 continua aguardando participação física;
iPhone permanece pausado. Graphify estrutural CURRENT, relações confirmadas nos
fontes; semântica documental pendente não usada como prova.

Inventário remoto somente leitura confirmou Evolution2.3.7/Baileys7.0.0-rc.9 e
n8n2.45.4. O [perfil do provider](../../docs/runbooks/whatsapp-mvp/050-transporte-local.md)
registra digest, fontes primárias fixadas e limitações. A sessão lógica/número de
teste ainda não foi selecionada; não alterar webhook ou enviar mensagens reais.

Habilitar somente seis rotas de transporte previamente fechadas em040: events,
inbox claim/result e outbox claim/authorize-submit/result. Preservar filtros,
auth/CORS/WS legados, os principals e o limite32KiB. Eventos autenticados são
normalizados e persistidos antes do202; nenhuma chave do envelope, mídia, GPS
real ou número entra em fixtures/logs. Pin estático fica no banco, apenas a
referência vai ao trabalhador de conversa. Eventos de status/conexão/eco/edição
não produzem comando humano. Flags desligadas ainda aceitam recibos de status,
sem criar mensagens processáveis.

Identidade é derivada de alias do provider na instância autenticada. Associação
PN/LID só por campos explícitos do mesmo evento; conflito entre identidades é
ignorado de forma durável, sem merge. Em cohort-only, remetente desconhecido
não vira backlog executável. Fixtures de coorte usam aliases artificiais em DB
local isolado; admissão de contatos reais será preparada antes do piloto.

Inbox tem um único lease autoritativo por identidade, ordenação pelo primeiro
evento não terminal e fencing por credencial/token/geração/deadline. RETRY ou
PAUSED bloqueia ultrapassagem na mesma conversa, sem bloquear outras. Recibo
de comando já comprometido permite recuperação sem nova decisão do modelo.
Respostas fixas dos cinco handlers040 são gravadas atomicamente com o recibo.

Outbox050 libera apenas RESPONSE/TEXT desses handlers. Conteúdo/destinatário só
saem após authorize-submit persistir SUBMITTING, com prazo curto e submissão
serial por instância. Timeout/crash ambíguo vira UNKNOWN sem retry automático.
Status monotônico é reconciliado por instância+ID externo, inclusive callback
anterior ao resultado do worker; uma rodada de claim também reconcilia recibos
pendentes. Os demais propósitos e recuperação avançada continuam em120.

V27 aditiva amplia filas/recibos, preservando V26. Compatibilidade do operador
seed/reset e da suíte040 deve ser testada. Não adicionar dependência de runtime.
O adapter de envio terá contrato executável contra servidor HTTP local falso;
exports n8n e conversa completa ficam em070, validação real integrada em160.
Não confundir fixture com envio WhatsApp nem gate local com aprovação do MVP.

Sequência: fechar perfil/migration/interfaces; implementar ingresso e inbox em
arquivos separados; integrar outbox/HTTP/contrato; testes PostgreSQL de corrida,
replay e rollback; revisão independente; commit local; gate canônico completo e
smoke do mesmo JAR/SHA. Java17/Maven3.9.9; Docker local2CPU/2GiB após ajuste
documentado em040. Builds e testes pesados sequenciais sob guarda de memória.
Paths adicionais: `scripts/qa_integration_profile.sh` e
`scripts/ci_quality_gate_test.py`, apenas para ampliar a matriz obrigatória.

Rollback local: desabilitar integração/parar runtime isolado, preservar banco e
recibos, reverter commits próprios. Não remover migration já aplicada; não tocar
produção, stack GPS, n8n/Evolution existentes nem configurações do app.

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`, `scripts/qa_integration_profile.sh`, `scripts/ci_quality_gate_test.py`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-040-DOMAIN-AUTH](VR-WA-040-DOMAIN-AUTH.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependência040 concluída; versões/dirty paths e recursos locais conferidos na IA; sessão real não necessária ao contrato local e permanece pendente para integração.
- [x] Gates, smoke do mesmo JAR/SHA, evidência e sequência definidos; PostgreSQL real e revisão independente obrigatórios antes de concluir.

## Plano sequencial

1. Inventariar instância e versão real por leitura; registrar autenticação/webhook/status e distinção Baileys/Cloud API.
2. Normalizar texto, static pin, reply, PN/LID, eco, grupos, edição e conexão; persistir antes de ACK.
3. Implementar claim/result e leases de inbox/outbox, status separado e fixtures sanitizadas.
4. Preparar configuração do número de teste e ensaio autenticado; mudanças externas/envios somente no ambiente e destinatários autorizados.
5. Testar limite de corpo, autenticação, duplicação, indisponibilidade antes/depois da persistência e loops.

## Critérios de aceite

- [x] Contrato da versão efetiva comprovado ou impedimento identificado sem inventar payload.
- [x] Pin é extraído sem modelo; callbacks não são mensagens humanas.
- [x] Fixtures/exports não contêm telefone, token ou GPS real; outbox base persistente.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gate backend; contrato adapter; T04/T17/T18/T23/T24/T34/T37; smoke real restrito só com configuração autorizada.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-050-WHATSAPP-TRANSPORT.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Desativar integração nova, preservar fila para reconciliação, reverter configuração de webhook previamente salva sem alterar outras instâncias.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

Fechado localmente em31/10/2026: [evidência e limites](../evidence/VR-WA-050-WHATSAPP-TRANSPORT.md). Gate1227/1227, smoke8/8 no mesmo JAR/SHA, adapter24/24 e preflight negativo25/25.070 é o próximo card elegível;030 segue pendente, iPhone físico pausado.
