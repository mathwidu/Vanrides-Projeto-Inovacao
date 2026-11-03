# VR-WA-070-AGENT-WORKFLOWS — Construir workflows e interpretação estruturada

## Resultado esperado

W01–W08 importáveis com estado na API, modelo limitado e fallback determinístico.

## Estado

`done` local. Proprietário confirmou o destino e concluiu a configuração
privada no Terminal. OPENAI_API_KEY presente no arquivo local aprovado,0600,
ignorado e não versionado; valor não inspecionado em saída. Smoke/corpus reais
concluídos; acesso confirmado e critérios de qualidade aprovados na fonte
f5377d0. Gates locais, smoke integrado e revisão de encerramento concluídos;
sem publicação ou ativação do canal real.
Pacote P03, épico `VR-WA-MVP`. Responsável de integração: executor principal;
módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

### IA de execução — 2026-10-31

Dependência050 concluída: backend técnico b69a39b4e9ab2b83de022af6df3ff01f9614bcd0,
HEAD documental6b884427e85ac9129f1023597b8b2af2375512c5; coordenação
b47107674a686e1e941c33c5995ed2642ce75ca0. Checkouts limpos, appb89e0fa preservado.
Graphify estrutural CURRENT, relações confirmadas nos fontes; semântica
pendente não usada como prova.030 continua aguardando físico; iPhone pausado.

Executar primeiro as partes determinísticas independentes de OpenAI. Chave
não encontrada no ambiente/arquivos locais usuais; decisão de criação/reuso
solicitada ao usuário pelo fluxo seguro. Nenhuma chave de outro projeto ou da
VPS será reutilizada implicitamente. Não implementar/chamar OpenAI antes da
resposta. A ausência de smoke real deve ficar explícita, nunca como PASS stub.

Estender os comandos de convite/pareamento com payload vazio fechado: a API
consome o hash da própria inbox autenticada, sem reconstruir/expor o segredo.
Preservar variante legada com código e sua verificação contra a mesma mensagem.
Adicionar RESPOND_TO_MESSAGE (25º comando, templates informativos enumerados)
e habilitar o mínimo de REQUEST_SUPPORT: caso deduplicado e pausa da conversa;
retomada e operação completa pertencem a140. Respostas negativas que já geram
recibo precisam de outbox atômica; W01 não executa um segundo comando no mesmo
evento. Nenhum texto livre do agente ou destinatário entra nesse caminho.

Contexto fornece etapa de cadastro e frescor da mensagem, sem nome, hash,
telefone ou GPS. Mensagens históricas/desconhecidas não autorizam novos efeitos
de negócio: janela inicial cinco minutos, tolerância futura30s, relógio da API.
AJUDA oferece orientação; atendimento humano explícito pausa a conversa. Pin,
áudio e localização ao vivo seguem caminhos determinísticos, sem modelo.
Aceite exige resposta inequívoca à versão do aviso, sem inferência do modelo.
Catálogo de respostas e direitos por estado são revalidados no backend.

n8n2.45.4 observado remotamente; validar imagem/versão local antes de importar.
Exports inativos com credenciais lógicas separadas, estado na API e flags de
retenção desabilitadas; isso não prova ausência de execução persistida.
W02–W05 permanecem esqueletos dos contratos de negócio; W08 verifica saúde,
com jobs SYSTEM reservados ao card150. IMPORT não equivale a ativação
nem comprovação de endpoints futuros. Limite de corpo/prazo total de W06 deve
preservar adapter050; fronteira n8n/adapter sob revisão antes de escrever esse
componente. Não alterar configuração/encryption key da instalação compartilhada.

Validação: corpus sintético>=60, import/export/restart em n8n local isolado,
falhas sem ação, contratos Java/Node/PostgreSQL, revisão independente, gate
canônico e smoke no mesmo JAR/SHA. Compilações/testes pesados sequenciais com
Docker2CPU/2GiB e guarda de memória. Sem simuladores ou IDEs abertos.

Adendo de recursos31/10: qa_gate_card no SHAaa3d977 foi interrompido pela
guarda após65,6s, com pressão2 em duas amostras; não é PASS. A API e o banco
exclusivos foram parados. Executar os mesmos componentes sequencialmente:
ci_quality_gate canônico com fonte limpa; só após seu término iniciar o mesmo
JAR e banco e executar os três checks HTTP básicos do qa_gate_card e o smoke
070. Registrar essa composição separada e o resultado interrompido, sem
atribuir PASS ao comando original nem dispensar matriz PostgreSQL ou smoke.

Paths adicionais backend: scripts/qa_integration_profile.sh e
scripts/ci_quality_gate_test.py, somente matriz obrigatória. Plano/contrato08
serão sincronizados com os25 comandos e implementação real. Rollback local:
parar os containers exclusivos, desativar exports próprios, preservar banco e
filas, reverter commits por repositório sem apagar migration aplicada.
Nenhum push, deploy, webhook, mensagem real ou teste físico está autorizado
implicitamente por esta etapa; a publicação anterior cobre somente a stack GPS.

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`, `scripts/qa_integration_profile.sh`, `scripts/ci_quality_gate_test.py`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-050-WHATSAPP-TRANSPORT](VR-WA-050-WHATSAPP-TRANSPORT.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependência050 concluída; versões/dirty paths conferidos. Recursos locais disponíveis; chave e smoke real dependem da decisão explicitada na IA, sem impedir as partes determinísticas.
- [x] Gates, smoke, evidência e sequência local definidos na IA; sem aprovação implícita de integração real.

## Plano sequencial

1. Implementar JSONs e manifest de nós/versões/subworkflows/credenciais, usando HTTP nativo e contratos 08.
2. Implementar W01/W06/W07/W08 e esqueletos contratuais W02–W05; fluxos de negócio serão completados nos cards seguintes, sem ativação prematura.
3. Implementar Responses API gpt-6-luna low com schema, limite de contexto/tempo/retry e minimização; configurar chave via cofre quando disponível.
4. Implementar VOU/NÃO VOU/AJUDA e respostas para áudio/localização ao vivo; erro do modelo não executa ação.
5. Validar export/import em instância de teste autorizada, corpus congelado e restart de workers; não alterar encryption key compartilhada.

## Critérios de aceite

- [x] Oito exports importam inativos com referências corretas e sem secrets; autenticação/contrato separam SYSTEM_JOB. W02–W05 são stubs e W08 verifica saúde, conforme IA; jobs/T35 e queue/cron/T38 completos permanecem nos aceites150/170.
- [x] Timeout/recusa/JSON inválido não executam comando; agente não muda identidade.
- [x] Stub, smoke/corpus reais e integração API/PG distinguidos; acesso ao modelo comprovado, corpus original e suplemento aprovados sem ocultar rodadas reprovadas.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Validação de JSON/contratos, corpus ≥60 exemplos, teste de importação e falhas; cobertura local de T17/T18/T27/T35–T38. T35 aqui cobre separação de principal; T38 cobre CLI regular/import/restart. Jobs, queue/agendas e E2E externo têm aceites explícitos150/170 e continuam NOT_RUN em070.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-070-AGENT-WORKFLOWS.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Desativar workflows da versão, restaurar export anterior e manter filas duráveis; revogar somente credenciais próprias afetadas.

## Encerramento

Checkpoint: backend1262/1262, PostgreSQL203, HTTP básico3/3, Node191/191,
Python23/23 e smoke worker/API12/12 passaram. n8n exato validado em CLI regular;
queue/agendamento não comprovados. Revisões corrigidas, fontes/hashes e
limitações na [evidência070](../evidence/VR-WA-070-AGENT-WORKFLOWS.md).
Serviços locais encerrados; recursos privados preservados. Esse checkpoint
determinístico foi complementado pela integração e avaliação real abaixo.
Seus hashes históricos não substituem a nova prova. A conclusão070 habilita
somente o sucessor local após DoR; MVP/piloto continuam sujeitos aos gates finais.
iPhone físico permanece pausado conforme030.

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## IA da integração do modelo — retomada31/10

Coordenação b164d02, backend013aaa6 (sem alteração nesta frente), app9fda8da
(smoke08 documentado; físico iPhone pausado). Checkouts limpos. GraphifyCURRENT
confirmou createInboxProcessor/decideDeterministically/loadConfig; fontes lidos.
Escolha de reuso recebida; nenhuma chave encontrada ou copiada implicitamente.

Escopo: somente src/whatsapp/{agent,worker}, helpers locais de avaliação,
runbook/plano08 e evidência070. Política determinística continua prioritária;
modelo só classifica texto elegível em categorias fechadas, sem IDs, GPS, nomes
solicitados no cadastro, aceite, tokens, histórico ou ferramentas. Propostas
não concedem comandos de negócio:070 só mapeia classificação a respostas
enumeradas já autorizadas. Nome/consentimento/convite/pareamento/suporte continuam
nos handlers determinísticos. Campos de presença ficam como intenção, até100.

Responses API fixa em https://api.openai.com/v1/responses, gpt-6-luna low,
store:false, schema estrito e600 tokens de saída. Configuração explícita para
ativar; segredo não enumerável e nunca logado. Prazo total de processamento25s
preservado: modelo recebe no máximo20s e apenas o saldo após reservar10s para
comando/ACK e respeitar lease. Uma repetição transitória no mesmo orçamento;
nenhum retry em autenticação/recusa/saída inválida. Timeout/recusa/incompleto/
JSON inválido não emitem comando; mensagens determinísticas funcionam sem modelo.

Plano: política/corpus pelo executor, transporte/config/deadline pelo principal,
testes adversariais+integração fechada+revisão independente. Após configuração
privada, smoke e corpus sintético reais, sem contatos/WhatsApp/envio real. Sem
novo build/backend/n8n se artefatos não mudarem. Registrar limites de stub vsreal;
rollback: modo disabled e reversão dos commits locais, preservando filas.

### Resultado desta retomada

Fonte384ee102:337 testes Node locais e332 na imagem final PASS; corpus139 via
stub PASS,82 propostas simuladas, zero chamadas externas. Startup dos dois modos
e9 cenários HTTP com API/modelo simulados passaram. Revisão corrigiu minimização,
critérios de avaliação e compatibilidade de `HELP` com o produtor: propostas de
orientação usam `CONTINUE`, enquanto AJUDA literal mantém o handler original.
Backend não alterado. Manifest específico:
[VR-WA-070-AGENT-MODEL.json](../evidence/VR-WA-070-AGENT-MODEL.json).

Bloqueio daquele checkpoint: confirmar o destino e disponibilizar privadamente a
chave. Resolvido pelo proprietário na avaliação real abaixo, sem converter
STUB_PASS em prova real. Evidência Spring/PG anterior permanece histórica;
o worker novo recebeu uma execução própria. Sem push, deploy ou mensagem real.


## IA da avaliação real —31/10

Proprietário confirmou .env.local/OPENAI_API_KEY, inseriu a chave diretamente
no Terminal e autorizou validar acesso e continuar os testes reais. O auxiliar
não criou chave na conta; origem/projeto/escopos não são inferidos do valor.
Coordenação f20f173, assets técnicos384ee102; backend013aaa6 e app9fda8da limpos.
Único dirty path inicial era o dashboard regenerado pelo startup.

Escopo inicial: avaliação existente com corpus congelado exclusivamente sintético,
primeiro3 mensagens e depois139 casos/até82 classificações se o smoke passar.
Nenhum contato, conversa real, GPS, n8n/Evolution ou aparelho é acionado.
Modo openai habilitado apenas no processo; serviço padrão permanece disabled.
Relatórios sanitizados privados, com hashes/contadores/tempos, sem chave ou
resposta bruta. Não ampliar permissões nem trocar o modelo para contornar erro.

Falha de conta/configuração interrompe o lote e vira impedimento concreto.
Falha de qualidade demanda diagnóstico/revisão/testes antes de nova versão;
corpus de aceite não será alterado para esconder uma falha. Retorno: encerrar
avaliador, preservar relatório parcial e manter worker disabled. Nenhum push
ou deploy faz parte deste smoke; publicação do laboratório GPS não cobre070.

### Reexecução integrada no backend atual

O preflight exige correspondência exata de HEAD, diretório, labels e JAR; o
runtime de aa3d977 não será reutilizado com o HEAD documental013aaa6. Preparar
novo runtime privado wa070-backend-013aaa6a, banco/volume exclusivos e novas
credenciais sintéticas. API18083 e PostgreSQL51222 em loopback, sem carregar
.env.local no Java ou no smoke determinístico. Nenhum fonte backend muda.

Build Java17, CI canônico sozinho sob guarda, depois API/banco e checks HTTP
legados, seguidos do smoke070 no worker corrente. O ambiente filtrado do smoke
mantém modelo disabled e Evolution falsa: essa prova complementa a avaliação
real do modelo, sem apresentá-las como uma jornada integrada OpenAI/WhatsApp.
Encerrar somente recursos/PIDs confirmados deste runtime e preservar recibos,
volumes e resultados anteriores. Não alterar o runtime antigo para satisfazer
preflight nem transportar seu PASS para a versão atual.

### Resultado final da avaliação real e integração

Fonte f5377d0:139 casos originais,82 classificações reais,54/56 intenções
inequívocas (96,4%),26/26 ambiguidades perigosas e zero comando não autorizado;
16/16 exemplos independentes também passaram. Duas divergências finais e todas
as rodadas reprovadas estão no manifest, incluindo três timeouts sem causa provada.
Node337/337, imagem332/332 e startup2modos passaram na fonte final.

Backend013aaa6:CI1262/1262 (PostgreSQL203 incluído), HTTP básico3/3 e smoke
worker/API/PG12/12 no JARf4431763; modelo disabled/provider falso nessa última
prova. A primeira tentativa CI interrompida por memória foi preservada; o retry
passou sob guarda. Lote resetado, credenciais revogadas, API/DB encerrados.

[Manifest real](../evidence/VR-WA-070-AGENT-MODEL-LIVE.json) e evidência canônica
registram todos os hashes, comandos, tempos, imagem e limites. A revisão de
encerramento vinculou T35/T38 completos aos aceites150/170; nenhum desses gates
recebeu PASS antecipado. Reversão continua pelo modo disabled e commits locais,
sem apagar filas ou migration. Nenhum impedimento de chave/acesso permanece.
