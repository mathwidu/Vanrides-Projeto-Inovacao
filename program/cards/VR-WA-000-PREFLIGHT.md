# VR-WA-000-PREFLIGHT — Reconciliar e preservar o baseline do MVP

## Resultado esperado

Base atribuída e recuperável, programa consistente e inventário de falhas anteriores sem apagar trabalho existente.

## Estado

`done`. Pacote P00, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `.gitignore`, `AGENTS.md`, `.graphifyignore`, `.graphify/config.json`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `program/program.json`, `program/repositories.json`, `program/cards/VR-WA-000-PREFLIGHT.md`, `program/evidence/VR-WA-000-PREFLIGHT.md`, `docs/workspace/preflight-2026-10-30.md`.
- `app`: `.gitignore`, `.graphifyignore`, `AGENTS.md`, `.graphify/config.json`, `scripts/graphify_refresh.sh`, `backlog/QA-Execution-Log.md`, `backlog/Flutter-Cards.md`.
- `backend`: `.gitignore`, `.graphifyignore`, `AGENTS.md`, `.graphify/config.json`, `scripts/graphify_refresh.sh`, `backlog/QA-Execution-Log.md`, `backlog/SpringBoot-Cards.md`.

Somente configuração/documentação e correção do wrapper Graphify; nenhuma funcionalidade do produto. O backup da entrada preserva bytes, diffs e SHAs dos três repositórios.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

Nenhuma; primeiro trabalho de preparação, sem implantar ou alterar funcionalidade nesta abertura.

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [x] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

Baseline observado em 30/10: alterações preexistentes limitadas ao padrão Graphify; backup privado em `~/.local/share/vanrides/preflight/20261030T163110Z`. Atribuição: configuração compartilhada anterior, adotada após revisão neste card, sem autoria inferida. Correção necessária: GRAPHIFY_FORCE era ignorado pelo early exec. Runtimes serão validados em 010; checks deste card cobrem o controle e os wrappers.

## Plano sequencial

1. Salvar diffs/arquivos não rastreados e SHAs dos três repositórios; conferir autoria e escopo dos 14 dirty paths observados.
2. Revisar as mudanças de Graphify/AGENTS por repositório; reconciliar o wrapper que ignora GRAPHIFY_FORCE se confirmado; adotar somente mudanças compreendidas em commits próprios ou preservá-las em checkout isolado recuperável.
3. Atualizar baseline e proveniência local/remota por leitura; não substituir o serviço remoto pelo HEAD local. Não alterar knownDirtyPaths para esconder falhas.
4. Executar controle core, testes de governança e Graphify; inventariar gates de runtime executáveis e faltas de ferramentas para 010. Atualizar dashboard apenas com backup e estado canônico consistente.

## Critérios de aceite

- [x] Mudanças anteriores preservadas e atribuídas, com caminho de restauração.
- [x] Controle core sem erro novo ou dirty path inexplicado; nenhum gate enfraquecido.
- [x] SHAs, inventário de ferramentas e falhas de baseline registrados; ausências de SDK não são reportadas como PASS.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Testes do coordenador; check core; diff explícito por repositório; Graphify status/ensure. Gates de runtime que dependem de SDK ficam em 010 com resultado NOT_RUN justificado.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-000-PREFLIGHT.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Restaurar backups específicos e reverter apenas commits próprios; preservar o estado anterior do dashboard e nenhuma alteração externa.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

Conclusão de 30/10/2026: controle core PASS sem erros, 70 testes PASS, wrappers normais CURRENT e revisão independente sem achados. Commits: `coordination:5be29675992631b11c7675805e7bb29baaf0688f`, `app:91ea136693bc727eac4ba87dd1469fe71dd616fb`, `backend:a262154fdee8c811563dc70b223ddea1b9aa1f2a`. Evidência: [VR-WA-000-PREFLIGHT](../evidence/VR-WA-000-PREFLIGHT.md). Runtimes seguem em 010, sem liberação de piloto/produção.
