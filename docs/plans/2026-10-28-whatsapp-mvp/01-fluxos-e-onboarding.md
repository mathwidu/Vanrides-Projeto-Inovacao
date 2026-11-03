> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Fluxos, conversas e onboarding

Parte da [proposta do MVP](README.md), revisada em 29/10/2026. Mensagens são exemplos de produto, não mensagens enviadas. Botões representam ações; respostas por texto devem funcionar mesmo quando o canal não disponibilizar botões. O app mínimo VanRides coleta GPS; a integração com navegação externa fica fora do MVP (ADR-009, 02/11/2026).

## 1. Princípios de experiência

- Uma pergunta por vez, com contexto preservado entre mensagens e reinícios.
- Reutilizar informações confirmadas. Pedir confirmação quando houver ambiguidade relevante.
- Identificar sempre a data, o horário e o sentido quando alterar uma presença ou iniciar um percurso.
- Cadastro e configuração inicial são distintos da rotina diária; nenhuma ação manual por parada.
- Permitir “voltar”, “corrigir”, “continuar cadastro”, “ajuda” e “falar com suporte”.
- Não pedir senha de WhatsApp/Google, API key, código técnico do provedor ou acesso remoto ao telefone.
- Não mostrar coordenadas, tokens, IDs internos ou mensagens de exceção técnica ao usuário.
- Uma ação só é confirmada depois da gravação pela API. Uma solicitação de envio não equivale a mensagem entregue.

## 2. F01 — Motorista inicia cadastro

**Entrada:** conversa privada iniciada pelo motorista no número do VanRides.

1. Agente apresenta finalidade e reconhece a intenção “sou motorista”, sem conceder esse papel. Resolve identidade do evento autenticado, inclusive antes de existir perfil. O primeiro motorista conhecido usa convite de bootstrap/allowlist previamente habilitado pelo operador; desconhecido recebe orientação restrita.
2. Pergunta nome e como quer ser identificado aos passageiros.
3. Explica em linguagem simples quais dados serão usados e para quais avisos; registra o aceite aplicável e preferência de mensagens.
4. Pergunta dias da semana, horário de saída e destino habitual. Se houver ida e volta, configura cada uma separadamente.
5. Pergunta a sequência habitual dos pontos. No início ela pode ser montada conforme passageiros se cadastrarem, mas precisa de aprovação antes da primeira saída.
6. Mostra resumo editável e confirma o cadastro.

Exemplo: “Vou organizar suas presenças e avisar seus passageiros quando a van estiver se aproximando. Para isso, precisamos preparar uma vez o acompanhamento no seu celular.”

**Saída:** motorista cadastrado, com estado de preparação explícito. Cadastro concluído não habilita automaticamente avisos de aproximação.

**Exceções:** número já cadastrado retoma; mudança de nome não cria conta nova; papel duplicado não muda privilégios; troca de número é recuperação assistida, sem juntar cadastros só por nome.

## 3. F02 — Instalação e pareamento do motorista

**Pré-condições da equipe:** aplicativo assinado nos dois sistemas, identificadores estáveis, instalação preparada, página HTTPS e fluxo de links seguro, política de dados acessível e teste interno de coleta/instalação. Nada disso é tarefa do motorista configurar. Navigation Connect/verificação Google não é requisito do coletor próprio.

1. Agente identifica Android/iPhone e versão compatível. Telefone incompatível recebe orientação específica, sem loop de instalação. A equipe precisa validar as duas plataformas antes da primeira entrega.
2. Explica: “Vamos preparar o acompanhamento no seu celular. Depois você começa pelo link no WhatsApp, abre o VanRides e acompanha o estado.” Medir os toques reais: link direto quando suportado; no fallback iOS, página com botão e possível confirmação do sistema. A instalação de teste gratuita do iPhone precisa de renovação periódica.
3. Orienta instalação do APK assinado no Redmi 8 e do build Xcode/Personal Team no iPhone do proprietário. A preparação iOS assistida ocorre uma vez, com renovação do provisionamento gratuito em sete dias. Depois, o início diário vem pelo link; no fallback iOS, uma landing apresenta o botão Abrir VanRides. Usar Maps/Waze não é pré-requisito.
4. Após instalar, motorista abre o VanRides diretamente pelo ícone. O app cria seu pedido de pareamento vinculado à chave/PKCE antes de qualquer passagem ao WhatsApp. Não criar o primeiro vínculo por custom scheme.
5. O app mostra código curto que o motorista envia/confirma na conversa com o bot. A API associa ao motorista autenticado; o app resgata a sessão somente com a chave/PKCE originais. Um link de retorno pode abrir esse pedido já vinculado, sem trocar sua chave. Não há segundo formulário de cadastro ou senha própria.
6. Aplicativo confirma ao backend que a vinculação foi concluída. O bot reconhece essa confirmação, não apenas a resposta “instalei”.
7. App explica o uso da localização durante uma saída e solicita as permissões necessárias na ordem adequada ao sistema. Android pode exibir notificação persistente de acompanhamento; iPhone exibe os indicadores e permissões do sistema. Não orientar a conceder permissões genéricas sem necessidade comprovada.
8. Agente oferece teste de conexão; app inicia uma sessão curta de coleta e envio. Ao terminar o teste, interrompe a coleta. Aprovar coleta em primeiro plano e em segundo plano são verificações distintas.
9. Backend espera sessão ativa e atualizações recentes. Bot informa “Conexão testada” ou instrução de correção concreta.
10. Equipe realiza a prova de percurso no aparelho/modelo do piloto antes de marcar “pronto para aproximação”. Teste parado não comprova segundo plano em movimento.

**Recuperação:** “continuar configuração” retoma a etapa; link expirado gera substituto e invalida o anterior; app ausente abre instrução de instalação; outra conta/aparelho não herda vínculo pelo número digitado; substituição revoga o aparelho anterior. Exibir estados de conexão sem afirmar que GPS está ativo fora de uma sessão.

**Estados propostos:** `CADASTRADO → PAREAMENTO_PENDENTE → VINCULADO → CONEXAO_TESTADA → PERCURSO_VALIDADO`. Falha de permissão mantém progresso e aponta a ação restante.

## 4. F03 — Convite e cadastro do passageiro

1. Motorista pede “convidar passageiros”; agente gera convite revogável para seu grupo de atendimento.
2. Motorista compartilha esse convite com pessoas que atende. O bot não importa contatos ou envia convites a terceiros automaticamente.
3. Passageiro abre a conversa e vê a identidade do motorista: “Você quer se cadastrar com este motorista?”.
4. Informa nome. O próprio passageiro é o contato operacional no primeiro piloto.
5. Informa dias/sentidos habituais e confirma que deseja receber mensagens relacionadas ao transporte.
6. Confirma um ponto já aprovado ou propõe seu ponto pelo fluxo F04.
7. Agente mostra resumo e encaminha a solicitação ao motorista.
8. Motorista aprova vínculo e ponto uma vez. Passageiro recebe “Cadastro confirmado para a rotina …”.

**Antes da aprovação:** não receber posição da van nem informações de outros passageiros; não entrar automaticamente no roteiro ativo. Convite conhecido não concede acesso à lista de passageiros. D02 propõe um vínculo ativo por passageiro; tentativa de segundo vínculo exige resolução explícita.

## 5. F04 — Ponto habitual de embarque

O dado principal é o **ponto de embarque aprovado**, que pode ser diferente da residência. Localização pontual é suficiente; compartilhamento contínuo do passageiro fica fora do MVP.

| Entrada | Tratamento |
|---|---|
| Ponto já cadastrado pelo motorista | Mostrar nome/referência e pedir confirmação |
| Pin pontual pelo WhatsApp | Extrair coordenadas da mensagem suportada; pedir descrição curta de acesso/entrada |
| Endereço escrito | Geocodificar como sugestão, apresentar candidato e exigir confirmação; endereço ambíguo gera escolha |
| Localização em tempo real | Explicar que o cadastro precisa de um ponto fixo; pedir marcador ou confirmação explícita de qual ponto salvar, sem congelar silenciosamente o primeiro dado |
| Link de mapa | Aceitar apenas formatos reconhecidos e confirmar o ponto resultante; se não resolver com segurança, pedir pin |
| Texto contradiz pin | Mostrar a divergência e pedir correção; não escolher automaticamente |

Mensagem: “Envie o ponto onde a van deve te buscar. Se estiver nesse local, pode enviar sua localização atual. Se estiver em outro lugar, escolha o ponto no mapa.”

Depois: “É na entrada principal, portão lateral ou outro acesso?”. Referência é opcional quando o ponto já está claro; não obrigar preencher endereço postal completo campo por campo.

Salvar versão de coordenadas, rótulo, referência e quem aprovou. O motorista confirma adequação do ponto, inclusive lado da rua/entrada. A API não determina automaticamente segurança física de embarque.

Alteração de ponto fica pendente até aprovação. Durante uma saída iniciada, o ponto do snapshot permanece válido; não prometer atendimento no novo lugar nessa mesma saída.

## 6. F05 — Preparar rotinas habituais

Uma rotina contém nome simples (“Ida das 6h”), dias, timezone, horário, destino final e sequência aprovada. A volta possui configuração própria. O motorista revisa resumo no WhatsApp; não precisa abrir cadastro de viagens a cada dia.

Participação habitual não equivale a presença confirmada. Para uma data, a API materializa a ocorrência e as respostas. Cancelamento de uma ocorrência não apaga a rotina recorrente. Alterar rotina afeta somente ocorrências futuras ainda não iniciadas.

O MVP não promete navegação multiparada programada automaticamente no Maps/Waze. O motorista segue a sequência habitual aprovada; o acompanhamento funciona independentemente de abrir um navegador. Não incluir botões de navegação externa nesta entrega.

## 7. F06 — Presença por data e sentido

No horário acordado: “Amanhã, na ida das 6h, você vai? Responda VOU ou NÃO VOU.”

| Estado | Significado e efeito proposto |
|---|---|
| Confirmado | Passageiro explicitamente vai nessa ocorrência |
| Não vai | Retira antes da partida; mantém histórico da resposta |
| Pendente | Não respondeu; pela D03 confirmada, continua previsto no roteiro habitual e aparece como pendente; recebe aviso se continuar elegível |
| Alteração tardia | Saída já iniciada; a resposta é registrada com regra de exceção |

Um “sim” só vale quando há uma pergunta inequívoca e ainda válida. Caso existam ida/volta ou datas concorrentes, o agente pergunta qual. Resposta de ontem não altera hoje. A confirmação devolve a data e o sentido. Um lembrete no máximo por janela é a proposta; horário, prazo e limite são fechados em D10.

Antes da partida: “Para a ida de hoje: confirmados, não vão e pendentes. Pela regra combinada, pendentes continuam previstos.” O motorista pode corrigir a lista em conversa antes de iniciar.

O corte da lista ocorre quando o ativador confirma o início na API (`STARTING`), antes de aguardar o GPS. Depois desse corte: cancelamento suspende avisos individuais futuros, mas não promete desvio/remoção imediata da rota. Inclusão ou mudança de ponto recebe resposta de solicitação pendente, sem prometer atendimento. A rotina normal não exige olhar o telefone em cada parada.

## 8. F07 — Início e retomada

1. Motorista escreve “começar”. Se houver uma única ocorrência elegível, o agente apresenta seu nome e link. Se houver várias, pergunta qual.
2. API cria ou recupera a mesma intenção de início. Cliques e mensagens duplicados não criam várias sessões.
3. Link abre o ativador já vinculado em primeiro plano. Ao confirmar a ativação na API, roteiro, pontos, participantes e política são congelados/versionados (`STARTING`); o app recebe autorização de coleta para aquela saída e inicia o serviço nativo.
4. Se uma permissão estiver faltando, mostra a ação necessária antes de conduzir. O app oferece status, parada/retomada e retorno ao WhatsApp; navegação externa fica fora do MVP. O objetivo diário de poucos toques precisa ser medido, não presumido.
5. Só depois de coletor ativo e posição recente recebida pela API: “Acompanhamento ativo. Você pode voltar ao WhatsApp e deixar o VanRides em segundo plano.” Explicar na configuração que encerrar à força o VanRides é diferente de deixá-lo em segundo plano.
6. O estado muda para acompanhamento ativo; o snapshot já criado permanece o mesmo. Respostas recebidas enquanto se aguardava conexão seguem a regra de alteração tardia.

Se a pessoa apenas abriu o link ou recusou permissão, mostrar “Aguardando conexão”, nunca “em acompanhamento”. Se já existe saída ativa, oferecer retomá-la; não criar outra por reinício do n8n ou novo “começar”. A autorização de coleta não pode ser reutilizada em outro run ou aparelho.

## 9. F08 — Avisos e consultas durante o percurso

- Passageiro elegível recebe um aviso quando a regra de aproximação validada para seu ponto for atingida.
- Mensagem inicial proposta: “Atualização das 06h10: a van está se aproximando do seu ponto de embarque. Prepare-se.” Horário meramente ilustrativo; usar o instante real da decisão para contextualizar eventual atraso de entrega. Não preencher minutos inventados.
- O envio considera sequência, sentido, posição recente, cancelamento e se o ponto ainda é futuro. Passagem por rua paralela ou perto de parada posterior não deve bastar.
- “Onde está a van?” devolve pin estático da última posição recente autorizada e horário de aquisição, ou informa indisponibilidade. É consulta pontual atualizada a cada pedido, não mensagem nativa de localização ao vivo do WhatsApp. Não compartilhar o roteiro completo ou pontos de terceiros; posição vencida não é apresentada como atual.
- GPS interrompido suspende avisos de aproximação e informa o problema sem repetição excessiva. Posição antiga armazenada não é posição atual.
- Após reconectar, ignorar alertas vencidos; não anunciar chegada a um ponto já passado.
- “Passou pelo ponto” é estado operacional; não significa “passageiro embarcou”.

## 10. F09 — Encerramento

Objetivo: ao chegar ao destino final e cumprir a regra de conclusão validada, encerrar o acompanhamento na API, fazer o app interromper a coleta e informar a conclusão. Diferenciar “percurso concluído” de “coleta parada no aparelho”: só afirmar a segunda depois do ACK do coletor.

Durante a prova, validar que o destino não é apenas uma parada intermediária e que passar perto dele não encerra cedo. App suspenso, falha técnica, falta de GPS ou fim do horário previsto não significam conclusão.

Comando “finalizar” é alternativa explícita, com confirmação se ainda houver inconsistência. Se a conclusão automática não for confiável, a exigência de uma confirmação final precisa ser aceita pelo proprietário para o piloto; não é uma simplificação já aprovada.

Encerrar na API bloqueia novos avisos e o uso de novas posições para aquela saída; a resposta de controle manda o coletor parar. Sem rede, o desligamento remoto não é instantâneo: autorização com duração limitada e proteção local restringem a coleta, com teste específico. O motorista sempre pode interromper pelo app. Não prometer que o bot fecha outros aplicativos ou desliga o GPS do telefone inteiro.

## 11. F10 — Correções, suporte e saída do serviço

| Solicitação | Resultado |
|---|---|
| “Corrigir meu nome” | Atualização confirmada sem refazer cadastro |
| “Alterar ponto” | Nova versão submetida ao motorista; mantém ponto ativo até decisão |
| “Troquei de celular” | Novo pareamento a partir da identidade conhecida; revoga aparelho antigo |
| “Parar avisos” | Confirma preferência; não interpreta automaticamente como “não vou” |
| “Não uso mais essa van” | Desativa vínculo e remove de ocorrências futuras não iniciadas |
| “Apagar meus dados” | Abre procedimento identificável e auditável de exclusão/retenção aplicável |
| “Ajuda” | Explica ações possíveis e oferece atendimento humano |

O suporte é papel restrito de operação, não uma terceira persona de produto com portal completo. Encaminhamento humano suspende respostas automáticas conflitantes naquela conversa. Troca de número e disputas de identidade exigem verificação, sem revelar dados de outra conta.

## 12. Critério de simplicidade

Medir em teste: passos para cadastro, abandono, retomada, instalação limpa, consentimento, toques por saída e intervenções durante percurso. O alvo é zero formulários repetidos, zero ação por parada e uma ativação por saída após a configuração inicial. Permissões e confirmações do sistema operacional devem ser contadas honestamente.
