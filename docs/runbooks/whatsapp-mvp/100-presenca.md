> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Presença no laboratório WhatsApp

## Jornada de passageiros recorrentes

O passageiro cadastra o seu ponto e escolhe se quer receber o lembrete. O motorista aprova o vínculo e as rotinas. Com os dois cadastros aprovados, W04 materializa a presença prevista para cada dia e sentido. Na véspera, o passageiro pode responder ao lembrete citando a mensagem. Também pode escrever “Não vou amanhã na ida” ou enviar “Presença” para escolher uma ocorrência. Quando a mensagem pode apontar para mais de uma saída, o bot exibe as datas e os sentidos antes de guardar a resposta.

O motorista envia “Presenças”, escolhe a saída e vê nomes e totais de confirmados, ausentes e pendentes. A lista operacional inclui confirmados e pendentes. O passageiro pode corrigir a resposta para aquela ocorrência até o corte operacional. Depois do início, uma nova solicitação de embarque precisa ser tratada com o motorista.

## Reproduzir as provas separadamente

1. Execute os testes do agente e do worker com o Node 22 do laboratório. Execute também os testes PostgreSQL do perfil de integração do backend. As provas de relógio verificam as 20h locais, a expiração da janela e o corte operacional.
2. Importe os oito workflows inativos na imagem n8n fixada pelo manifesto. O script `integrations/whatsapp/scripts/070-n8n-smoke.py` testa a chamada a W04, passa por reinício e verifica o conteúdo exportado. O controller usado nessa prova retorna contadores sintéticos.
3. Com um JAR da revisão limpa e o PostgreSQL isolado no laboratório documentado no 070, execute `node integrations/whatsapp/scripts/100-attendance-smoke.mjs DIRETORIO_PRIVADO SHA_BACKEND`. O script cadastra os usuários pelas mensagens e usa o worker e a API reais. O destino Evolution é um receptor de loopback que confere cada envio. O script limpa a coorte e revoga as quatro credenciais.
4. As duas rotinas sintéticas usam o fuso de Manaus, escolhido na conversa com o motorista. Para observar o envio na janela natural, a etapa de lembretes deve ocorrer entre 20h e 20h30 nesse fuso. O smoke pode começar a qualquer hora. Fora dessa janela, ele cria uma pergunta com prazo de laboratório, restrita ao passageiro da coorte sintética. O contador `seededReminderWindows` distingue esse caso. A comprovação dos limites das 20h usa o teste com relógio controlado. O ensaio de entrega pelo WhatsApp dedicado pertence ao card 170.

## Pausa e retomada

Para interromper novos acionamentos, desative W04 no ambiente correspondente. Os lembretes já na fila continuam sujeitos ao prazo, ao estado da conversa e à preferência na hora de enviar. Quando W04 retoma, a API procura o que falta e preserva as ocorrências explicitamente canceladas. A confirmação de que alguém recebeu uma mensagem depende do estado devolvido pelo canal.

A correção operacional pelo motorista antes de iniciar é integrada ao preparo da saída no card 110. O registro de presença do 100 identifica quem respondeu. A correção da lista guarda a autoria operacional do motorista separadamente.
