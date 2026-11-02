# VR-WA-190-HANDOFF — Entregar pacote reproduzível e guia de teste do MVP

## Resultado esperado

Proprietário pode instalar/renovar e testar todas as funcionalidades com documentação e evidências completas.

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

[VR-WA-180-E2E-DEVICES](VR-WA-180-E2E-DEVICES.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [ ] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [ ] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Revisar critérios da entrega e inexistência de defeito crítico/alto no escopo; confirmar cada artefato instalado.
2. Organizar APK/checksum, build iOS/renovação, URLs, contratos, workflows, manifests, runbooks e roteiro curto.
3. Registrar execução/aceite do proprietário sem confundir com teste automatizado; explicitar limites da conta gratuita.
4. Atualizar QA Log/backlogs/programa com commits/evidências corretos e próximos passos opcionais; manter HOLDs de piloto/produção.
5. Conferir checkout limpo de arquivos próprios e preservação de trabalho alheio; devolver pacote e relatório self-contained.

## Critérios de aceite

- [ ] Todas as funções solicitadas testáveis em ambos os aparelhos com caminho de instalação/renovação comprovado.
- [ ] Pacote não depende de credencial em chat, arquivo não versionado essencial ou intervenção oculta do executor.
- [ ] Relatório final honesto: resultados, versões, limites e zero impedimento essencial omitido.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Checklist 09 §6; verificação de links/hashes, controle core e auditoria final de manifest/evidência; sem repetir suites sem mudança.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-190-HANDOFF.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Retirar declaração de pronto se integridade/aceite falhar, preservar artefatos e histórico; não apagar dados ou reverter ambiente sem plano específico.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.
