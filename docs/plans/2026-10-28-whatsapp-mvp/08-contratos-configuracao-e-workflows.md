> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Contratos, configuração e workflows a implementar

Especificação de construção de 30/10/2026; complementa [arquitetura](02-arquitetura-e-contratos.md). Nomes abaixo são o contrato pretendido, não endpoints ou arquivos já existentes. O card 040 materializa OpenAPI/DTOs/fixtures; números de migrations só são alocados ao executar o card.

Materialização local de 31/10 em andamento no040: [OpenAPI do backend](../../../vanrides-backend/contracts/whatsapp-mvp/openapi.yaml),
[protocolo e limites](../../../vanrides-backend/docs/whatsapp-runtime.md) e
[CLI de laboratório](../../../vanrides-backend/docs/whatsapp-operator.md). A migration alocada éV26,
com schema separado `wa`. A disponibilidade por operação está explícita no contrato;
descrever uma família futura aqui não significa que seu handler já esteja pronto.
Gates finais e smoke do mesmo SHA ainda precisam constar na evidência040 antes de encerrar o card.

## 1. Propriedade e artefatos

Backend: pacote de contexto WhatsApp dentro do namespace real do projeto, sem reutilizar entidades com `companyId` obrigatório; controllers, application services, repositories e adapters testáveis. Contrato em `contracts/whatsapp-mvp/openapi.yaml` no backend. App: entrada `lib/main_driver_mvp.dart`, módulo `lib/features/driver_mvp/`, bridges Android/iOS e configuração de build dedicada. Esses paths devem ser confirmados no Impact Analysis antes de escrever.

Coordenador: `integrations/whatsapp/{workflows,fixtures,scripts,config}/`, `docs/runbooks/whatsapp-mvp/` e manifests/evidências sem dados reais. Fixtures de payload são sintéticas ou sanitizadas preservando estrutura, nunca cópias de conversas/contatos. Exports n8n não levam credenciais, URLs privadas com tokens ou dados de execução.

## 2. Atores, bootstrap e autorização

| Principal | Pode | Não pode |
|---|---|---|
| `CHANNEL` | Submeter eventos da instância autorizada; status são outra categoria | Executar comandos de usuário ou escolher destinatário livre |
| `UNREGISTERED` | Continuar onboarding, usar convite, pedir ajuda | Tornar-se motorista apenas dizendo “sou motorista”; consultar perfis |
| `PASSENGER` | Perfil/ponto próprios, presença da ocorrência permitida, preferências | Alterar outro passageiro, sequência ou posição do veículo |
| `DRIVER` | Próprias rotinas, convites, aprovações, início/fim | Operar outro motorista ou vincular pessoa sem consentimento/aceite |
| `DEVICE` | Pareamento autorizado, status/run próprio, telemetria e parada | Cadastrar pessoas ou abrir run de outro motorista |
| `CONVERSATION_WORKER` | Comandos derivados de inbox válida e contexto limitado | Forjar `actorId`, aprovar papéis ou chamar jobs administrativos |
| `DELIVERY_WORKER` | Claim/submissão/resultado da outbox elegível | Inventar texto/destinatário fora da mensagem reservada |
| `SYSTEM_JOB` | Materializar ocorrências, lembretes vencidos, expiração e saúde | Personificar mensagem humana, aprovar vínculos ou editar ponto |
| `OPERATOR` | Habilitar motorista de teste, revogar, suporte e manutenção auditada | Ler GPS geral ou executar ações não concedidas ao seu escopo |

A inbox e `wa_identity_alias` podem existir antes de `wa_profile`; criar perfil é uma transação posterior idempotente. O primeiro motorista conhecido é habilitado por comando/CLI de operação restrita com convite de uso único/allowlist, não por endpoint administrativo público nem resposta gerada pelo modelo. Não há tela de empresa.

Aliases PN/LID/JID permanecem opacos. Só mapear quando o payload autenticado comprovar a associação. A identidade se deriva da instância/remetente normalizados. Nunca confiar em telefone digitado, nome, `actorId` do modelo ou grupo para essa decisão. Credenciais por principal/ambiente, com rotação e default deny.

## 3. API e comandos de conversa

Manter os endpoints de [arquitetura §5](02-arquitetura-e-contratos.md); acrescentar:

| Endpoint | Uso |
|---|---|
| `POST /driver-app/v1/pairings/begin` | App aberto diretamente cria pedido com chave pública + desafio PKCE; ainda sem motorista/sessão; rate limit e TTL |
| `GET /driver-app/v1/pairings/{id}/status` | Somente quem prova posse do desafio/chave consulta a aprovação |
| `POST /integrations/whatsapp/v1/jobs/tick` | `SYSTEM_JOB`; tipo fechado, chave por janela, server time; nunca `inboxEventId` fictício |
| `POST /integrations/whatsapp/v1/outbox/{id}/authorize-submit` | Valida lease, TTL, elegibilidade e fim imediatamente antes de submeter; registra tentativa em andamento |
| Operação restrita `driver-bootstrap`, `support`, `revoke-device`, `reset-test-data` | CLI com RBAC, sem administração HTTP; reset recusa produção e dados fora do lote sintético exato. O040 implementa CLI de laboratório; suporte humano e operação de coorte real continuam nas etapas posteriores |

Envelope humano: `commandType`, `inboxEventId`, `expectedVersion`, `payload`; cabeçalhos `Idempotency-Key` e `X-Inbox-Lease`. Contexto e resultado da inbox também exigem lease. A API deriva o ator da mensagem autenticada, revalida instância, proprietário, geração e prazos dentro da transação, inclusive após esperas por locks. Resultado: `commandId`, `outcome`, `entityVersion`, `nextStep`, `responseIds`, `correlationId`. O texto aprovado e destinatário entram na outbox da API quando o respectivo handler estiver implementado; 050 passou a gravar resposta e recibo atomicamente;070 acrescenta respostas informativas e suporte mínimo com a mesma garantia. Não devolver diretório de usuários para o agente.

| Família / comandos | Dados e regra principais |
|---|---|
| `START_ONBOARDING`, `SET_PROFILE_NAME`, `ACCEPT_NOTICE` | Campos mínimos; etapas retomáveis; notice com versão/instante |
| `CLAIM_DRIVER_INVITE` | Segredo digitado validado por hash/TTL/tentativas; habilitação prévia do operador |
| `CREATE_INVITE`, `REVOKE_INVITE`, `REQUEST_LINK`, `APPROVE_LINK`, `CLOSE_LINK` | Dono/vínculo derivados e transações com unicidade |
| `PROPOSE_PICKUP`, `APPROVE_PICKUP` | `sourceMessageId` do pin autenticado; referência textual; coordenadas não passam pelo LLM |
| `ADVANCE_DRIVER_SETUP`, `CONFIGURE_ROUTINE`, `SET_STOP_ORDER`, `SET_ROUTINE_STATE`, `CANCEL_OCCURRENCE` | Rascunho na API a partir da inbox, resumo e confirmação vinculada; valores não retornam pelo agente |
| `SET_ATTENDANCE` | `questionRef` e escolha `GOING/NOT_GOING`; API resolve data/sentido/membro; ambiguidade pede esclarecimento |
| `REQUEST_START`, `REQUEST_RESUME`, `FINISH_RUN`, `GET_SUMMARY`, `GET_DRIVER_LOCATION` | Run/rotina do ator; não aceitar IDs livres fora das opções do contexto |
| `CONFIRM_PAIRING` | Código do app ligado ao pedido e ao motorista da conversa; limite de tentativas |
| `SET_PREFERENCE`, `REQUEST_SUPPORT`, `REQUEST_DATA_DELETION` | Pausa/retomada controlada e lifecycle, sem hard delete improvisado |

HTTP/códigos: `400 INVALID_PAYLOAD`, `401 INVALID_CREDENTIAL`, `403 ACTION_NOT_ALLOWED`, `404 RESOURCE_NOT_ACCESSIBLE`, `409 VERSION_CONFLICT/IDEMPOTENCY_CONFLICT/ACTIVE_RUN_EXISTS`, `410 INTENT_EXPIRED`, `422 AMBIGUOUS_OCCURRENCE/POINT_NOT_APPROVED`, `429 RATE_LIMITED`, `503 DEPENDENCY_UNAVAILABLE`. Erros não incluem token, URI completa, GPS ou stack. Mesma chave/corpo devolve resultado anterior; chave/corpo diferente falha. Documentar TTL e examples sintéticos para cada família; versão do schema e testes produtor/consumidor são obrigatórios.

Jobs têm envelope separado: `jobType` (`MATERIALIZE_OCCURRENCES`, `DUE_REMINDERS`, `EXPIRE_LEASES`, `HEALTH_RECONCILE`, `RETENTION`), `windowKey`, limite de lote e cursor opaco. A API usa seu relógio, autentica o principal e rejeita janela indevida. Retentativas não duplicam ocorrência/lembrete. Ausência do modelo não impede esses jobs.

## 4. Dispositivo, links e coleta

Primeiro pareamento: após instalar, o motorista **abre diretamente o VanRides pelo ícone confiável**, não por custom scheme. O app gera chave no Keychain/Keystore e desafio PKCE, chama `begin` e vincula irrevogavelmente pedido/chave/desafio antes de qualquer ida ao WhatsApp. Mostra um código curto; o motorista envia/confirma esse código na conversa com o bot. A API associa o pedido ao motorista autenticado e autorizado da conversa. O app consulta status e `redeem` exige prova da chave/PKCE originais. Usar primitives/bibliotecas padrão; nenhuma chave privada sai do armazenamento seguro. Código: cinco tentativas, validade inicial cinco minutos, uso único.

Perfil backend040: ES256/P-256 e DPoP com nonce de120s, JTI por300s,
URL pública canônica configurada e `ath` no acesso autenticado. OsPOSTs do aparelho
assinam também `vr_body_sha256`, extensão do protocoloVanRides que vincula o corpo
integral. PKCE éS256. Código de oito caracteres no alfabeto
`BCDFGHJKMNPQRSTVWXYZ`, semL; confirmação exige hash correspondente ao código
realmente presente no evento autenticado, não apenas texto escolhido pelo modelo.
Access de5min e refresh rotativo de30dias com prazo original não deslizante;
respostas com tokens são reapresentáveis por5min sob cifra e vínculo à operação.
Conferir prazos novamente após locks antes de permitir efeitos. Implementação
nativa dessas provas e armazenamento de chaves pertence ao060; os builds do spike
não implementam este protocolo por consequência de existir no backend.

Custom scheme só retorna a um pedido já criado/vinculado pelo app; nunca pode executar `begin`, trocar a chave ou criar o primeiro vínculo a partir de uma intenção recebida no link. Interceptor do retorno não possui a chave original e não resgata sessão; link inválido orienta abrir o app pelo ícone. T39 cobre interceptação **antes** de begin, retorno, tentativa de substituir chave e troca entre dois pedidos. Preview/GET não muda estado. Novo vínculo autorizado revoga o anterior; rollback não o ressuscita. Essa etapa assistida ocorre no onboarding inicial; não é repetida em cada parada ou saída.


Intenção de início: opaca, aleatória, validade inicial cinco minutos e uso único; não contém refresh token, telefone ou coordenada. A ativação exige aparelho já pareado ao dono; preview/crawler não muda estado. Perda de resposta é reconciliada por idempotência/status, não criando novo run. No iPhone gratuito, Associated Domains é capacidade a verificar; a landing HTTPS com botão e scheme é fallback explícito. Instruções de instalação/renovação ficam acessíveis sem um segredo no URL.

Implementação inicial do coletor: interface Dart e serviço Android de localização em foreground, mais Core Location iOS com background mode; ciclo de coleta não pertence a um widget. O spike compara a capacidade comprovada do plugin já fixado com o necessário. Se insuficiente, implementar bridges pequenas Kotlin/Swift dentro do app, sem contratar biblioteca paga por suposição. O serviço deve enviar/enfileirar e expirar autorização mesmo com UI suspensa; timer apenas em Dart não satisfaz o requisito. Não manter o Flutter aberto artificialmente para passar no ensaio.

Android começa o serviço a partir da abertura visível autorizada; manifest/tipo `location` e permissões conforme SDK, notificação persistente e ação parar. iOS solicita permissões gradualmente, indica acompanhamento e testa execução sem debugger. Pedir localização precisa/segundo plano somente quando necessário e explicar o motivo. Não pedir fotos. MIUI: registrar comportamento padrão e, se necessário, guiar ajuste mínimo de bateria/autoinício uma vez; não prometer sobreviver ao force-stop.

Lote de amostras: `sessionId`, `generation`, `batchId`, `deviceTime`, `samples[{sequence,capturedAt,latitude,longitude,accuracyM,speedMps?,headingDeg?}]`. Limite inicial 30 amostras/32 KiB; buffer total 120 amostras ou dois minutos. ACK por sequência com `accepted`, `duplicate`, `rejected:[{sequence,code}]`, `serverTime`, `leaseExpiresAt`, `action:CONTINUE|STOP`; não usar “último sequence” para apagar lacunas não confirmadas.

API valida dono, geração, run, números finitos/faixas, sequência, relógio e aquisição. Amostra futura >10 s, antiga >20 s ou precisão >50 m não alimenta aproximação; medir se valores atendem aos aparelhos. Dado atrasado pode receber recibo técnico, mas não substituir última posição atual. Nova fixação no mesmo lugar com timestamp novo é válida; replay/heartbeat não renova GPS.

Watchdog da API executa a cada cinco segundos, usando relógio do servidor e última aquisição aceita; ao ultrapassar o limite de frescor de 20 s, degrada em até dez segundos, mesmo sem novos samples/heartbeats. É independente do W08/n8n, com claim/lock e atualização idempotente em banco para evitar decisões concorrentes. Emitir aviso de indisponibilidade com rate limit, sem confundir silêncio com conclusão. Testar API ativa com n8n parado e nenhum novo pacote.

Lease de coleta: cinco minutos, renovado por estado autorizado online; transmissão e serviço local param ao expirar. Fim do run revoga autorização, solicita stop e espera ACK separado. Backend não afirma que sensor parou sem evidência. Expiração offline deve ser comprovada no nativo com tela bloqueada; se o SO impedir essa garantia, registrar falha e resolver antes do aceite, sem transformar timer não executado em sucesso. Ao reabrir, consultar API antes de coletar.

## 5. Aproximação e encerramento

Base v1 sem custo de rotas: sequência/pontos/destino aprovados, últimas fixações recentes, distância geográfica, tendência, sentido e histerese. A prova avalia se isso basta no percurso real. Não ativar aviso baseado apenas num círculo quando rua paralela/viaduto tornam a posição ambígua. Se faltar evidência, suprimir e contar oportunidade perdida. Se a cobertura ficar abaixo do critério, a tarefa permanece reprovada; abrir a correção para corredor habitual/provedor de rota com contrato/custo verificados antes de ampliar escopo. Não tornar uma integração Google opcional em pré-requisito silencioso.

Parâmetros iniciais de ensaio: raio de pré-aviso 800 m, três fixações coerentes, precisão ≤50 m e idade ≤20 s; raio de passagem 80 m com progresso e afastamento posterior. Destino final: até 100 m, velocidade baixa e permanência de 90 s, progresso compatível e posições válidas. Esses valores são hipóteses versionadas para calibrar em P01/P10; não garantem 2–5 minutos. Coordenadas sozinhas não distinguem pisos de viaduto; cenário ambíguo exige contexto/rota ou reprovação. Compartilhar ponto agrupa progresso, mas cada destinatário tem sua mensagem.

Passagem pelo ponto não prova embarque. Parada perdida não pode travar para sempre as seguintes: classificar ultrapassagem somente com evidência consistente, registrar estado e continuar. Cancelamento tardio inibe aviso do passageiro, preserva snapshot/ordem. Fim automático não usa ausência de dados; se não houver evidência suficiente, sinaliza pendência. Contingência manual não conta como aprovação de encerramento automático.

Consulta “onde está”: API autoriza vínculo/run ativo e devolve apenas posição recente do veículo da ocorrência, com horário. Resposta usa pin estático da Evolution, nunca live location nativa. Sem GPS atual, informar indisponibilidade. Esse envio pontual passa por W06; o fluxo contínuo de amostras continua direto app→API. Payload de pin na outbox é efêmero: deadline de envio é o menor entre TTL da resposta e `capturedAt + 20 s`. No `authorize-submit`, revalidar autorização/run e aquisição; se envelheceu, substituir pela posição mais recente válida ou responder indisponibilidade. Nunca enviar o pin vencido. Remover coordenadas após submissão/expiração; nunca enviado ao LLM nem salvo em logs/execuções. Retenção do recibo guarda somente tipo/horário/status. Não mostrar pontos de outros passageiros. T43 testa o contrato e sua expiração.

## 6. n8n W01–W08 e modelo

Implementar com nós oficiais básicos (Schedule/HTTP Request/If/Switch/Execute Sub-workflow/Error Trigger); não depender de community node Evolution. API é a fonte de estado. Os IDs, `typeVersion`, modo de execução/queue e versão efetiva são extraídos da instância real no export; não inventar compatibilidade com n8n 2.45.4 apenas por JSON escrito à mão.

| Fluxo | Trigger e limite inicial | Resultado / falha |
|---|---|---|
| W01 conversa | Poll5s; serviço privado faz claim1/lease60s; serial por conversa | Contexto → intenção/determinístico → comando → ACK; lease vencido não confirma tentativa velha |
| W02 motorista | Subfluxo W01, prazo total 45 s | Etapa persistida na API; espera por resposta é estado no banco, não execução aberta |
| W03 passageiro | Subfluxo W01, prazo total 45 s | Pin/referência/aprovação com resposta mínima |
| W04 presença | Schedule 1 min; `SYSTEM_JOB`, lote 100/cursor | Materializa próximos sete dias e solicita lembretes devidos; pausa/restart retoma sem duplicar |
| W05 início | Subfluxo W01, HTTP 10 s | Link curto e instrução; confirmação de GPS chega por outbox da API |
| W06 envio | Poll2s; serviço privado claim1/lease60s; provider até10s | Autorizar submissão → Evolution → resultado; ambiguidade vira UNKNOWN |
| W07 suporte | Pedido humano ou Error Trigger com metadados sanitizados | Caso deduplicado e pausa por conversa; canal de atendimento só ao operador autorizado |
| W08 operação | Schedule 1 min; retenção diária via job dedicado | Saúde/expiração/filas; credencial própria; alerta somente em mudança relevante |

Implementação070/RD29: n8n executa nós HTTP nativos contra o serviço Node privado,
com entrada vazia e resposta fechada de contadores. O serviço processa inbox e
delivery com credenciais próprias separadas, sem banco/fila adicionais. Texto,
JID/GPS e tokens de lease/tentativa não entram no runData n8n. `save*none` não
elimina persistência transitória em erro de queue. O adapter050 mantém limite
32KiB/prazo total/sem retries; timeout do cliente n8n não repete uma submissão.
No checkpoint070, W02–W05 ficaram inativos e fechados.080 implementa W02 como
controle do worker comum; W03–W05 permanecem fechados até seus cards. W08 health
não anuncia os jobs150 como implementados.

Uma execução por conversa é garantida pelo lock/lease da API, não apenas por concorrência configurada no n8n. Começar com até duas chamadas LLM concorrentes e envio serial por instância; confirmar capacidade e rate limit do canal no teste real. Não alterar a configuração global de todos os workflows para impor esses limites.

Modelo inicial de produto: `gpt-6-luna`, Responses API, raciocínio `low`, Structured Outputs com JSON Schema estrito. É classificação/proposta de intenção, não autoridade nem loop autônomo irrestrito. A documentação atual suporta esse contrato; disponibilidade/permissão/crédito na conta devem ser testados. [Modelo](https://developers.openai.com/api/docs/models/gpt-6-luna), [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs).

A chamada ao modelo ocorre no serviço privado; n8n usa HTTP Request somente para controle, conforme RD29. Reuso e destino confirmados em31/10; chave configurada privadamente pelo proprietário, acesso e corpus real aprovados na fonte f5377d0 conforme RD31. Uma classificação por mensagem, contexto máximo inicial 4 mil tokens, saída até 600, timeout nominal20s e no máximo uma repetição por erro transitório dentro do lease. Respostas incompletas/recusadas/inválidas não executam comando. Contexto mínimo pseudônimo, sem GPS, lista de contatos, chave ou histórico integral; `store:false` não equivale a promessa de retenção zero no provedor.

VOU/NÃO VOU/AJUDA e etapas inequívocas funcionam deterministicamente quando o modelo falha; mensagens ambíguas pedem esclarecimento. Texto livre é dado não confiável. O payload final usa referências permitidas da API; coordenadas vêm do pin. Áudio, imagem e localização ao vivo recebem orientação curta para texto/pin estático; nenhuma transcrição/download automático no MVP. Testar corpus sintético de no mínimo 60 exemplos incluindo negação, amanhã/volta, correção e tentativa de trocar ator; exigir 100% de bloqueio de ação sem autorização e registrar qualidade das intenções em avaliação congelada.

Configuração da chave existente ocorre por entrada privada no destino confirmado (cofre/picker se futura criação), com smoke real só depois. Testes de contrato usam stub antes disso. Não reutilizar por descoberta uma chave de outro workflow.

## 7. Entrega, retries e configuração

ACK de webhook somente após persistência. Chave de mensagem recebida separada da chave dos callbacks; eco, grupos, eventos de conexão e alterações não viram novo comando. Testar contrato real da Evolution com texto, pin, reply, PN/LID, duplicado, edição, status, erro e desconexão. Não inventar assinatura HMAC inexistente: usar autenticação/rede suportada; se não for adequada, preparar adapter privado.

Outbox tem chaves por finalidade: aproximação `(run, stop, recipient, APPROACH)`; lembrete `(occurrence, recipient, reminderWindow)`; resposta/pin `(commandId, recipient, responsePart)` com commandId derivado da inbox idempotente. Duas consultas novas recebem respostas novas; replay da mesma mensagem não duplica. Versão de política é metadado e não autoriza novo aviso de aproximação. Retry seguro somente com NOT_SUBMITTED/CHANNEL_UNAVAILABLE e prova de zero POST: no máximo três tentativas totais (inicial e duas repetições após 5/15 s), sempre dentro do TTL original e com nova autorização. FAILED/REJECTED e UNKNOWN não permitem retry; a quarta tentativa de 45 s não faz parte do contrato. `UNKNOWN` não é reenviado cegamente: reconciliar por ID/status/capacidade documentada; sem prova, expirar/suporte. Proposta TTL aproximação 60 s; lembrete até início da ocorrência; resposta operacional cinco minutos. Confirmar utilidade sob atraso no destinatário. Após fim/cancelamento não autorizar novas submissões; mensagem já submetida pode chegar depois, por isso leva horário de referência.

Configuração a documentar/testar: `WA_MVP_ENABLED`, `WA_NEW_STARTS_ENABLED`, `WA_APPROACH_SEND_ENABLED`, `WA_TEST_COHORT_ONLY`, URLs/instância permitidas, flags de diagnóstico, TTLs, limites, versão de política, horários e retention. Flags bloqueiam novos efeitos sem impedir finish/revoke/stop ACK/cleanup. Testar liga/desliga com run ativo.

Segredos novos: credenciais CHANNEL/CONVERSATION/DELIVERY/SYSTEM/OPERATOR, Evolution e modelo; chaves/refresh do aparelho no armazenamento seguro. Adicionar suporte `_FILE` explícito ao entrypoint backend, validação fail-closed de valor duplicado/faltante, permissões mínimas e rotação. Preservar a chave de criptografia já compartilhada entre editor/workers n8n; não rotacioná-la incidentalmente. Confirmar sessões Evolution persistentes e procedimento de reconexão por QR pelo proprietário.

Baseline de ensaio restrito: última posição apagada ao encerrar; recibos sem coordenadas sete dias; contexto mínimo sete dias de inatividade; auditoria/presença 90 dias; ponto/perfil enquanto ativo e limpeza até 30 dias após fechamento; backup sete dias com reaplicação de exclusões ao restaurar. Publicar notice coerente antes de dados reais e revisar termos dos fornecedores. Coleta/retention/jobs são testados, não apenas descritos. Logs de URI, telefone e payload no n8n/proxy devem ser minimizados; configuração global compartilhada requer plano próprio.

## 8. Importação, deploy e recuperação

Exportar workflows sem IDs privados desnecessários; manifest mapeia arquivos/hashes, IDs lógicos W01–W08, credenciais por nome lógico, subworkflows e Error Workflow. Importar em ordem, atribuir IDs/credenciais no ambiente e validar referências antes de ativar. [Export/import n8n](https://docs.n8n.io/workflows/export-import/), [queue mode](https://docs.n8n.io/hosting/scaling/queue-mode/).

Testar boot backend com banco vazio e atualização de fixture representativa do legado; migrations aditivas, flags desligadas, sem copiar usuários reais por padrão. Preparar primeiro backup/restauração e manifest, depois publicação específica. O workflow atual de backend pode publicar em push de dev/prod; não usar push como simples backup. Não modificar stacks de n8n/Evolution por conveniência.

Recuperação: congelar novos inícios/avisos, manter endpoints de parada, reconciliar sessões/dispositivos, desativar workflows de envio após drenar/cancelar elegíveis, reverter imagem compatível e não remover colunas em uso. Restore em banco isolado valida dados/constraints e purge antes de retorno. Builds antigos recusados por versão mínima recebem orientação de atualização; revogar artefato não desinstala automaticamente um app.


### Adendo070 — interpretação e avaliação real locais concluídas

RD30 registra a separação entre proposta e autorização. Modo OpenAI é opt-in;
modelo não altera comandos de negócio em070. A reserva10s de comando/ACK limita
os20s nominais ao saldo dos25s de processamento e ao lease; retry compartilha
esse prazo. Pedido completo tem limite conservador3200bytes e resposta32KiB.
Contexto só texto elegível/enums, com filtros conservadores e prioridade das
regras. Não é detecção completa de PII. O corpus139 e os critérios separados do
roteiro09 estão implementados no avaliador. A rodada final real obteve54/56
intenções inequívocas e26/26 ambiguidades perigosas;16 exemplos independentes
também passaram. Rodadas reprovadas e duas divergências finais estão preservadas
no manifest real070. Stub, modelo real e worker/API/PG são provas separadas;
esses resultados não demonstram saldo de crédito, assinatura iPhone ou entrega
WhatsApp. O modo padrão continua disabled, sem ativação remota.

### Adendo080 — rascunho de motorista e confirmação vinculada

Contrato implementado localmente, ainda sujeito aos gates080. Há27 comandos no catálogo
com ADVANCE_DRIVER_SETUP e SET_ROUTINE_STATE. Os oito comandos080 são:

| Comando | Payload do worker |
|---|---|
| ADVANCE_DRIVER_SETUP | `{}` |
| CONFIGURE_ROUTINE / SET_STOP_ORDER / SET_ROUTINE_STATE | `{routineRef, confirmationRef}` |
| CREATE_INVITE | `{confirmationRef}` |
| REVOKE_INVITE | `{inviteRef, confirmationRef}` |
| CANCEL_OCCURRENCE | `{occurrenceRef, confirmationRef}` |
| GET_SUMMARY | `{summaryRef}` — somente rotinas/convites do motorista nesta etapa |

O backend lê valores e escolhas da inbox autenticada e persiste um rascunho por
motorista. Contexto anuncia no máximo um comando080 e opções com referências
opacas e labels neutros; o worker não interpreta pin, dia, horário ou marcador.
Motorista ativo/habilitado com aceite pode configurar antes de parear o celular.

expectedVersion continua sendo a versão do perfil. Avanços incrementam essa
versão; a confirmação também captura revisão/digest e versões dos alvos. O
resumo enviado traz `CONFIRMAR <marcador>` de uso único, válido5min. Só a mensagem
autenticada com o marcador vigente autoriza aplicar o rascunho; `SIM` ou marcador
antigo não escolhem uma proposta nova. Ajuda preserva o rascunho; suporte pausa
e invalida a confirmação. O modelo não recebe os dados dessas etapas.

Convite `PASSAGEIRO <token>` tem família/hash próprios, distintos do bootstrap.
O código é emitido ao motorista para encaminhamento; consumo por passageiro e
eventual link com o número dedicado são090/170. Hash fica no convite e texto
sensível fica cifrado na outbox, com AAD por ambiente/outbox/comando/ator. Só a
reserva autorizada de entrega decifra; TTL/revogação são reavaliados antes de
submeter. Remover cifra ao submeter, cancelar ou expirar; retenção integral150.

Pausa não apaga rotina nem encerra run. Cancelamento explícito persiste uma
ocorrência CANCELED por rotina/data, preservada pela materialização100. Alterações
de configuração/membros devem incrementar a versão da rotina, e100/110 precisam
reler a configuração vigente antes de materializar/enviar/iniciar. Snapshots
iniciados ficam imutáveis. Rotina sem passageiros informa sequência pendente.

### Verificação do contrato 080

A fonte `aec364e` passou na matriz de PostgreSQL e nas jornadas 070 e 080.
W02 chama o worker com controle privado. A importação/execução de laboratório
do n8n fica separada da ativação dos workflows da VPS. O 060 usa o código de
oito letras do pareamento 040 e o 090 consome a família de convites de
passageiro. [Manifest e limites de integração](../../../program/evidence/VR-WA-080-DRIVER-ROUTINES.md).


### Incremento120: recuperação e W08

W08 passa do healthcheck técnico para POST privado `/v1/reconcile-delivery`,
corpo vazio, execução a cada60s e sem retry do nó. O worker chama
HEALTH_RECONCILE com SYSTEM_JOB e conserva seus cursores; n8n recebe apenas
status e contagem0..200. O limite é duas páginas de100 itens em18s totais.
Conexão fechada impede submissão, mas não deve impedir reconciliação/expiração.
Retenção geral segue no150 e watchdog GPS continua na API independentemente
de W08. Exports ficam inativos até o ensaio do ambiente.
