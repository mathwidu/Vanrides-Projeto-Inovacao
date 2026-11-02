# VR-WA-060-DRIVER-APP — Concluir app mínimo, links e pareamento seguro

## Resultado esperado

Motorista inicia o vínculo pelo app instalado, confirma o código no WhatsApp e verifica o estado do aparelho. A jornada completa inclui preparação para localização, início, parada e encerramento, com responsabilidades e provas distribuídas abaixo.

## Estado

`done`. Pacote P05, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `app`: `lib/`, `test/`, `integration_test/`, `android/`, `ios/`, `pubspec.yaml`, `pubspec.lock`, `backlog/`, `docs/`, `README.md`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

- A decisão de sequência também admite o adendo em `program/decisions/ADR-007-whatsapp-android-first-development.md`.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-020-GPS-SPIKE](VR-WA-020-GPS-SPIKE.md), [VR-WA-040-DOMAIN-AUTH](VR-WA-040-DOMAIN-AUTH.md), [VR-WA-080-DRIVER-ROUTINES](VR-WA-080-DRIVER-ROUTINES.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [x] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Criar telas mínimas de vínculo, permissões, status, parada/retomada, versão e ajuda; remover dependência de jornada/empresa legada.
2. Iniciar pareamento no app aberto pelo ícone, vincular pedido/chave/PKCE antes do WhatsApp e só então confirmar código na conversa; scheme apenas retorna ao pedido existente; Keychain/Keystore e revogação substituem armazenamento inseguro.
3. Implementar App Links Android e testar capabilities iOS; fallback HTTPS com botão/scheme sem consumir intenção em GET.
4. Integrar coletor validado, refresh/rotação, transições, renovação de lease e reconciliação após restart.
5. Sanitizar URI/segredos nos logs; testar atualização, expiração de assinatura e recuperação; artefatos assinados.

## Critérios de aceite

- [x] A chave de outro aparelho não ganha o vínculo, e a prévia ou o retorno por scheme não cria novo pedido. Ensaios host, links e Redmi aprovados.
- [x] Link a frio e retorno pelo ícone a quente verificados no Redmi. Os testes de apresentação distinguem abertura, primeira posição pendente, coleta ativa, GPS desatualizado, parada pendente e coleta interrompida. Execução real no contrato do percurso e seu GPS pertencem ao 110/030/180, conforme ADR-007.
- [x] Onboarding do aparelho verificado com usuário e canal sintéticos, worker e Spring reais e Redmi físico. Nenhuma foto, senha, empresa ou intervenção por parada faz parte do vínculo. Prova do canal WhatsApp publicado em 170 e validação bilateral em 030/180.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gates backend/app, contrato, builds/instalações físicas; T09–T16/T29–T33/T39/T40.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-060-DRIVER-APP.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Revogar intents/sessões afetadas, bloquear novos starts por versão, manter stop/recovery; reinstalação guiada sem promessa de downgrade automático.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Inventário preparatório — 31/10, sem ativação

Leitura independente do app9fda8da e contratos Spring040, preservados no
backend502e4ad. Grafo do app CURRENT na leitura, com semântica documental
pendente; relações críticas confirmadas nos fontes.080 continua pendente de
validação, portanto este inventário não promove060 nem altera seus aceites.

O companion ainda usa o protocolo020: bearer de teste, ambiente gps-spike e
tempos numéricos. Não basta trocar a URL para a API Spring. A fundação040 já
implementa begin/status/redeem/refresh/revoke e estado próprio, com P-256,
PKCE, DPoP, nonce, ath e hash do corpo exato. Rotas de activate/resume/finish,
samples/state ainda estão fechadas e pertencem110.

Reuso confirmado no app: entrypoint/flavor driver separado do legado,
DriverTrackingPlatform, estados de parada/reconciliação, fila e lease nativos,
armazenamento Android atômico/cifrado e Keychain iOS. Não migrar automaticamente
TEST_BEARER para uma sessão nova. Não reutilizar o listener legado, que possui
dependências antigas e registra a URI inteira. Chave privada, PKCE, tokens e
bytes de operações pendentes ficam nativos; Dart recebe estado sanitizado.

Proposta de delimitação a formalizar na IA/ADR-007 e matriz antes de iniciar:

- 060: pareamento completo e sessão segura contra Spring040, UX mínima, links
  fechados, refresh/revogação, contratos e preservação do coletor por fixtures.
- 110: início/snapshot/telemetria/lease/estado GPS ponta a ponta em Spring,
  substituindo o endpoint e a credencial de spike.
- 030/180: preservar prova física bilateral e repetir onboarding, links,
  permissões, recuperação/percurso/energia/encerramento nos binários finais.
  iOS host não comprova iPhone físico; pausa do proprietário continua vigente.

Essa proposta não dispensa critérios: o texto atual060 promete coleta real em
ambos os sistemas. Não marcar done com esse texto e apenas testes host. É preciso
distribuir formalmente as obrigações sem lacuna; não adicionar060→110, pois110
já depende060. Associação HTTPS preparada não equivale a App Links verificados.

Primeiro incremento previsto: chave P-256 no AndroidKeyStore, pedido/PKCE e
operações idempotentes persistidos; transporte DPoP com nova prova por retry e
mesmo corpo/idempotency key; bridge e tela de vínculo; smoke begin→confirmação
por inbox sintética→redeem→status→refresh/resposta perdida→revoke. O código
recebido por URI nunca cria ou substitui pedido/chave. READY da API não significa
GPS ativo. Depois, adapter iOS e testes host, seguidos de links cold/warm e UX.

Antes do primeiro write: fechar080, adicionar040 à dependência explícita,
conferir README/arquitetura/app e contratos atuais, registrar paths e ambiente,
validar produtor/consumidor no mesmo manifest e confirmar DoR. Um build pesado
por vez; nenhum teste físico iOS enquanto durar a pausa. Rollback local não
restaura sessão revogada no servidor. Nenhum desses incrementos foi implementado
neste inventário.


## IA executável de 31/10/2026

Insumos verificados no Mac após o fechamento 080. App limpo em
`9fda8dad7c479443996ce026bdf44e593624bee1`, backend técnico em
`aec364e2265bd1a7988b5c9c7fb06533ccb25140` e backend documental em
`8411f9ac47fb59517e57aa7165d97d7c8e55c66e`. O grafo do app estava atual
para código. As fronteiras de DriverSetupApp, DriverActivity e DriverTrackingBridge
foram confirmadas nos fontes. A semântica documental do índice está pendente.

O fluxo do ícone e a retomada do WhatsApp usam um canal de sessão separado de
tracking.v1. O servidor 040 aceita o código puro de oito consoantes. A inbox o
transforma em hash e o worker confirma usando o contexto autorizado do próprio
motorista. A tela pede o código individualmente. PKCE, chaves, tokens e bytes
pendentes pertencem aos adapters nativos. O flavor legado e o coletor do spike
possuem pontos de entrada próprios. Um vínculo pronto indica cadastro do aparelho.

### Sequência e responsáveis

1. App Android. Implementar chave P-256 no Keystore, journal cifrado de operações,
   transporte HTTPS/DPoP, vínculo, refresh e revogação. O adapter iOS usa a mesma
   interface com Security/Keychain. Executar testes host e compilar para destino
   genérico sem operação no iPhone físico.
2. Flutter. Ação explícita no ícone para iniciar, exibição transitória do
   código, retorno à conversa, sincronização ao voltar, ajuda e desvinculação.
   A rota de retorno só permite reconciliar pedido local existente. A compilação
   do laboratório tem origem fixa e a versão distribuída exige configuração.
3. Integração. Backend e worker no Mac, canal sintético, CA privada do laboratório,
   certificado com SAN localhost, proxy HTTPS preso a loopback e adb reverse no
   Android. Validar begin, evento autenticado pela inbox, worker, redeem, status,
   recuperação de resposta perdida, replay e revogação. Registrar versões e
   limites. A associação de domínio externo e o ensaio real do WhatsApp são
   verificados no ambiente de teste, em 170 e 180.
4. Análise e testes Flutter, Kotlin/Swift, regressão do coletor, build do Redmi,
   binário genérico iOS e smoke do mesmo SHA. Uma carga pesada de cada vez.
   A ausência do dispositivo, da assinatura ou da origem válida fica visível no
   quadro de QA. A conta Apple e o histórico do Redmi permanecem recuperáveis.

### Contrato da primeira fatia

Canal `dev.matheusduarte.vanrides.driver/session.v1`. Métodos sem argumentos
`getState`, `beginPairing`, `syncSession`, `refreshSession`, `revokeSession`
e `showPairingCode`. Os cinco primeiros devolvem mapa fechado com `state`;
`expiresAt` ISO UTC é opcional em PENDING. Estados aceitos são UNCONFIGURED,
UNPAIRED, REQUESTING, PENDING, SETUP_REQUIRED, READY, EXPIRED, REVOKING e
REVOKED. Somente showPairingCode retorna string de oito letras ou null. A sessão
da tela é sincronizada após aprovação e rede indisponível permite tentar novamente.
A revogação exige confirmação do motorista. Acesso a um link de retorno nunca
cria uma chave, troca o vínculo ou dispara GPS. Os builds sem configuração
mostram instrução local; a URL nunca vem de mensagem ou argumento do Flutter.

Critérios originais de onboarding, links a frio/quente e estados de coleta
continuam rastreados. Neste card ficam a sessão, os links de retorno e a
representação verídica do estado do aparelho. O 110 integra a ativação de
percurso, amostras, lease, reconciliação e stop no Spring. Os cards 030 e 180
comprovam os dois aparelhos e a jornada final. A promoção 160 exige 030.
A evidência técnica de cada etapa identifica exatamente o que foi exercitado;
o aceite do MVP continua bilateral.

Reversão local por entrypoint de laboratório e binário identificado do spike;
revogar a sessão no servidor antes de retirar o adapter. Preservar fila e provas
do coletor 030. O rollback não reativa uma credencial já revogada.

### Retorno pelo ícone e falhas transitórias

O canal nativo `dev.matheusduarte.vanrides.driver/launch.v1` expõe
`isLauncherVisit`, sem argumentos. No Android, uma chamada em primeiro plano
com entrada MAIN/LAUNCHER permite mostrar novamente a ação explícita de
vincular. A entrada pelo link desabilita essa ação. No iOS, a recuperação de
uma entrada anterior pelo link orienta abrir a frio pelo ícone. A UI mostra o
estado local persistido quando ocorre um erro de rede e faz uma consulta
automática após três segundos se o app continua visível. A tentativa é única;
o botão Atualizar vínculo permite a próxima ação do motorista.

O registro iOS novo usa a proteção de chave e journal necessária após o
primeiro desbloqueio do aparelho. Um registro v1 vivo conserva sua chave,
incluindo os pedidos com confirmação pendente. Encerrado esse vínculo, o
primeiro novo pedido explícito usa a política v2. O ensaio de coleta assinada
com tela bloqueada depende do 110 e da prova física 030/180.

## Resultado registrado em 31/10/2026

Vínculo e recuperação aprovados nos dois APKs Android +10 individualmente, com gates de app, contrato e backend e build genérico iOS. [Evidência canônica](../evidence/VR-WA-060-DRIVER-APP.md).
