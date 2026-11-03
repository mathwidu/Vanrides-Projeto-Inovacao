# Ativar o atendimento do grupo no n8n

Os fluxos acordam o worker por uma rede privada. A Evolution envia mensagens e recibos ao serviço de entrada do VanRides, que limita a instância e os contatos e encaminha o evento à API. O n8n recebe somente operações de controle.

1. Prepare o projeto VanRides no n8n e a credencial Header Auth denominada `VanRides HML Controle`. Configure o cabeçalho Authorization com o Bearer privado do worker e compartilhe a credencial somente dentro do projeto. Copie o ID atribuído pelo n8n.
2. Gere os oito exports em um diretório novo com `node src/whatsapp/workflows/generate.mjs --homologacao CAMINHO_NOVO ID_DA_CREDENCIAL`. O resultado contém a referência à credencial. Os valores privados ficam no n8n.
3. Confira que o executor do n8n alcança o worker pelo nome `vanrides-hml-worker` na porta 7080 e que os IDs gerados estão livres. Importe na ordem indicada pelo manifest, começando por W07. Confira os IDs efetivamente atribuídos pelo n8n após a importação. Se foram remapeados, ajuste os vínculos de erro e os chamadores autorizados antes de publicar.
4. Com o worker, a API, o número pareado e o contato de teste habilitados, publique W01 para processar mensagens e W06 para entregar respostas. No editor, confirme o primeiro recebimento e resposta no celular. W07 recebe falhas e não requer agendamento. W02, W03 e W05 são subfluxos.
5. Publique W04 para presença e W08 para reconciliação quando a credencial SYSTEM_JOB estiver no worker. Para suspender o atendimento, despublique os agendamentos desse projeto no editor.

A ativação dos fluxos exige o pareamento da instância dedicada e o cadastro individual dos contatos da equipe. Um fluxo importado e inativo não comprova troca de mensagens pelo WhatsApp.
