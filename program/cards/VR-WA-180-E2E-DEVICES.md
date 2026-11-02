# VR-WA-180-E2E-DEVICES — Validar todas as funções em iPhone e Redmi 8

## Resultado esperado

Entrega integrada comprovada no canal real e nos dois aparelhos físicos.

## Estado

`analyzed`. Pacote P13, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`, `Dockerfile`, `docker-entrypoint.sh`, `scripts/`, `.github/workflows/`, `VPS_RUNBOOK.md`, `DEPLOY.md`.
- `app`: `lib/`, `test/`, `integration_test/`, `android/`, `ios/`, `pubspec.yaml`, `pubspec.lock`, `backlog/`, `docs/`, `README.md`, `scripts/`, `.github/workflows/`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-030-GPS-PROOF](VR-WA-030-GPS-PROOF.md): M1 deve estar aprovado
separadamente em Android e iPhone, conforme a
[ADR-007](../decisions/ADR-007-whatsapp-android-first-development.md).

[VR-WA-170-TEST-ENVIRONMENT](VR-WA-170-TEST-ENVIRONMENT.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [ ] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [ ] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Executar roteiro 09 completo com contatos autorizados e build final, invertendo o papel de motorista por plataforma.
2. Repetir percursos/métricas críticas, instalação, bateria, interrupção, alerta no destinatário e fim automático.
3. Executar T01–T43 aplicáveis e regressões; tratar cada falha com evidência, correção e novo manifest quando código mudar.
4. Revisar logs, orçamento, restore/rollback, distribuição e ausência de efeitos em outros serviços.
5. Preparar demonstração reproduzível para proprietário; bloquear entrega pronta se requisito essencial falhar.

## Critérios de aceite

- [ ] Ciclo completo passa separadamente em iPhone/Android, sem simulação substituindo evidência física.
- [ ] Avisos reais úteis, zero ação por parada, zero erro crítico de destinatário/fim/segredo.
- [ ] Matriz e relatório distinguem PASS/FAIL/BLOCKED/NOT_RUN; nenhum requisito essencial dispensado.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Roteiro 09 e matriz T01–T43; manifest final; M0–M3 no escopo de testes controlados, sem promover M4/G6/G7.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-180-E2E-DEVICES.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Pausar coorte, bloquear novos efeitos, parar coletores e retornar candidato anterior comprovado; corrigir antes de repetir ensaios afetados.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Integração recebida do 060

Conferir onboarding, links a frio e quente vindos do WhatsApp, permissões, recuperação, percurso e fim de coleta nos dois binários e aparelhos do manifest final.
