# Área da equipe

A área da equipe reúne a liberação de participantes e os convites de motorista.
Cada integrante usa sua própria conta do GitHub; não recebe acesso ao banco,
SSH ou credenciais do bot. A pessoa liberada continua responsável por concluir
seu cadastro e aceitar o aviso de dados no WhatsApp.

**Estado em 09/11/2026:** implementação em validação local. O login GitHub,
a configuração do servidor e a publicação ainda não foram concluídos.
O endereço previsto é `/equipe` na mesma API do ambiente compartilhado.
Este documento descreve o fluxo a ser disponibilizado, não um serviço já ativo.

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

## Acesso e responsabilidades

A aplicação verifica o acesso de escrita ao repositório pelo GitHub. A sessão
é individual, curta e fica em cookie seguro; as operações verificam a sessão,
a origem e a proteção contra requisições de outro site. A auditoria registra
a conta que executou cada operação, sem guardar telefone ou código no registro
da operação. As identidades do produto continuam no banco próprio do VanRides.

Um GitHub App dedicado precisa ser instalado somente neste repositório, com
leitura de metadados. O cadastro do app deve permitir autorização por usuários
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
O [guia do deploy automático](../../deploy/homologacao/AUTOMATICO.md) descreve
as credenciais do Actions, o serviço atualizado e o retorno à versão anterior.

A admissão dinâmica deve ser ativada somente depois de a nova API estar
saudável. Ao retornar a uma API anterior, retorne primeiro o receptor ao modo
estático preservado. O novo endpoint responde com erro em versões antigas,
sem permitir que elas criem cadastros por engano.

Não coloque telefone, convite, sessão, token ou conversa em issues. Registre
apenas cenário, horário, versão e resultado. Se o acesso for removido do GitHub,
a próxima verificação da sessão deve recusar a operação.
