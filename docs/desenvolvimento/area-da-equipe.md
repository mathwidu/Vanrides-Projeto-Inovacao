# Área da equipe

A área da equipe reúne a liberação de participantes e os convites de motorista.
Cada integrante usa sua própria conta do GitHub; não recebe acesso ao banco,
SSH ou credenciais do bot. A pessoa liberada continua responsável por concluir
seu cadastro e aceitar o aviso de dados no WhatsApp.

**Disponível em 09/11/2026:** [abrir área da equipe](https://vanrides-inovacao-api.matheusduarte.dev.br/equipe).
Login real do mantenedor, consulta/liberação do passageiro e recuperação do
convite de motorista foram verificados no ambiente online. O acesso do Rafael também foi confirmado pela captura enviada ao mantenedor em 10/11.

## Preparar um teste

1. Acesse a área e escolha **Entrar com GitHub**. Use a conta que tem acesso de
   escrita ao repositório `mathwidu/Vanrides-Projeto-Inovacao`.
2. Informe o WhatsApp pessoal do participante com país e DDD. Confirme que ele
   concordou com o teste. O número do bot não substitui o número do participante.
3. Escolha **Passageiro** ou **Motorista** e libere o contato.
4. Passageiro: oriente a enviar `AJUDA` e `QUERO ME CADASTRAR` ao bot.
   Motorista: copie as instruções e entregue o convite em conversa privada.
   Ele envia somente o código ao bot, antes de concluir o cadastro.
5. Use **Consultar cadastro** para acompanhar liberação, papel e convite.
   Um convite de motorista expirado e não utilizado pode ser renovado.
   Convites são individuais e valem 24 horas.
6. Depois, siga a [jornada de testes](homologacao-da-equipe.md).

Repetir a operação após uma falha de rede reutiliza o mesmo identificador e não
cria outra liberação. Uma conta já ativa em outro papel não é convertida pela
área. A consulta não revela o código do convite; emitir novamente um convite
ativo recupera o convite elegível sem estender sua validade.

## Testar o passageiro com o motorista de teste

Use o **Motorista de teste da equipe** para testar o cadastro e o vínculo do
passageiro sem um segundo número de motorista. Ele tem uma rotina fictícia de
ida, todos os dias às 07h30.

1. Libere o número como **Passageiro**. No bot, conclua o nome e o aceite do aviso
   de dados. O contato precisa estar admitido e com cadastro ativo.
2. Na área da equipe, em **Motorista de teste da equipe**, toque em
   **Gerar convite de passageiro**. O convite tem um uso e vale 24 horas.
3. Pelo WhatsApp do passageiro, envie ao bot somente `PASSAGEIRO <código>`,
   usando o código exibido.
4. Siga as perguntas sobre rotina, dias e ponto de embarque. Envie o ponto pela
   função de **localização estática do WhatsApp**, confira o resumo e use a
   confirmação apresentada na conversa.
5. Volte à área, toque em **Atualizar pedidos** e escolha **Aprovar vínculo e
   ponto**. Confira o aviso recebido pelo passageiro no WhatsApp.

A lista também permite pedir correção do ponto, recusar o pedido, aprovar um
novo ponto e encerrar o vínculo. As decisões geram os avisos correspondentes ao
passageiro. Os pedidos destinados ao motorista ficam na área da equipe.

Presença, saída, GPS e avisos de aproximação exigem um motorista real.

## Acesso e responsabilidades

A aplicação verifica o acesso de escrita ao repositório pelo GitHub. A sessão
é individual, curta e fica em cookie seguro; as operações verificam a sessão,
a origem e a proteção contra requisições de outro site. A auditoria registra
a conta que executou cada operação, sem guardar telefone ou código no registro
da operação. As identidades do produto continuam no banco próprio do VanRides.

O GitHub App **VanRides Equipe** está instalado somente neste repositório, com
leitura de metadados. O cadastro permite autorização por usuários
externos à conta proprietária; o backend restringe o acesso à instalação e ao
repositório configurados. O token pessoal do mantenedor não é usado pelo site.

A Evolution confirma o número canônico. Mensagens autenticadas da mesma
instância podem associar um identificador LID ainda livre a um telefone já
liberado; números desconhecidos e identificadores conflitantes são ignorados.
O receptor usa uma rota exclusiva desta política, para que uma API antiga não
processe mensagens destinadas à nova política durante uma troca de versão.

O GitHub confirma quem opera; a liberação habilita um teste do produto. Isso
não dá permissão de alterar n8n, Evolution, banco, infraestrutura ou segredos.

## Publicação e manutenção

A tela faz parte do mesmo JAR da API. A configuração inicial requer o GitHub
App, seus segredos no Swarm, acesso interno à Evolution e a migração V39.
O motorista de teste também depende da V40. O estado da publicação fica no
[guia de homologação](homologacao-da-equipe.md).
O [guia do deploy automático](../../deploy/homologacao/AUTOMATICO.md) descreve
as credenciais do Actions, o serviço atualizado e o retorno à versão anterior.

A admissão dinâmica deve ser ativada somente depois de a nova API estar
saudável. Ao retornar a uma API anterior, retorne primeiro o receptor ao modo
estático preservado. O novo endpoint responde com erro em versões antigas,
sem permitir que elas criem cadastros por engano.

Não coloque telefone, convite, sessão, token ou conversa em issues. Registre
apenas cenário, horário, versão e resultado. Se o acesso for removido do GitHub,
a próxima verificação da sessão deve recusar a operação.

## Se não conseguir entrar

Use a conta GitHub que aceitou o convite de colaboração no repositório. No
primeiro acesso, o GitHub pede autorização ao **VanRides Equipe**. O aplicativo
confere o acesso de escrita; não pede senha da VPS ou token de administrador.
Uma conta diferente, convite de colaboração não aceito ou permissão apenas de
leitura impedem as operações. Não instale o aplicativo em outros repositórios.

Após 30 minutos ou uma atualização da API, pode ser necessário entrar novamente.
Na issue, informe apenas o horário e a mensagem apresentada; nunca copie cookies,
convites ou o telefone do participante. Cada integrante opera com sua identidade.

A área libera testes e convites. Ela não permite alterar um participante ativo
para outro papel nem substitui o aceite de dados feito pelo participante no bot.
