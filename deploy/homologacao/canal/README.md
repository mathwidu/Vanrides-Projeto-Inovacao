# Canal de WhatsApp da homologação

A API do grupo continua em sua própria stack. Esta segunda stack reúne o receptor de eventos, o worker, a instância Evolution exclusiva, o PostgreSQL e o Redis da Evolution e um proxy HTTPS interno. O worker também entra na rede existente n8n_internal, onde o executor do n8n o encontra pelo nome vanrides-hml-worker. Não há porta publicada no host nem domínio adicional de administração.

## Preparar a instância

Use o nome vanrides-hml na Evolution, no cadastro das credenciais técnicas da API, na configuração privada do receptor e no worker. Separe a chave administrativa da Evolution, o token da instância usado pelo worker, o segredo de webhook, os quatro papéis da API e o token de controle do n8n. O número pessoal do passageiro é um contato de teste. A linha do bot é a conta já ativa no WhatsApp Business do Redmi.

1. Gere no servidor a senha do banco da Evolution, a senha do Redis, a chave administrativa, o segredo do webhook e o token de controle. Todas as entradas são exclusivas dessa homologação. O Redis usa um arquivo de configuração privado com requirepass, sem snapshot e sem AOF, e uma URI com a mesma senha na Evolution. Use valores base64url sem caracteres reservados de URI.
2. Para o HTTPS interno, crie uma CA exclusiva e um certificado de servidor com SAN DNS:evolution-hml. Distribua ao worker só a CA pública. Monte a chave privada do servidor apenas no proxy. Guarde as chaves fora de Git.
3. Pelo operador local da API, provisione CHANNEL, CONVERSATION_WORKER, DELIVERY_WORKER e SYSTEM_JOB para a instância vanrides-hml, com vencimento dentro da janela de homologação. Defina os integrantes autorizados e os vínculos com papéis próprios. Uma conta de teste que já possua o aplicativo não ganha acesso com a chave do operador.
4. Prepare o secret do receptor com a janela, os contatos autorizados e os segredos de webhook e de CHANNEL. Use o schema do receptor de homologação. Comece com um contato já disponível e acrescente os demais quando aderirem. Não importe a agenda do Redmi.
5. Distribua as fontes congeladas de agent, transport, worker e lab/live-ingress em uma pasta de versão somente leitura. Defina VANRIDES_WHATSAPP_SOURCE para essa pasta. Crie exclusivamente os secrets nomeados na stack e confira imagens, volume novo, recursos disponíveis e escopo da rede n8n_internal. A instância de n8n existente continua no mesmo endereço e com o mesmo executor.
6. Suba vanrides-hml-canal e confira seus serviços antes de criar a instância na Evolution. Cadastre o webhook da instância em http://ingress:7180/evolution/webhook, com header Bearer próprio, eventos MESSAGES_UPSERT, MESSAGES_UPDATE e CONNECTION_UPDATE, webhook por evento e base64 desativados. Configure 7180 também no receptor. O QR é tratado fora do Git e das issues e exibido apenas ao dono do bot para o pareamento.
7. Gere e importe os fluxos conforme [o guia do n8n](../../../src/whatsapp/workflows/HOMOLOGACAO.md). O modo inicial usa o interpretador determinístico e não consome crédito de modelo. Abra o atendimento somente após o estado OPEN da instância. Confira uma mensagem do contato autorizado, a resposta e seu recibo de entrega.

## Demonstração em equipe

A primeira demonstração precisa de uma conversa autorizada para o motorista e outra para o passageiro, distintas da conta do bot. No WhatsApp, o motorista inicia o cadastro e vincula o aplicativo. O passageiro entra com seu convite, envia o ponto fixo e responde à presença. Na saída, o motorista abre o link, autoriza a localização no Android e confere a chegada das amostras à API. Encerre a coleta após a apresentação. A aproximação e o encerramento automático continuam com seu andamento próprio no backlog.

Para reverter, desative somente os agendamentos VanRides do n8n, desligue o webhook da instância dedicada e retire a stack vanrides-hml-canal. Preserve os volumes, sessões e dados da equipe para recuperação. O código de atendimento, a stack da API e o APK têm revisões independentes. Uma importação de workflow ou um pareamento isolado não confirmam a jornada completa.
