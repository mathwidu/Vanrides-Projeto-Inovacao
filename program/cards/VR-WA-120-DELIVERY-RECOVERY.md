# VR-WA-120-DELIVERY-RECOVERY — Concluir entrega, reconciliação e expiração de mensagens

## Resultado esperado

Avisos resistem a duplicação/restart sem tratar envio incerto como entrega confirmada.

## Estado

`waiting_human`. Pacote P09, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

Em 01/11/2026, o proprietário informou que precisará disponibilizar outro número
para o bot e não o possui agora. Pendência: linha sob seu controle, registro e
pareamento da conta dedicada, envio autorizado e callback real correlacionado.
Implementação, CI e prontidão/limpeza da VPS têm evidência própria; esta espera
não conclui o card nem confirma o motivo da indisponibilidade da linha anterior.
Ver [RD44](../../docs/plans/2026-10-28-whatsapp-mvp/10-registro-de-decisoes.md#rd44--continuidade-sem-número-do-bot-2026-11-01).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`.

O contrato consumidor da CI acompanha a mesma revisão backend em três arquivos
delimitados: `.github/ci/backend-contracts/manifest.json`,
`.github/ci/backend-contracts/openapi.yaml` e
`.github/ci/backend-contracts/fixtures/catalog.json`. O primeiro registra a
origem e os outros dois são cópias byte a byte dos blobs do commit produtor.
Não altera permissões, triggers ou destinos dos workflows GitHub. Verificar
hashes, fronteiras da CI e contratos Node após atualizar o pin.

O registro da autorização ampliada do proprietário permite também atualizar
exatamente `program/decisions/ADR-008-remote-validation-and-pilot.md` neste card.
O adendo não dispensa gates nem altera por si só infraestrutura ou permissões.

Para executar a suíte do filtro descartável na CI, o escopo inclui
`.github/workflows/program-ci.yml`, `scripts/prepare_wa_validation_snapshot.py`
e seu teste `scripts/prepare_wa_validation_snapshot_test.py`. A mudança adiciona
apenas os testes e fontes públicos de `src/whatsapp/lab/live-ingress/`
e `src/whatsapp/lab/live-runtime/` à validação/projeção existentes;
não altera triggers, permissões, credenciais, destinos ou condições de publicação. Configuração privada não integra o Git.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-050-WHATSAPP-TRANSPORT](VR-WA-050-WHATSAPP-TRANSPORT.md), [VR-WA-110-RUN-TRACKING](VR-WA-110-RUN-TRACKING.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependências050/110 concluídas; backend1d5109b e coordenação2a35796 limpos. CI remota disponível para a implementação; número dedicado solicitado antes da prova real, que permanece critério de fechamento.
- [x] Gates por módulo, testes PostgreSQL/contrato, injeção de falhas e prova real separados na sequência abaixo; nenhum compilador pesado no Mac.

## Plano sequencial

1. Implementar authorize-submit transacional coordenado com fim/cancelamento e TTL.
2. Concluir W06 backoff, lease e ACK por tentativa; UNKNOWN exige reconciliação documentada.
3. Processar status fora de ordem sem regredir; conexão caída suspende elegíveis e cancela vencidos.
4. Exercitar falhas antes/depois da chamada Evolution e late delivery com timestamp no texto.

## Critérios de aceite

- [ ] Aproximação única por run/parada/destinatário/finalidade; resposta/pin por comando/destinatário/parte; lembrete por ocorrência/pessoa/janela, sem duplicação por mudança de política.
- [ ] Sem novas submissões depois de fim/TTL; tentativas já em andamento explicitadas.
- [ ] HTTP 200/UNKNOWN não contam como delivered; callback real comprovado.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gate backend, fault injection/W06 e canal teste; T07/T17/T20/T23–T25/T38.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-120-DELIVERY-RECOVERY.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Desativar novas submissões, preservar resultados/tentativas, reconciliar UNKNOWN e reverter versão sem reabrir outbox terminal.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Retomada e análise confirmada — 2026-11-01

Inventário somente leitura em backend `1d5109b10a8e85ef14ba7ccf8e7091ebf94d10b5`
e coordenação `2a35796516954bd910c41461f461fd51e1f7392f`. A base existente
já possui unicidade por finalidade, lease/token/geração, autorização transacional,
resultado idempotente, callbacks monotônicos e UNKNOWN sem reenvio. Faltam
bloqueio pelo estado da conexão, retentativa explicitamente segura, progresso
operacional de reconciliação e limites/prioridades. Não substituir essa base.

Primeira fatia delimitada: projeção durável da conexão por ambiente/instância,
alimentada somente por evento CONNECTION autenticado, e consulta sob o mesmo
lock de entrega antes de claim/authorize. Sem estado conhecido OPEN, suspender
novas reservas; expirar pendências antes de liberar backlog. Evento antigo não
reabre conexão fechada; empate deve favorecer suspensão. Resultados/callbacks
históricos continuam registráveis com canal fechado. Nenhuma retentativa ou
mudança do adapter entra nessa primeira fatia. Contrato de claim/authorize
mantém formato e erros existentes; desconhecido/fechado não é entrega falha.

Paths backend restritos inicialmente a `whatsapp/transport/`, migration própria,
seus testes e fixtures de integração, contrato/doc do transporte e QA Log.
A matriz obrigatória `scripts/qa_integration_profile.sh` e regressões do gate
podem acompanhar novas classes/contagens, sem reduzir cobertura ou ratchets.
Coordenação é responsável por contrato consumidor, worker/adapter/W06, roteiro
e recibos; backend é responsável por autorização, persistência e testes PostgreSQL.
App não faz parte deste card; iPhone físico permanece pausado.

### Decisões de implementação e sequência executável

1. Implementar conexão sob lock, ordem dos eventos e bloqueio de reserva;
   testar desconhecido/fechado, abertura atrasada, desconexão entre claim e
   authorize, reconexão com TTL vencido, isolamento de ambiente/instância e
   resultado/callback depois da desconexão. Fixtures existentes devem declarar
   a conexão explicitamente; não criar bypass por ambiente de teste.
2. Introduzir resultado explícito `NOT_SUBMITTED` somente quando o adapter
   comprovar que não invocou POST. `FAILED/REJECTED` continua definitivo e
   `UNKNOWN` continua sem retry. Qualquer timeout/erro após invocar POST é
   incerto. Probe assíncrono expirado não pode posteriormente iniciar envio.
   Revisar o contrato compartilhado antes de implementar esta segunda fatia.
3. Limitar a três tentativas totais: inicial mais duas repetições após 5 s e
   15 s, sempre com nova autorização e dentro do TTL. A menção anterior a 45 s
   não permite uma quarta tentativa; corrigir o plano/contratos quando essa
   política entrar. Preservar payload privado apenas enquanto não submetido,
   ainda elegível e não terminal; expirar/cancelar sem reabrir estado terminal.
4. Completar reconciliação operacional de UNKNOWN, callbacks tardios e
   visibilidade/expiração/suporte, sem alterar recibos idempotentes para inventar
   entrega. Prova por ID externo é distinta de inferência por texto/telefone.
5. Prioridades/quota por instância e destinatário, unicidade por finalidade e
   fencing de fim/TTL. A geração de aproximações fica no130; 120 prepara os
   contratos necessários sem produzir alertas geográficos fictícios.
6. Gates oficiais no GitHub, smoke Spring/PostgreSQL/worker com falhas controladas
   no mesmo artefato e W06 n8n. Publicar candidato concreto no laboratório
   autorizado somente após revisão de impacto, pins e retorno. Conferir serviços
   compartilhados antes/depois; não usar a A8 como prova de outra versão.
7. Com número dedicado vinculado, executar envio e callback real no escopo
   autorizado, comprovando submissão, entrega e eventual leitura separadamente.
   Sem esse resultado, o120 não recebe done. O ambiente completo de piloto170
   e o fluxo completo180 continuam separados.

### Estado observado de infraestrutura e recurso pendente

Em 2026-11-01, consulta somente leitura confirmou Evolution 2.3.7 (1/1) e os
quatro serviços n8n 2.45.4 (1/1). Consulta interna autenticada da Evolution
retornou duas instâncias, zero conectadas e nenhuma nomeada VanRides. Nenhum
telefone, apikey ou perfil foi exportado; nenhuma instância/webhook foi alterada
e nenhuma mensagem enviada. Foi solicitada ao proprietário a disponibilidade
do número dedicado para futura vinculação por QR, sem pedir valores no chat.
Isso não bloqueia implementação e testes sintéticos; bloqueia o aceite de
callback real até sua execução. Não reaproveitar instância alheia automaticamente.

### Verificação e retorno por fatia

Backend: gate canônico completo remoto com matriz PostgreSQL exata, testes
concorrentes nas duas ordens e sem pulos. Coordenação: testes Node do adapter,
worker, contratos e workflows, além de governança 59+11. Testes de transporte
devem provar zero POST para NOT_SUBMITTED e uma única invocação para UNKNOWN,
incluindo conclusão tardia após timeout. Revisão independente das mudanças com
risco de duplicação/autorização. Não repetir builds iOS para alterar transporte.

Retorno: suspender novas submissões, preservar resultado de tentativas em voo,
manter reconciliação e parada disponíveis; reverter fonte/imagem compatível
sem apagar schema ou reabrir outbox terminal. Migração adicional deve permitir
rollback de aplicação com coluna/tabela preservada. Segredos, números, conteúdo
de conversa e GPS ficam fora de evidência versionada.


Fonte externa conferida: a consulta `connectionState` da
[revisão oficial Evolution2.3.7](https://github.com/evolution-foundation/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/controllers/instance.controller.ts)
retorna o estado em memória; não deve ser substituída pela operação `connect`,
que pode iniciar uma conexão. A [entrega de webhooks](https://github.com/evolution-foundation/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/integrations/event/webhook/webhook.controller.ts)
tem suas próprias repetições; isso reforça a deduplicação de eventos, não
constitui autorização para repetir o POST de mensagem doVanRides. As páginas
antigas da referência HTTP retornaram404 nesta consulta; usar o fonte fixado
e verificar o comportamento na versão efetivamente instalada.


DoR conferida pelo controlador em 2026-11-01, estado ready; transição posterior
para in_progress com WIP 1. Revisão especializada aprovou o contrato fechado
`{attemptToken, outcome: NOT_SUBMITTED, failureCode: CHANNEL_UNAVAILABLE}` para
situação comprovada antes da invocação do POST. Confirmou limite de três totais e
necessidade de cancelar definitivamente uma continuação assíncrona expirada.
A observação atual do canal no worker complementará os eventos persistidos;
um callback antigo de conexão não será suficiente para reabrir despachos.


Atualização do recurso humano: o proprietário indicou o número do Redmi,
condicionado à disponibilidade. Leitura mínima encontrou WhatsAppBusiness
instalado e conta desse tipo no Android; sem extrair número e sem provar sessão
ativa. Preparar instância exclusiva e confirmar acesso no pareamento; manter
contatos do ensaio distintos do número do bot e definidos privadamente.


Detalhamento revisado de UNKNOWN: expirar outbox no TTL sem alterar o significado
da tentativa incerta; purgar payload e criar caso operacional deduplicado,
separado de suporte conversacional. HEALTH_RECONCILE usa SYSTEM_JOB e não
pode depender da conexão ou do claim. CLI privada deve listar os casos com
metadados/escopo, sem conteúdo pessoal. Resultado tardio de reserva abandonada
e callback correlacionado preservam outbox terminal; replay de um result UNKNOWN
já confirmado continua imutável. Sem ID externo correlacionável, expiração e
suporte são o destino da primeira entrega, sem recuperação inventada.
