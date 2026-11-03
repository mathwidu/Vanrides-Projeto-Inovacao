# VR-WA-170-TEST-ENVIRONMENT — Publicar ambiente de teste e integrar canal autorizado

## Resultado esperado

Artefatos aprovados acessíveis em HTTPS e conectados ao número exclusivo de teste.

## Estado

`analyzed`. Pacote P13, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`, `Dockerfile`, `docker-entrypoint.sh`, `scripts/`, `.github/workflows/`, `VPS_RUNBOOK.md`, `DEPLOY.md`.
- `app`: `lib/`, `test/`, `integration_test/`, `android/`, `ios/`, `pubspec.yaml`, `pubspec.lock`, `backlog/`, `docs/`, `README.md`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-160-RELEASE-PREP](VR-WA-160-RELEASE-PREP.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [ ] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [ ] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Confirmar autorização específica e manifest; usuário realiza DNS necessário; guardar backup/configuração anterior.
2. Publicar migrations/backend isolado com flags desligadas, ingress restrito e secrets próprios conforme skill de VPS.
3. Importar W01–W08, mapear referências/credenciais, conectar instância Evolution com proprietário e ativar somente coorte de teste.
4. Distribuir APK e instalar iOS do manifest; smoke de saúde, evento/pin, modelo, inbox/outbox, posição/stop sem ampliar audiência.
5. Reconciliar estado remoto/manifest e executar rollback se smoke reprovar; nenhum push/deploy extra implícito.

## Critérios de aceite

- [ ] URL/canal/instância e builds realmente correspondem ao manifest aprovado.
- [ ] Destinatários autorizados e isolados; produção/serviços alheios preservados.
- [ ] Smoke externo passa com rollback disponível e workflows corretamente referenciados.
- [ ] T35/T38: agendas e modo real de workers/filas são executados no ambiente autorizado, incluindo retry/restart e principal SYSTEM_JOB; importação CLI/SQLite070 não substitui esta prova.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Smoke ponta a ponta no digest externo, status de serviços e T17/T24/T28/T35/T37/T38/T41; resultado rastreável. Incluir jornada com OpenAI + API + Evolution/WhatsApp real na coorte autorizada; as provas locais separadas070 são antecedentes, não E2E externo.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-170-TEST-ENVIRONMENT.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Desligar novos efeitos, parar/reconciliar sessões, restaurar stack/workflows anteriores compatíveis e verificar saúde; DNS só pelo proprietário.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.
