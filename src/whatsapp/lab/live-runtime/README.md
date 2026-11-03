# Preparação do ensaio limitado de transporte

Estas ferramentas não autorizam execução, pareamento, envio, deploy ou promoção.
O perfil `MONOREPO_WA120_V1` prepara um ensaio de até 30 minutos, sem IA, novos
starts, portas públicas ou retry de envio incerto. O ambiente permanente tem
decisão e gates separados. Nenhum comando abaixo inicia o runtime.

`contract.json`, `receipt.py`, `canonical110.py` e `canonical110-pins.json`
preservam o perfil histórico. Não preencher esses documentos com evidência da
árvore centralizada. `evidence.py` distingue explicitamente schema1 histórico
de schema2 monorepo; perfil desconhecido não recebe fallback.

## Exportação na CI

Executar no workflow manual `.github/workflows/laboratorio-whatsapp.yml`, no
job `candidate`, somente depois do gate completo terminar com sucesso. O exporter
confere os campos reais de ambiente `GITHUB_REPOSITORY`, `GITHUB_SHA`,
`GITHUB_RUN_ID`, `GITHUB_RUN_ATTEMPT`, `GITHUB_WORKFLOW_REF`, `GITHUB_EVENT_NAME`
e `GITHUB_JOB`. O evento aceito é `workflow_dispatch`.

Antes de executar o gate, reservar uma raiz de evidências nova e exclusiva em
`VANRIDES_QA_EVIDENCE_ROOT`, fora do Git. O exporter exige exatamente um filho
`integration-UTC-pid` com `manifest.json` terminal; não seleciona o mais recente.

```sh
python3 -B src/whatsapp/lab/live-runtime/export.py gate \
  --evidence-root "$VANRIDES_QA_EVIDENCE_ROOT" \
  --repository-root "$GITHUB_WORKSPACE" \
  --output "$RUNNER_TEMP/vanrides-gate.json"
```

O gate exportado contém uma projeção sanitizada, a matriz obrigatória derivada
do script versionado, todos os resultados verdes sem skips e a fonte Git limpa.
O manifest bruto e XMLs não integram o artefato. `eligibleForRelease` permanece
`false`; esta exportação não substitui o término bem-sucedido do job.

Empacotar uma vez e executar as seis jornadas no mesmo JAR. O smoke grava seu
recibo estruturado em `VANRIDES_ACADEMIC_RECEIPT_PATH`, absoluto, novo e fora do
checkout. Ele deve comprovar 070/080/100/110/120/130, provedor sintético,
Node 22.23.2 em Linux/x64 e cleanup. Nenhum log é usado como recibo.

```sh
python3 -B src/whatsapp/lab/live-runtime/export.py bundle \
  --gate "$RUNNER_TEMP/vanrides-gate.json" \
  --smoke "$VANRIDES_ACADEMIC_RECEIPT_PATH" \
  --jar "$JAR_TESTADO" \
  --repository-root "$GITHUB_WORKSPACE" \
  --output "$RUNNER_TEMP/vanrides-lab-candidate"
```

O diretório novo contém apenas `backend.jar`, `gate.json`, `smoke.json` e
`candidate.json`. Publicar somente esses quatro arquivos, com nome de artefato
`whatsapp-lab-SHA-RUN_ID-RUN_ATTEMPT`. O JAR exportado é o testado, sem reconstrução
posterior. O exporter não faz upload nem chama rede.

## Coleta após o run terminar

O principal confere a revisão e obtém o ZIP do artefato exato, IDs reais e digest
independente da API GitHub. Não usar o hash calculado apenas pelo próprio ZIP
como âncora. `collect.py` executa somente três `gh api GET`: run, jobs da tentativa
exata e artefato. Não instala/autentica ferramentas nem aceita run em andamento,
rerun diferente, job falho, artefato expirado ou outro workflow. O nome esperado
do job na API é `Testar e preservar o mesmo binário`; seu ID de YAML é `candidate`.

```sh
python3 -B src/whatsapp/lab/live-runtime/collect.py \
  --artifact-zip "$ZIP_EXATO" \
  --expected-artifact-sha256 "$DIGEST_INDEPENDENTE_SEM_PREFIXO" \
  --run-id "$RUN_ID" --artifact-id "$ARTIFACT_ID" \
  --expected-source-sha "$SHA_REVISADO" --expected-source-tree "$TREE_REVISADA" \
  --images-file "$ARQUIVO_PUBLICO_COM_CINCO_PINS" \
  --output "$NOVO_DIRETORIO_PRIVADO_FORA_DO_GIT"
```

O arquivo de imagens contém exatamente `java`, `node`, `postgres`, `evolution`
e `redis`, cada valor fixado por `@sha256:`. Conferir disponibilidade, plataforma
e capacidade do destino separadamente. Não copiar ambientes ou sessões antigos.

O coletor produz `backend.jar`, `verified-ci-receipt.json` e `contract.json`
schema2, todos com modo 0600. O contrato fixa SHA/tree, matriz Git, bytes/hash do
JAR, IDs/digests reais de CI e digest do envelope. Seu estado continua sendo
`PINNED_FOR_PREFLIGHT`. Não há entrada de CLI para declarar release aceita ou
runtime autorizado. A API GitHub e o digest independente são a origem externa
da prova; os testes locais usam apenas fixtures declaradas, sem chamadas reais.

## Empacotamento local posterior

Usar o checkout limpo do mesmo SHA/tree do contrato, com os mesmos fontes. A
ferramenta confere a matriz antes e depois da cópia; diretórios de saída devem
ser novos, privados e externos ao Git.

```sh
python3 -B src/whatsapp/lab/live-runtime/prepare.py \
  --contract "$COLETA/contract.json" \
  --jar "$COLETA/backend.jar" \
  --verified-receipt "$COLETA/verified-ci-receipt.json" \
  --destination "$PACOTE_NOVO_FORA_DO_GIT"
```

A inspeção, extração e importação offline não provam que o destino está pronto.
Transporte remoto, preflight efetivo, exibição local autorizada do QR, scan no
WhatsApp Business do Redmi, corroborar destinatário, envio único e callback
correlacionado continuam sendo etapas explícitas. Não iniciar GPS/iPhone físico
nem ativar workflows por ter obtido o pacote.

## Verificação local

```sh
python3 -B -m unittest discover -s src/whatsapp/lab/live-runtime -p '*_test.py'
```

Os testes usam arquivos/contatos fictícios e Git temporário próprio. Não executam
GitHub, SSH, Docker, JVM ou aparelhos. Cobrem perfis separados, artefato de quatro
arquivos, alterações de SHA/JAR/tentativa, travessia de caminhos, symlinks,
gate incompleto/skips, jornadas ausentes, cleanup e importação após extração.
