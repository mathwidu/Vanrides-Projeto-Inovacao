> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Pacotes de implementação e sequência

Parte da [proposta do MVP](README.md). **P00–P13 agrupam os 20 cards já registrados no programa**, conforme [sequência executável](07-plano-executavel.md). Antes de cada fatia, conferir o card, Impact Analysis, dependências, dirty paths, dono, gates e rollback. Manter um card vertical ativo, com commits separados em cada repositório afetado.

## 1. Caminho de entrega

| Pacote | Resultado verificável | Dependências | Repositório / sistema afetado |
|---|---|---|---|
| P00 | Escolhas e acessos necessários definidos | Revisão deste plano | Coordenador; contas/serviços somente com autorização específica |
| P01 | Fonte automática de posição e experiência comprovadas em Android e iPhone | P00, assinatura e aparelhos dos dois sistemas | Spike controlado em app/backend com coleta própria |
| P02 | Modelo e autorização do MVP funcionam sem empresa | P01 aprovado; D02/D03/D07 | Backend + contratos no coordenador |
| P03 | Mensagem vira comando autorizado e resposta durável | P02 | Backend + workflows versionados |
| P04 | Motorista configura sua rotina no WhatsApp | P03 | Backend + n8n |
| P05 | Instalação, pareamento e ativação prontos para uso | P02/P04 e evidência P01 | App + backend + distribuição |
| P06 | Passageiro se cadastra e tem ponto/vínculo aprovados | P03/P04 | Backend + n8n |
| P07 | Presença diária correta e snapshot preparado | P04/P06 | Backend + n8n |
| P08 | Saída e posição contínua com recuperação | P05/P07 | Backend + coletor Android/iOS |
| P09 | Entrega WhatsApp resiste a falha/duplicação | Base P03; eventos P07/P08 | Backend + n8n + Evolution |
| P10 | Aproximação individual útil e validada | P08 e base técnica P09 pinada; aceite real do canal obrigatório em P13 (RD44) | Backend + regras/rotas + mensagens |
| P11 | Conclusão e exceções fecham o ciclo | P10 | Backend + n8n + app |
| P12 | Operação, dados, custos e suporte preparados | Desenho iniciado em P02; integrado até P11 | Três repositórios + runbooks |
| P13 | Preparação e entrega integrada de teste; piloto ampliado é posterior | P00–P12 e gates para preparar; promoção externa depende de autorização específica | Manifest de versões e ambiente escolhido |

P01 é a redução de risco mais importante. Não construir todo o cadastro para só depois descobrir se o rastreamento atende ao requisito. P03 implementa a base de inbox/outbox suficiente para fluxos controlados; P09 completa entrega/reconciliação antes de qualquer piloto com usuários. Privacidade e autorização começam em P02, mesmo que a revisão operacional completa seja P12.

## P00 — Fechar o recorte e preparar os pré-requisitos

**Tarefas**

1. Registrar D01–D10 como aceitas, ajustadas ou pendentes, com responsável. Cobertura Android+iPhone e permanência dos pendentes já foram confirmadas. Confirmar aparelhos, sistemas e canal WhatsApp usado na VPS; navegação externa está fora do MVP.
2. Fechar número de participantes, dias/sentidos, pontos habituais, sequência, regra de pendentes e antecedência desejada; definir como será observado o embarque no ensaio sem pedir ação do motorista por parada.
3. Inventariar versões/configurações relevantes de n8n/Evolution e recursos da VPS em leitura: canal, webhook, autenticação, persistência, logs, capacidade e separação de ambiente. Não exibir segredos.
4. Preparar APK direto e instalação iOS Personal Team do proprietário, package/bundle estáveis, assinaturas e política de dados. Não exigir contas Play/TestFlight para esta primeira entrega. Google Cloud/billing/verificação Navigation Connect deixam de ser bloqueio da coleta própria; avaliar conta de mapas apenas se houver serviço de rotas/geocodificação contratado.
5. Preparar instalação direta, App Links Android e verificar capabilities de Universal Links na conta gratuita. Preparar fallback iOS HTTPS com botão/scheme, URL de política/instalação e retorno ao WhatsApp. Se precisar criar DNS/subdomínio, o proprietário executa essa parte.
6. Registrar requisitos do canal para mensagens proativas, status e pin estático; verificar documentos da conexão efetiva. Não presumir equivalência entre Baileys e Cloud API.
7. Reconciliar o estado do programa e os dirty paths preexistentes antes do primeiro card de código. Não apagar alterações para obter um check verde.

**Entrega:** registro de decisões, inventário sanitizado, matriz de compatibilidade, responsáveis e plano de acesso/ambiente aprovado. Falta de assinatura, aparelho ou ambiente de teste aparece como bloqueio real do ensaio físico.

**Aceite:** sabemos quais aparelhos Android/iPhone e canal testaremos, qual fluxo será considerado simples e quais recursos podem ser preparados. Acesso à conta Apple, assinatura e capacidade de distribuir são verificados, não inferidos do Xcode. Ter documentos não equivale a build assinado, GPS validado ou VPS alterada. O [registro de prontidão](06-fechamento-e-prontidao.md) identifica as pendências atuais.

## P01 — Provar o rastreamento antes do produto completo

**Tarefas**

1. Criar fatia técnica mínima e isolada nas duas plataformas: link de teste → app identificado → coletor nativo → API autenticada. Usar participantes autorizados e dados de teste sempre que possível. Compartilhar contrato de telemetria; separar ciclo de vida Android/iOS.
2. Medir abertura a frio, permissões, primeira posição recebida, cadência, idade da amostra, bateria e retorno ao WhatsApp. Investigar suporte do Geolocator fixado antes de escolher atualização/adaptação; mudanças de biblioteca exigem justificativa.
3. Percorrer rota representativa com pontos intermediários e nenhuma intervenção por parada, em Android e iPhone; executar com a coleta VanRides, sem exigir aplicativos externos de navegação. Acompanhar com observador, sem pedir manuseio durante condução.
4. Testar segundo plano, tela bloqueada, perda de rede, retomada, encerramento forçado/reboot, permissão negada/revogada, localização aproximada e economia de bateria. Separar segundo plano de encerramento forçado; falha precisa ser detectável.
5. Provar que sair da tela Flutter ou fechar o navegador opcional não cancela a coleta. Provar que parar pelo app ou pela regra final interrompe o coletor sem exigir ação por parada.
6. Calibrar alvo de envio de 5–10 s, precisão, uso de dados e consumo de bateria; simular avisos sem enviar a passageiros reais. Medir falsos positivos e atraso em ruas próximas.
7. Validar destino final, chegada real, suspensão e interrupção. Registrar se encerramento automático tem evidência suficiente.
8. Simular ACK perdido depois da gravação, fila offline, relógio incorreto, credencial expirada e finalização sem rede. Provar idempotência, descarte de amostras vencidas e limite local da coleta; registrar separadamente pedido e confirmação de parada.
9. Registrar resultado, limitações, custos previstos e material reaproveitável; descartar atalhos/credenciais do spike antes de produção.

**Entrega:** relatório de prova física com versões, tempos e resultado por cenário, sem publicar trajetos pessoais. Artefato técnico mínimo não é o app pronto.

**Aceite:** critérios M1 do [plano de validação](04-validacao-e-operacao.md) atendidos em ambas as plataformas; resultados separados impedem que Android compense uma falha no iPhone. Se falhar, registrar no-go desta solução e apresentar decisão ao proprietário; não avançar fingindo que o requisito foi simplificado. Se a coleta própria não atender, avaliar correção ou outro provedor, inclusive Navigation Connect com ensaio próprio. Não construir dois rastreadores em paralelo.

**Rollback:** interromper coletores/sessões de teste, confirmar parada nos aparelhos, revogar credenciais temporárias e manter relatório sanitizado. Qualquer recurso externo segue a autorização específica. Não alterar produto legado.

## P02 — Fundar o contexto WhatsApp no backend

**Tarefas**

1. Especificar OpenAPI e estados; definir prefixos/filtros explícitos, papéis e isolamento por motorista.
2. Criar migrations aditivas das entidades do [contrato](02-arquitetura-e-contratos.md), começando pelo necessário à primeira fatia. Constraints acompanham os casos de uso, sem tabelas especulativas.
3. Implementar perfil/aliases, vínculo, rotina/ponto versionados, presença, run/snapshot e políticas; nenhuma dependência obrigatória de empresa/email/senha.
4. Implementar identidade de integração, idempotência, versão otimista, correlação e auditoria mínima. Testar acesso cruzado e endpoints novos sem autenticação.
5. Criar fronteiras pequenas para mensageria e localização, sem espalhar payloads externos pelo domínio.
6. Preparar feature flag desabilitada, configurações por ambiente e migração compatível com o legado.

**Entrega:** primeira fatia de domínio exercitada por API com PostgreSQL, contratos e testes. Ampliações acompanham P04–P11.

**Aceite:** usuário do MVP existe sem empresa fictícia, um ator não alcança dados de outro e endpoints legados continuam compatíveis. Unicidade concorrente é comprovada em banco real.

**Rollback:** desativar flag/entrada nova e voltar versão compatível; manter tabelas aditivas para não perder dados. Migration reversa destrutiva não é o rollback padrão.

## P03 — Integrar conversa e comandos duráveis

**Tarefas**

1. Implementar adaptador de webhook Evolution com autenticação observada, limite de tamanho/taxa, normalização e inbox persistida antes do ACK.
2. Cobrir texto e localização estática; responder de modo útil a áudio/mídia não suportados. Transcrição de áudio fica fora do primeiro corte, salvo decisão explícita.
3. Implementar deduplicação, ordenação por conversa, lease e retomada após reinício; ignorar eco, grupos e eventos irrelevantes.
4. Criar W01 e subworkflows base: contexto mínimo → intenção estruturada → comando permitido → resposta/outbox. Limitar chamadas, tempo, custo e retries do modelo.
5. Implementar estado de diálogo durável, perguntas válidas por data/sentido, retomada e correção; resposta de sucesso só após commit da API.
6. Criar base de outbox/envio em ambiente controlado. Segregar credenciais e evitar dados brutos em execuções/logs.
7. Versionar exports sanitizados e instruções de importar/desabilitar. Ajustar retenção apenas destes workflows.

**Entrega:** conversa de teste persistente que executa um comando autorizado, sobrevive a reinício e não repete ação por webhook duplicado.

**Aceite:** “sim” ambíguo não muda presença; modelo não consegue atribuir a si outro ator; API indisponível não produz confirmação falsa. Contrato de pin testado na versão real do canal.

## P04 — Cadastro do motorista e rotinas

**Tarefas**

1. Implementar F01 e W02: identificação, nome, explicação/aceite, resumo, correção e retomada.
2. Configurar rotinas separadas de ida/volta: dias, horário, timezone, destino e sequência habitual.
3. Implementar criação/revogação de convite e aprovação de vínculo/ponto pelo motorista.
4. Criar estado de preparo distinguindo cadastro, pareamento, conexão e prova do piloto.
5. Impedir início sem destino/ordem/pontos aprovados e sem políticas necessárias definidas; responder com a pendência concreta.
6. Criar consulta de resumo e edição futura de rotina sem alterar run ativo.

**Aceite:** motorista prepara uma rotina em WhatsApp sem empresa ou formulário de viagem; retoma cadastro interrompido; ida e volta não se confundem; estado “pronto” não é antecipado.

## P05 — App mínimo, pareamento e instalação

**Tarefas**

1. Definir flavor/entrypoint que abre diretamente a ação do WhatsApp e preserva a jornada Flutter legada.
2. Preparar Android package ID e iOS bundle ID estáveis, assinatura/provisionamento, configuração por ambiente e instalação APK/Xcode Personal Team. Resolver signing no Mac, sem pedir credenciais ao motorista no bot; documentar renovação iOS em sete dias. Validar compatibilidade do Flutter fixado com o Xcode instalado antes de prometer o build iOS.
3. Implementar App Links e Universal Links verificados, incluindo arquivos de associação dos dois sistemas, e página mínima de orientação quando app ausente/incompatível. Não prometer abertura pós-instalação automática.
4. Implementar desafio de pareamento, confirmação inicial, armazenamento seguro, renovação, revogação e troca de aparelho.
5. Implementar ciclo de vida nativo: foreground service de localização Android e Core Location em segundo plano iOS. Separar serviço de widgets, proteger estado/credenciais e oferecer parada local visível. Incorporar o resultado aprovado de P01.
6. Implementar estados preparando, aguardando conexão, erro com ação recuperável e retomada; remover dependência de `companyId` nessa jornada.
7. Criar teste guiado com início e fim de coleta, ACK da API e confirmação de parada. Uma permissão concedida ou coordenada obtida só no aparelho não comprova transmissão.
8. Validar atualização do app com pareamento existente e links antigos expirados; contar toques e mensagens necessárias.

**Aceite:** instalação limpa e segundo uso funcionam nos aparelhos Android e iPhone do piloto; link não consumido por preview; aparelho errado não ativa sessão; permissões de localização e indicadores correspondem ao uso real, sem pedidos legados de fotos ou privilégios desnecessários. Builds assinados e distribuídos são os mesmos do manifest do ensaio.

**Rollback:** interromper distribuição da versão problemática, invalidar links afetados e manter atualização/recuperação para aparelhos já instalados. Não presumir downgrade ou renovação automáticos do APK/build iOS; revogar build não desinstala o app nem interrompe automaticamente sessões em andamento.

## P06 — Passageiro, ponto e vínculo

**Tarefas**

1. Implementar F03/F04 e W03: convite, identidade do motorista, nome, dias/sentidos e preferência de mensagens.
2. Receber pin estático, confirmar ponto de embarque e referência. Se houver endereço/link, resolver apenas formatos seguros; pedir pin quando não houver resultado confiável.
3. Geocodificação opcional: apresentar candidato e exigir confirmação, sem transformar endereço em verdade automática.
4. Encaminhar aprovação ao motorista e informar resultado ao passageiro. Sem aprovação, não divulgar posição ou lista de atendidos.
5. Implementar correção/versionamento, recusa com orientação, encerramento de vínculo e proteção contra convites expirados/revogados.
6. Agrupar ponto compartilhado para progresso, mantendo presença e avisos individuais.

**Aceite:** cadastro completo pelo passageiro sem aplicativo, senha ou endereço postal obrigatório; ponto e vínculo aprovados; outro passageiro não vê dados pessoais; alteração posterior não muda snapshot ativo.

## P07 — Presenças, calendário e corte da lista

**Tarefas**

1. Materializar ocorrências elegíveis por calendário/timezone, sem obrigar motorista a cadastrar viagem.
2. Implementar W04 com janelas e limite de lembretes; exclusão de quem não recebe avisos e de ocorrência cancelada.
3. Aplicar VOU/NÃO VOU/pendente por data e sentido; salvar autor/instante/versão e resposta contextualizada.
4. Implementar política D03 explícita e resumo de confirmados, ausentes e pendentes antes de iniciar.
5. Implementar corrida presença × início com snapshot transacional; não deixar passageiro confirmar uma lista e iniciar outra silenciosamente.
6. Tratar alteração tardia, feriado/exceção/cancelamento do dia, mudança de timezone/configuração e retomada após agendamento perdido.

**Aceite:** ida/volta/dias não vazam entre si; lembrete não duplica; política de ausência de resposta é a combinada; usuário recebe resultado fiel quando responde após corte.

## P08 — Início, sessão de coleta e ingestão

**Tarefas**

1. Implementar intenção de início da conversa e ativação autenticada no app; transação congela lista/pontos/ordem/política.
2. Bloquear dois runs ativos do mesmo motorista, inclusive por cliques concorrentes em aparelhos/links diferentes.
3. Criar sessão de coleta por run/aparelho/generation, autorização com prazo e renovação limitada; recuperar a mesma sessão ativa quando permitido, sem novo run por retry.
4. Implementar envio HTTPS em lotes pequenos, ACK durável, deduplicação de sequência e buffer offline limitado; proteger credenciais e não depender de WebSocket ou push silencioso para funcionamento normal.
5. Normalizar estado/tempos/coordenadas, rejeitar sessão desconhecida, amostra futura/atrasada e evento terminal fora de ordem.
6. Calibrar atualidade para cadência observada, precisão e incerteza de relógio; confirmar acompanhamento somente com coletor ativo e posição recente recebida.
7. Implementar estados degradado/recuperação, expiração de intenção e sessão sucessora autorizada; descartar dados da geração anterior.

**Aceite:** fluxo WhatsApp → app/coletor → API observado nos mesmos builds Android/iOS; GPS não passa pelo LLM; interrupção não produz posição “atual” falsa; reenvio não cria operação dupla.

## P09 — Entrega de mensagens e recuperação

**Tarefas**

1. Completar outbox transacional, lease, tentativas, backoff, chave única e validade por finalidade.
2. Completar W06 e callback de status; distinguir submissão, entrega, leitura quando disponível e resultado desconhecido.
3. Revalidar antes de enviar: run ativo, ponto futuro, preferência/vínculo/presença elegíveis e TTL.
4. Tratar timeout depois de possível envio, restart entre envio e ACK, lease expirado e ACK antigo sem retry cego.
5. Priorizar aviso de aproximação sobre mensagem não urgente, com limites por destinatário/instância e controle de backlog.
6. Implementar sessão Evolution desconectada, reconexão, falhas definitivas e suporte; descarte de alerta expirado na recuperação.
7. Validar texto como caminho garantido; botões e templates só entram conforme suporte real do canal.

**Aceite:** duplicação de evento não gera duas intenções; cenários de resultado desconhecido ficam visíveis; mensagem vencida não é submetida como aviso atual; estado de entrega não é inventado. Mensagens já aceitas pelo canal podem chegar depois: medir esse atraso e não prometer recolhimento.

## P10 — Aproximação por ponto e informação ao passageiro

**Tarefas**

1. Implementar motor determinístico de progressão/eligibilidade com política versionada e relógio controlado.
2. Aplicar distância, tendência, sequência e corredor/rota quando necessário; calibrar antecedência, histerese e tolerância de GPS.
3. Testar pontos próximos, ruas paralelas, sentidos opostos, desvio, parada longa e pontos compartilhados. Definir comportamento quando salto de GPS deixa progresso incerto.
4. Persistir decisão e outbox na mesma transação; não gerar aviso de ponto passado ou passageiro cancelado.
5. Implementar consulta “onde está?” com autorização e atualidade. Se enviar mapa pontual, rotular última posição e não expor roteiro/endereços de terceiros.
6. Avaliar cliente de rotas existente e ETA individual. Se não houver cálculo validado, manter mensagem sem minutos; necessidade de ETA exato é decisão de escopo, não preenchimento pelo agente.
7. Executar modo de observação: decisões registradas sem mensagens reais, comparadas com chegada observada; depois ensaio com destinatários de teste autorizados.

**Aceite:** cenários T19–T22 e métricas de aproximação do [plano de validação](04-validacao-e-operacao.md) passam no percurso-alvo. M2 completo é avaliado após P11, antes da promoção. Se precisar ação manual a cada parada, não atende ao MVP proposto.

## P11 — Encerramento, contingências e atendimento

**Tarefas**

1. Implementar regra de conclusão com destino final, progressão, amostras recentes, precisão e permanência compatíveis; passagem próxima não conclui automaticamente. Validar caso de poucos passageiros/último ponto coincidente e ausência de parada observável.
2. Implementar “finalizar” idempotente como contingência, com confirmação contextual quando houver inconsistência. Aplicar decisão D06 se o automático não passar.
3. Finalização invalida outbox pendente, bloqueia novos avisos e ignora telemetria tardia. Solicitar parada no ACK de controle e registrar confirmação do app; proteção local limita coleta sem rede. Não prometer que finalizar na API já desligou um aparelho offline.
4. Implementar cancelamento explícito e operação interrompida; falta de GPS/horário excedido gera pendência operacional, não conclusão inventada.
5. Implementar W07, pausa de conversa para humano, troca de número/aparelho, preferência de avisos, correção e saída do serviço.
6. Preparar procedimento de solicitação de dados/exclusão e retomada após falhas; distinguir “parar avisos” de “não vou”.

**Aceite:** ciclo termina sem ação por parada; finalização repetida é segura; suporte não concorre com o bot; nenhuma nova tentativa de envio é autorizada depois do corte de finalização/expiração. Envios já em andamento/submetidos são reconciliados e podem ter entrega posterior pelo canal; essa limitação entra na medição de utilidade e no texto da mensagem.

## P12 — Operação, retenção e orçamento

**Tarefas**

1. Consolidar configuração por ambiente, healthchecks, correlação, painéis mínimos e alertas operacionais acionáveis.
2. Definir e implementar retenção/limpeza por dado, mascaramento de logs/proxy/n8n, backup e restauração verificada.
3. Aplicar limites de custo/consumo para modelo, rotas/geocodificação opcionais, canal, banco e rede; contabilizar licenças mobile se a prova exigir biblioteca paga e número de execuções do n8n. Não contratar Navigation Connect/Pub/Sub sem mudança de arquitetura.
4. Criar runbooks: sem GPS, canal desconectado, outbox atrasada, chave revogada, pareamento comprometido, usuário em suporte e rollback.
5. Verificar capacidade da VPS sem alterar recursos das automações existentes silenciosamente. Queue mode/Redis/workers só se medição justificar.
6. Preparar release manifest por repositório e workflow, rollback testado e feature flags que preservem dados.
7. Definir responsável e janela de suporte no piloto, autorização de envio real, coleta de feedback e critério de pausa.

**Aceite:** falha é detectável e tem resposta definida; restore funciona; evidências não contêm dados pessoais desnecessários; orçamento e retenção aprovados; operar não depende de acessar banco manualmente em cada saída.

## P13 — Entrega completa de teste e preparação do piloto

**Tarefas**

1. Executar ensaio de ponta a ponta com instalação limpa, motorista e passageiros de teste, rotina, presença, partida, avisos e fim.
2. Rodar regressões/gates dos repositórios alterados e smoke do mesmo SHA/artefato; registrar versão de workflow, app/coletor, aparelho, sistema, bibliotecas e política de aviso.
3. Revisar M0–M3, riscos conhecidos, métricas e pendências; nenhuma reprovação crítica pode ser omitida por “ser MVP”.
4. Preparar plano concreto de publicação na VPS e distribuição. Pedir autorização específica apenas quando artefatos, validação e rollback estiverem prontos.
5. Após autorização, publicar pelo processo do projeto e validar o mesmo manifest no ambiente; não reutilizar smoke de outra versão.
6. Conduzir ensaio controlado no ambiente de teste com iPhone e Redmi 8, acompanhamento operacional e critério de pausa. Registrar feedback sobre simplicidade/antecedência; não confundir com promoção de piloto comercial.
7. Decidir continuar, corrigir ou interromper a partir de evidência. Expansão de participantes, versões suportadas ou funcionalidades é decisão posterior; Android e iPhone já fazem parte desta entrega.

**Aceite:** proprietário consegue testar onboarding, presença, localização, avisos e fim nos dois aparelhos; avisos úteis e falhas tratadas sem atualização manual por parada; pacote completo e renovação iOS comprovados. Promoção a piloto/produção permanece separada.

## 2. Artefatos que a implementação deverá deixar

- OpenAPI, migrations e regras versionadas no backend, com testes e QA Log.
- App mínimo com coletor, builds Android/iOS assinados, canais de instalação, links verificados e testes de aparelho/parada/recuperação.
- Workflows exportados sem segredos, subworkflows, prompts/esquemas e manual de importação/rollback.
- Textos de onboarding, erro, aviso e suporte revisados com os estados reais do sistema.
- Configuração externa documentada por referência ao cofre, sem valores secretos.
- Matriz executada de cenários, relatório físico, métricas, orçamento e decisões do piloto.
- Manifest de release e runbooks por repositório/ambiente; commits e gates independentes.

## 3. Cards e estimativa

P00/P01 têm dependências externas e maior incerteza. Depois da prova, quebrar cada pacote em fatias observáveis de poucos dias, estimadas com quem executará. Não assumir que um pacote equivale a um sprint ou a um único commit.

Cada card deve declarar: problema e exemplo; fluxo Fxx; contrato afetado; escopo/fora de escopo; dependências; arquivos/repos; evidência de aceite Txx; testes e smoke; rollback; atualização documental; responsável. Se cruzar repositórios, usar manifest e commits próprios, mantendo o limite global de um card ativo.

O próximo card canônico é `VR-WA-000-PREFLIGHT`; depois vêm ferramentas e prova física. A [sequência de 20 cards](07-plano-executavel.md) já faz a decomposição. Ferramentas locais estão autorizadas pelo proprietário. Mudanças externas exigem seu plano concreto; este documento não publica nem instala o MVP.
