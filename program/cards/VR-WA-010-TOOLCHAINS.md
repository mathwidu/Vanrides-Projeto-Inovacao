# VR-WA-010-TOOLCHAINS — Preparar SDKs e instalar builds mínimos nos aparelhos

## Resultado esperado

Mac capaz de compilar/testar o baseline e instalar builds de teste no Redmi 8 e iPhone sem debugger.

## Estado

`done`. Pacote P00, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Execução iniciada em 30/10 após 000 concluído. Impacto concreto: toolchains locais dedicados; matriz Gradle/AGP/Kotlin; flavor/entrypoint mínimo de diagnóstico e assinatura de teste; bootstrap de PostgreSQL local e gates do baseline. Sem endpoints de negócio, GPS contínuo ou deploy neste card. Diretórios pessoais novos serão registrados e configurações anteriores preservadas; nenhum perfil de shell/default Java/Ruby será substituído.

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; neste card, apenas preparação/build/diagnóstico, sem contratos de negócio do MVP.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `app`: `lib/`, `test/`, `integration_test/`, `android/`, `ios/`, `pubspec.yaml`, `pubspec.lock`, `backlog/`, `docs/`, `README.md`, `AGENTS.md`, `scripts/`, `.github/workflows/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`, `Dockerfile`, `docker-entrypoint.sh`, `scripts/`, `.github/workflows/`, `VPS_RUNBOOK.md`, `DEPLOY.md`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-000-PREFLIGHT](VR-WA-000-PREFLIGHT.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [x] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Conferir instalação existente e documentação oficial; usar Flutter/Java/Pods dedicados, sem mudar defaults do Mac.
2. Instalar Android Studio ARM64/SDK/platform-tools; alinhar AGP 8.11.1, Gradle 8.14 e KGP 2.2.20 para Flutter 3.38.5; registrar matriz/lockfiles e licenças.
3. Preparar flavor/entrypoint mínimo sem Maps/Places obrigatório, identidade estável e assinatura APK própria; conferir Android/MIUI/ABI via aparelho. Observado: Redmi **Note** 8, Android 10/API29, MIUI12, arm64/ARMv7; denominação inicial corrigida a partir do hardware conectado.
4. Configurar Personal Team no Xcode com login do proprietário, testar capabilities/links e instalação iOS sem debugger; documentar renovação de sete dias.
5. Preparar PostgreSQL local isolado e executar gates de baseline app/backend; corrigir incompatibilidades dentro do escopo ou registrar impedimento específico antes de avançar.

## Critérios de aceite

- [x] APK e build iOS mínimos abrem após desconectar o Mac; signing/capabilities observados.
- [x] Gates de baseline executados sem skips indevidos; versões documentadas.
- [x] Instalação local e restauração de configuração documentadas; nenhuma credencial em Git.

## Aceite físico

Em 30/10/2026, o proprietário confirmou a reabertura no iPhone e, depois de
carregar o aparelho, confirmou que o Redmi Note 8 abriu e permaneceu aberto fora
do USB do Mac. A pendência foi satisfeita, sem waiver. Evidência:
[registro 010](../evidence/VR-WA-010-TOOLCHAINS.md) e
[manifest](../evidence/VR-WA-010-TOOLCHAINS-manifest.json).

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Procedimento reproduzível: [builds e instalação](../../docs/runbooks/whatsapp-mvp/010-builds-e-instalacao.md).

flutter doctor/version, java/pod/xcodebuild; gates canônicos, build Android/iOS, instalação física, abertura/relançamento. T09/T29/T31 no escopo de instalação.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-010-TOOLCHAINS.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Reverter alterações de build e usar versões anteriores preservadas; SDKs dedicados não exigem restaurar shell global. Não remover dependências compartilhadas sem inventário.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.
