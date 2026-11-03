# VR-WA-020-GPS-SPIKE — Construir prova isolada de coleta em segundo plano

## Resultado esperado

Coletor e receptor autenticado de ensaio prontos sem depender do cadastro completo.

## Estado

`done`. Pacote P01, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

Escopo congelado em 30/10: somente app driver, receptor dedicado na coordenação e
controle/evidência. Backend Spring permanece intacto: auth/empresa e persistência
legadas não atendem ao laboratório. Baselines limpas: coordenação 70e6173862e1450f180fdc16701874c673578580,
app bab06503d315da750f226c46e5c2ba59ba73a676, backend f930e6eb7f156b285d65c1376818337063bfc0e9.
010 concluído após confirmação humana dos dois aparelhos sem USB. Gates: contrato e
unittest do receptor, Flutter canônico, compilação nativa Android/iOS, integração em
emulador/simulador com fixture sintética. Receptor público será preparado para aprovação;
percurso e expiração física são 030. Rollback local por commits; nenhuma publicação agora.

## Contratos

Contrato v1 congelado: [GPS spike](../../src/whatsapp/gps-spike/CONTRACT.md).
Fila em memória limitada, bearer individual de ensaio sem alegação de device binding.

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `.github/workflows/gps-spike-ci.yml`, `.github/workflows/gps-spike-publish.yml`, `docs/runbooks/whatsapp-mvp/`.
- `app`: `lib/`, `test/`, `integration_test/`, `android/`, `ios/`, `pubspec.yaml`, `pubspec.lock`, `dart_test.yaml`, `backlog/`, `docs/`, `README.md`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-010-TOOLCHAINS](VR-WA-010-TOOLCHAINS.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [x] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Definir interface única de coleta, amostra/ACK/lease/stop e fixtures sintéticas de pontos e identidade.
2. Implementar serviço Android e Core Location iOS fora do ciclo do widget; fila limitada, relógio, geração, rede e expiração nativa.
3. Preparar receptor HTTPS de prova com autenticação/allowlist, diagnósticos agregados e sink de aviso; sem endpoints de prova em produção.
4. Gerar builds/manifest e plano concreto para receptor alcançável em rede móvel; antes de qualquer publicação aplicar procedimento de VPS.
5. Executar testes locais de lote, perda de ACK, replay, geração/lease e parada; registrar as limitações que o campo vai resolver.

## Critérios de aceite

- [x] Coleta/ingestão de prova funciona localmente nas duas plataformas; UI não é dona do stream.
- [x] Receptor/artefatos e autorização externa necessária preparados sem publicar implicitamente.
- [x] GPS/logs minimizados; fixture de prova não contamina dados reais.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gates módulos afetados; integração receiver/app sintética; T12–T16/T30/T33 locais; simulador não substitui 030.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-020-GPS-SPIKE.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Desabilitar receptor/flag de prova, revogar identidades e parar coletores; reverter commits sem apagar histórico do ensaio.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Resultado registrado em 30/10/2026

PASS no escopo local: 704 testes Flutter, 10 Kotlin, 11 Swift core,
44 testes do receptor, integração nativa final 4 cenários Android e 3 iOS e smoke real de
gateway/backup/restore. Artefatos assinados 0.1.0+2 preservados e manifest
sanitizado registrado no Git local. [Evidência canônica](../evidence/VR-WA-020-GPS-SPIKE.md)
e [plano externo](../../docs/runbooks/whatsapp-mvp/020-publicacao-laboratorio.md).
030 aguarda aprovação, DNS e acesso GHCR; não há prova física nem MVP completo.
