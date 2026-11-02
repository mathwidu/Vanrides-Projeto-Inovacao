# VR-WA-150-OPERATIONS — Preparar operação, privacidade, segredos e restauração

## Resultado esperado

MVP operável em ambiente restrito sem afetar serviços compartilhados e com recuperação ensaiada.

## Estado

`analyzed`. Pacote P12, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

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

[VR-WA-040-DOMAIN-AUTH](VR-WA-040-DOMAIN-AUTH.md), [VR-WA-140-FINISH-SUPPORT](VR-WA-140-FINISH-SUPPORT.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [ ] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [ ] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Implementar flags, limits, secret _FILE e fail-closed; configurações por ambiente e ingress de endpoints permitidos.
2. Concluir retenção/purge/export/revogação e notice; sanitizar proxy/app/backend/n8n e limitar acesso.
3. Preparar backup/restore isolado com reaplicação de exclusões, health/alertas e suporte/QR Evolution.
4. Medir carga sintética e custo; apresentar teto financeiro real antes de uso pago ou expansão.
5. Ensaiar kill switches com run ativo, restore/rollback e verificar saúde de serviços compartilhados.
6. Implementar os jobs SYSTEM_JOB de W08 e validar T35: janela/relógio/limite próprios, rejeição do principal humano, reexecução idempotente e auditoria sem inbox fictícia. Materialização e lembretes reutilizam o domínio100;070 cobre somente a separação de autenticação/contrato, com handler ainda503.

## Critérios de aceite

- [ ] Parada/finish continuam funcionando com novos starts/avisos desabilitados.
- [ ] Restore efetivamente executado e purge comprovado; sem segredo/GPS em evidência.
- [ ] Runbook tem monitoramento, limites, contato do operador e recuperação reproduzível.
- [ ] T35 completo: jobs reais com principal SYSTEM_JOB separado, sem ator humano forjado e sem duplicação após retry/restart.
- [ ] T38 operacional: export/import, credenciais, persistência e restart verificados no modo de execução escolhido; queue/cron não herdam PASS do CLI regular070.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gates todos afetados, T27/T28/T33/T35/T38/T41/T42, carga/restore em ambiente autorizado. T35/T38 completos permanecem pendentes até esta prova; não são aceites transferidos pelo encerramento local070.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-150-OPERATIONS.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Reverter apenas configuração versionada do MVP após backup, preservar encryption key n8n e segredos externos; nenhuma limpeza global incidental.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Retenção recebida da presença

Incluir as páginas e os cursores de `wa.attendance_job_page` e `wa.attendance_job_cursor` na limpeza de registros técnicos. Durante a janela de replay a API precisa continuar devolvendo o resultado da página já aplicada. O reset de uma coorte preserva esses registros compartilhados da instância. O teste de retenção deve mostrar que expirar um cursor ou remover uma página antiga preserva as escolhas dos passageiros e a unicidade do lembrete por pessoa e ocorrência.
