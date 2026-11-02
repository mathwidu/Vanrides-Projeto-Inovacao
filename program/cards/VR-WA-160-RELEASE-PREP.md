# VR-WA-160-RELEASE-PREP — Congelar artefatos e preparar publicação de teste

## Resultado esperado

Entrega concreta e verificável pronta para aprovação externa, sem publicar nesta tarefa por suposição.

## Estado

`analyzed`. Pacote P13, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`, `deploy/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`, `Dockerfile`, `docker-entrypoint.sh`, `scripts/`, `.github/workflows/`, `VPS_RUNBOOK.md`, `DEPLOY.md`.
- `app`: `lib/`, `test/`, `integration_test/`, `android/`, `ios/`, `pubspec.yaml`, `pubspec.lock`, `backlog/`, `docs/`, `README.md`, `scripts/`, `.github/workflows/`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-030-GPS-PROOF](VR-WA-030-GPS-PROOF.md): M1 deve estar aprovado
separadamente em Android e iPhone, conforme a
[ADR-007](../decisions/ADR-007-whatsapp-android-first-development.md).

[VR-WA-150-OPERATIONS](VR-WA-150-OPERATIONS.md)

[VR-WA-120-DELIVERY-RECOVERY](VR-WA-120-DELIVERY-RECOVERY.md): mensagem e callback
reais precisam estar comprovados na conta autorizada antes de congelar o
candidato integrado. Testes sintéticos e prontidão de infraestrutura não
substituem esse gate; a ausência de número mantém o 120 em espera (RD44).

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [ ] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [ ] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Construir em checkout limpo da revisão avaliada; revisar CI/push dev/prod que pode disparar deploy.
2. Executar gates/regressões e montar manifest com APK/iOS/backend/workflows/schema/config/assinaturas não secretas.
3. Preparar imagem e arquivos de stack/ingress/credenciais/workflows inativos, inventário remoto atual, impacto e rollback ensaiado.
4. Preparar instruções curtas de instalação/renovação/onboarding e plano de smoke; solicitar aprovação específica somente com artefatos prontos.

## Critérios de aceite

- [ ] Artefatos têm hashes e origem reproduzível; nenhuma dependência de arquivo esquecido.
- [ ] Plano externo identifica serviço/ambiente/digest, migração, validação e reversão.
- [ ] iOS signing de desenvolvimento e limite de sete dias documentados; TestFlight não é requisito desta entrega.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gates canônicos limpos, smoke local mesmo SHA, revisão independente do diff/manifest, T28/T31/T38/T41.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-160-RELEASE-PREP.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Descartar candidato sem tocar ambiente externo; reverter somente commits de preparação se preciso, preservar build anterior.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.
