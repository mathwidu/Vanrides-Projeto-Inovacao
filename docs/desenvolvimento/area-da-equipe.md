# Área da equipe

A área da equipe reúne a liberação de participantes e os convites de motorista.
Cada integrante usa sua própria conta do GitHub; não recebe acesso ao banco,
SSH ou credenciais do bot. A pessoa liberada continua responsável por concluir
seu cadastro e aceitar o aviso de dados no WhatsApp.

**Disponível em 09/11/2026:** [abrir área da equipe](https://vanrides-inovacao-api.matheusduarte.dev.br/equipe).
Login real do mantenedor, consulta/liberação do passageiro e recuperação do
convite de motorista foram verificados no ambiente online. A equipe também
confirmou acesso ao painel em testes com contas individuais.

O botão **Abrir guia de testes** do painel leva à
[homologação da equipe](homologacao-da-equipe.md). Ali estão os dois caminhos:
praticar cadastro sem motorista físico e testar a jornada completa com números
distintos. O fluxo do PR #133 está publicado, com API `5ef07cb`, banco V48 e
APK Android **0.5.0+16 disponível para instalar**. Worker, receptor e páginas
foram atualizados. A publicação técnica foi conferida; os novos percursos de
conexão, início e fim ainda precisam de prova no WhatsApp e nos aparelhos.

## Preparar um teste

1. Acesse a área e escolha **Entrar com GitHub**. Use a conta que tem acesso de
   escrita ao repositório `mathwidu/Vanrides-Projeto-Inovacao`.
2. Informe o WhatsApp pessoal do participante com país e DDD. Confirme que ele
   concordou com o teste. O número do bot não substitui o número do participante.
3. Escolha **Passageiro** ou **Motorista** e libere o contato.
4. Siga a próxima etapa mostrada no resultado. Use **Abrir conversa com o bot**
   ou o contato já compartilhado pela equipe. Para testar a resposta, envie
   `AJUDA` pelo WhatsApp do participante.
5. **Preparo inicial do motorista no piloto:** use **Copiar link para o
   motorista** e encaminhe o link individual em privado. Ele abre pelo próprio
   WhatsApp, envia a mensagem pronta e segue nome e aviso de dados. Isso
   autoriza seu papel; não conecta o aplicativo.
   **Passageiro:** prefira abrir o link individual compartilhado pelo motorista
   e enviar a mensagem preenchida. Não precisa ler nem redigitar o conteúdo
   técnico do convite.
6. Use **Consultar cadastro** para acompanhar liberação, papel e convite.
   Um convite de motorista expirado e não utilizado pode ser renovado.
   Convites são individuais e valem 24 horas.
7. Depois, siga a [jornada de testes](homologacao-da-equipe.md). Com o cadastro
   pronto, a conexão do celular é feita pelo bot com “Conectar meu celular”,
   sem copiar códigos do aplicativo. Nome, trajetos e permissões não precisam
   ser refeitos a cada saída.

Repetir a operação após uma falha de rede reutiliza o mesmo identificador e não
cria outra liberação. Uma conta já ativa em outro papel não é convertida pela
área. A consulta não revela o código do convite; emitir novamente um convite
ativo recupera o convite elegível sem estender sua validade.

## Convite inicial do motorista por link

A interface oferece **Copiar link para o motorista** para a equipe
encaminhar o convite em privado. O link abre a conversa com o bot e já
preenche a mensagem do convite; o motorista confere e toca em **Enviar**.
O WhatsApp continua mostrando essa mensagem antes do envio. O link evita
copiar ou redigitar o código, mas não o torna invisível nessa conversa.

A opção secundária **Abrir neste WhatsApp** usa a conta conectada no aparelho
ou navegador atual. Só deve ser usada se essa for a conta do motorista
convidado; o integrante que prepara outro motorista deve encaminhar o link.
O código manual permanece nos detalhes para contingência. Esta melhoria só
muda a apresentação do convite: não conecta o app, não troca o papel de um passageiro e não aceita
o aviso de dados pela pessoa.

## Retomar uma conversa após suporte

Quando a pessoa pede `SUPORTE`, o bot pausa as ações de negócio e preserva o
cadastro e o rascunho necessário. Um integrante pode consultar o número em
**Consultar cadastro**, revisar o problema com a pessoa e usar **Retomar
conversa**. Depois, a pessoa envia `AJUDA` ao bot para continuar. Essa ação
não exige um novo cadastro nem aceita o aviso de dados por ela.

O botão aparece somente para o caso de suporte atual de um perfil ativo ou
em cadastro. A retomada encerra aquele caso e invalida confirmações antigas;
não converte o papel, não reativa perfil suspenso ou encerrado e não renova
convites, credenciais ou autorizações de motorista. Uma repetição após falha
de rede não encerra um novo pedido de suporte que tenha surgido depois.
A auditoria registra o identificador GitHub do integrante e o caso retomado.
O atendimento humano e a disponibilidade da equipe continuam combinados
fora do bot.

## Testar o passageiro com o motorista de teste

Use **Testar com o WhatsApp** quando quiser confirmar a jornada real com um
único número de passageiro. O papel de motorista fica no painel.

1. Libere seu número como **Passageiro**.
2. Em **Motorista de teste da equipe**, toque em **Gerar convite de passageiro**.
3. Use **Abrir convite no WhatsApp** e envie a mensagem preenchida. Se a
   conversa já estiver aberta no Mac, use **Copiar mensagem do passageiro**
   e cole no chat. O convite tem um uso e vale 24 horas.
4. No primeiro cadastro, informe o nome e leia o aviso de dados. Confira os
   dias sugeridos e envie o ponto de embarque pela localização do WhatsApp no
   celular. Revise e confirme uma vez para enviar o pedido.
5. Toque em **Atualizar pedidos** e escolha a ação do motorista: aprovar
   vínculo e ponto, pedir correção do ponto, recusar, aprovar novo ponto ou
   encerrar o vínculo.

O passageiro recebe no WhatsApp os mesmos avisos que receberia de um motorista
real. O que iria para o motorista aparece só na lista da área. Presença, saída,
GPS e avisos de aproximação não acontecem com esse motorista; para esses
testes, use um motorista real.

## Simular conversas no navegador

Abra **Simular conversas** e comece no papel de motorista. Siga as opções de
cadastro, rotina e convite, depois troque para **Passageiro** para usar o
convite e indicar o embarque. Volte a **Motorista** para aprovar o pedido.
A simulação guarda o progresso enquanto a página permanece aberta. Use
**Recomeçar** para praticar o outro formato de convite. Ela usa dados fictícios
e o estado fica no navegador. Para conferir o bot, a entrega de mensagens e a
API da homologação, siga a jornada real da seção anterior.
A simulação é um treino com dados fictícios; não comprova conexão de aparelho,
GPS ou o novo percurso entre WhatsApp e aplicativo. Para esses casos, use o
[roteiro do candidato](roteiro-validacao-fluxo-simples.md), depois da publicação
compatível e com os aparelhos preparados.

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
