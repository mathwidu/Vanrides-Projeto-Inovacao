# VR-WA-040-DOMAIN-AUTH — Implementar domínio, contratos e autorização do WhatsApp

## Resultado esperado

Modelo sem empresa na UX, comandos versionados e principals com escopo mínimo.

## Estado

`done`. Pacote P02, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

### IA de execução — 31/10/2026

Backend observado limpo em `f930e6eb7f156b285d65c1376818337063bfc0e9`; última
migrationV25. Coordenação em `291fdeb53572`;030 aguarda participação física,
conforme ADR-007. Java17.0.21, Maven3.9.9 e Docker29.8.2 local por socket Unix
confirmados; Docker limitado a2CPU/4GiB, sem containers ativos na preparação.
Executar builds/gates sequencialmente com controle de memória. Não operar o
iPhone físico nem iniciar outra coleta enquanto040 estiver ativo.

Fatia040: contrato OpenAPI e schemas fechados de todas as famílias; schema
`wa` aditivo; identidade/alias antes de perfil; autorização de serviço e
comando humano derivado de inbox; idempotência; bootstrap restrito, pareamento
backend e revogação. A jornada verificável usa fixtures sintéticas internas:
identidade/inbox com lease válido → bootstrap → confirmação de pareamento →
resgate com prova da chave/PKCE → status/revogação. Adapter real, claims de
transporte e conversa completa ficam em050/070; app nativo/links em060;
rotinas/passageiros/presença em080/090/100. Comando ainda não implementado não
recebe sucesso nem fica anunciado como ação executável.

Parecer de arquitetura independente: guard dedicado antes do CORS global,
allowlist de método/rota/principal e negação de descendentes desconhecidos dos
prefixos novos. Preservar `FilterAuthBasic`, WS, rotas e modelos empresariais.
Adicionar `X-Inbox-Lease` a context/commands/result; revalidar evento, conversa,
credencial, geração e expiração dentro da transação do efeito. Deduplicação
também por evento, para troca da chave do cliente não repetir o comando.

Pareamento: chave públicaP-256, PKCE S256, provaDPoP/ES256 via JJWT existente,
URL canônica de configuração, nonce/JTI e digest do corpo no perfilv1 para
begin/redeem. Pedido/chave/desafio/ambiente imutáveis. Cinco tentativas por
ator/janela, cinco minutos, código legível de oito caracteres base20. Resgate
concorrente serializa o motorista e revoga credenciais/dispositivo anteriores;
access/refresh são opacos, com hash, validade, rotação e vínculo à chave.
Usar primitives padrão; testes com duas chaves/pedidos e replay são obrigatórios.

Operação restrita por CLI, sem abrir administração HTTP pública. Credenciais
separadas por principal/ambiente, sem fallback; suporte `_FILE` explícito e
configuração incompleta falhando fechada. Seed/reset exigem ambiente e lote
sintético exatos, recusam produção e registros fora do lote. Flags desligam
novos efeitos, preservando revogação/parada autenticadas. Nenhuma credencial
real, ambiente remoto ou mensagem WhatsApp será usado nesta etapa local.

Paths adicionais justificados: `docker-entrypoint.sh` para `_FILE` e
`scripts/qa_integration_profile.sh`/`scripts/ci_quality_gate_test.py` para
expandir a matriz PostgreSQL obrigatória com os novos testes. Não relaxar
matriz, skips, ratchet ou proveniência. Dependência de validação OpenAPI,
se necessária, somente em escopo de teste; sem atualizar Spring/Jackson do
runtime. Confirmar compatibilidade no gate completo.
O `AGENTS.md` do backend recebe apenas a atualização da referência da matriz,
para não manter a contagem antiga como instrução operacional; a fonte numérica
continua `EXPECTED_TEST_CLASSES` no script versionado.
Após interrupção real por pressão de memória, `.mvn/jvm.config` limita o Maven
a384MiB de heap/256MiB de metaspace e o Surefire existente usa um único fork
de512MiB/256MiB, sem alterar versões ou selecionar menos testes. Configuração
versionada no checkout, sem overrides herdados proibidos pelo gate. Verificação
prévia: Maven3.9.9, descritor instalado Surefire3.5.3 e documentação oficial.
O teste existente `scripts/qa_maven_evidence_lock_test.py` também recebe uma
correção de fixture: aplicar explicitamente0755 após mkdir, pois umask077
transformava a pasta insegura pretendida em0700. O lock continua inalterado;
a regressão exige a permissão insegura real antes de verificar a rejeição.

Sequência: congelar contrato/base de schema; implementar fundação persistente
e guard; integrar protocolo/CLI; testes focados de contrato/RBAC/concorrência;
revisão independente; commit local e gate canônico em PostgreSQL real; smoke
do mesmo JAR/SHA com banco isolado e evidência sanitizada. Rollback local:
reverter código próprio; preservar migrations aplicadas e banco de ensaio
separado. Nenhum push/deploy está incluído nesta execução.

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`, `docker-entrypoint.sh`, `scripts/qa_integration_profile.sh`, `scripts/ci_quality_gate_test.py`, `scripts/qa_maven_evidence_lock_test.py`, `AGENTS.md`, `.mvn/jvm.config`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-020-GPS-SPIKE](VR-WA-020-GPS-SPIKE.md), com contratos e fixtures
sintéticas. Conforme a [ADR-007](../decisions/ADR-007-whatsapp-android-first-development.md),
030 permanece obrigatório antes de160/180. Esta precedência permite construir
o domínio enquanto os ensaios físicos aguardam participação; não aprova M1.

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependência020 concluída; versões/dirty paths e recursos locais conferidos;030 permanece pendente conforme ADR-007.
- [x] Gates, smoke, evidência e sequência confirmados na IA acima; PostgreSQL real é obrigatório antes de concluir.

## Plano sequencial

1. Materializar OpenAPI/DTOs e tabelas/invariantes dos documentos 02/08; alocar migrations após verificar o último número.
2. Permitir inbox/alias antes de perfil e bootstrap restrito do motorista; separar CHANNEL, trabalhador humano, SYSTEM_JOB, DEVICE e OPERATOR.
3. Implementar idempotência, erros estáveis, RBAC, constraints e fluxo de pareamento pendente com prova do aparelho.
4. Criar seed/reset de teste limitado a ambiente/lote; default deny e perfil de deploy isolado do legado.
5. Testar banco vazio, upgrade de fixture legada e regressão de auth/rotas compartilhadas.

## Critérios de aceite

- [x] OpenAPI validada com exemplos sintéticos por família; nenhum comando escolhe ator pelo LLM.
- [x] Bootstrap desconhecido e jobs não usam identidade humana fictícia.
- [x] Migrations, unicidade, isolamento e rollback compatível passam em PostgreSQL real.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gate backend e smoke local do mesmo SHA; testes de contrato/concorrência; T10/T18/T30/T34/T35/T38/T41.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-040-DOMAIN-AUTH.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Desligar novos fluxos, manter parada/revogação, voltar imagem compatível; migrations aditivas não são removidas com dados ativos.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Resultado da execução local — 31/10/2026

Backend técnico `8b5676b1868b30785497cf81a7ff1d67db1d0332`; fechamento
documental `a25978a529a797d045ed6898791e24a1bf8459f6`. Gate1154/1154
(matriz PostgreSQL125 incluída) e jornada HTTP/CLI do mesmo JAR passaram,
sem falha/erro/skip. Evidência e limites em
[VR-WA-040-DOMAIN-AUTH.md](../evidence/VR-WA-040-DOMAIN-AUTH.md).
Coordenador `90c488838174be7b699bdad757d9b2cf4197058f` registra o smoke e
as evidências; governança59/59 +11/11 e checkcorePASS. Estado canônico no programa.
