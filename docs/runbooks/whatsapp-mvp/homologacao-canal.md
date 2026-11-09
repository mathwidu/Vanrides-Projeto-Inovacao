# Canal WhatsApp da homologação da equipe

Registro de 04/11/2026, relacionado à issue #91. A autorização do proprietário
abrangeu publicação do canal, importação dos fluxos, admissão do contato de teste
e preparação do QR no Redmi. Nenhum serviço pago ou modelo de IA foi ativado.

## O que foi aplicado

- Repositório `mathwidu/Vanrides-Projeto-Inovacao`, branch
  `codex/homologacao-api-equipe`, fonte de execução `c7cbe3d`.
- VPS `187.77.47.96`, stack `vanrides-hml-canal`: Evolution 2.3.7, PostgreSQL,
  Redis, proxy TLS interno, receptor de eventos e worker. Seis serviços 1/1.
  Imagens fixadas por digest em `deploy/homologacao/canal/stack.yml`.
- Rede privada própria e integração do worker à rede existente do n8n. Nenhuma
  porta adicional publicada na internet. Dois volumes exclusivos para banco e
  sessão do WhatsApp. Credenciais próprias em Docker Secrets.
- Instância `vanrides-hml`, sem sincronização de histórico/agenda/grupos.
  Webhook de mensagens, estados de entrega e conexão apontado ao receptor privado.
- Quatro credenciais da API com funções separadas: canal, conversa, envio e
  agendamento. Expiram em **01/12/2026 00:00 UTC**. Renovar antes dessa data.
- Lote técnico separado dos participantes reais. Um passageiro admitido com
  confirmação do proprietário, sem preencher automaticamente perfil ou papel.
- Oito fluxos novos no projeto pessoal autorizado do n8n. Uma Header Auth
  exclusiva controla o worker. W07 e W01/W04/W06/W08 publicados após conexão OPEN do bot.
  W02/W03/W05 permanecem subfluxos inativos.

A API foi atualizada de `57a2ffd` para `acc572f` após autorização e repetição
dos testes de passageiro; o aplicativo continua
`0.3.0-hml+13`. O JAR administrativo é executado em serviços temporários
não web, sem reiniciar a API nem executar migração. Os sete pedidos
administrativos iniciais e a correção posterior da admissão retornaram COMMITTED;
os serviços temporários foram removidos.

## Evidências e limites

| Conferência | Resultado |
| --- | --- |
| API HTTPS depois da preparação | 200 / UP |
| Réplicas do canal | 6 serviços com 1/1 |
| Inicialização do receptor e worker | READY |
| Saúde privada do worker | 200 |
| HTTPS Evolution com CA própria e hostname validado | 200 |
| Controle sem credencial | 401 / UNAUTHORIZED |
| Controle autenticado, inbox vazio | 200 / IDLE, processed=0 |
| n8n: importação | 8 fluxos próprios, inicialmente inativos |
| n8n: publicação de W07 | Publicado, confirmado também no editor |
| Fluxos preexistentes do n8n | Estados e versões preservados |
| Pareamento WhatsApp | Confirmado no Redmi pelo proprietário e OPEN no servidor |
| Agendamentos W01/W04/W06/W08 | Publicados, com estados e versões dos outros fluxos preservados |
| Execução real dos agendamentos | W01/W06 com execuções success; W04/W08 também executaram após correção do intervalo |
| Conversa real, envio e recibo | 2 mensagens COMPLETED, 2 respostas DELIVERED, 4 callbacks STATUS COMPLETED; respostas visíveis no WhatsApp do Mac |
| GPS pelo canal publicado | Ainda não verificado; requer participante motorista |

Os testes físicos do iPhone continuam pausados. Os avisos de aproximação
continuam desabilitados até a calibração e validação previstas. Não confundir
instalação dos serviços com aceite de todas as funcionalidades do MVP.

## Decisões da operação

1. A Evolution é exclusiva deste ambiente para separar sessão, armazenamento e
   configuração dos outros projetos da VPS. O n8n existente foi reutilizado.
2. A importação usa IDs exclusivos e confere colisões antes de escrever.
   A CLI instalada do n8n 2.45.4, em queue, suporta a publicação por
   `import:workflow --activeState=fromJson` sem reiniciar o serviço compartilhado.
   Publicar apenas os IDs dessa entrega. Ver `deploy/homologacao/canal/n8n.md`.
3. W07 precisa de versão publicada. Sua lista de chamadores foi ajustada na
   cópia implantada para os outros sete fluxos desta entrega; não foi aberto
   acesso a fluxos de outros projetos. O helper registra o hash derivado.
4. A primeira tentativa administrativa falhou na consulta de serviço: o Docker
   não aceita `service ls --no-trunc`. Nenhuma solicitação havia sido enviada.
   O serviço daquela tentativa foi removido após verificar seu identificador
   de propriedade. A consulta passou a usar nome e inspeção do ID completo.
5. Arquivos públicos montados no operador recebem modo 0444 explícito,
   independentemente do umask. Solicitações, recibos, tokens e QR permanecem
   privados e fora do Git. O operador também trata a precisão do timestamp Java.

## Ajuste de horário e pareamento pela interface

Na tentativa de pareamento de 04/11, o Redmi informou que não foi possível
conectar o aparelho. O servidor permaneceu `connecting`, sem código de
encerramento persistido. Esse estado não comprova conexão nem identifica a
causa da recusa no telefone.

Foi identificado separadamente um erro de horário na Evolution 2.3.7: ela
subtrai o offset local e usa o sufixo UTC no timestamp. A imagem estava com
`TZ=America/Sao_Paulo` e offset de 180 minutos, fazendo o receptor descartar
os primeiros callbacks como anteriores ao início da janela. Foi aplicado
`TZ=UTC` apenas ao serviço Evolution dedicado, mantendo imagem e volumes.
A tarefa nova confirmou offset zero. Após o ajuste, dois callbacks de conexão
foram encaminhados à API (`FORWARDED`). Os filtros de horário e identidade
foram preservados. A correção desse erro não comprova resolução do pareamento.

Para a tentativa solicitada pelo proprietário, o Evolution Manager foi aberto
no Chrome por túnel SSH restrito a `127.0.0.1` no Mac. Nenhuma porta do painel
foi publicada na internet. A instância exibida é `vanrides-hml`; o QR foi
aberto na interface. A credencial de login não integra documentação ou Git.
Após o proprietário confirmar o pareamento pela interface, a API retornou OPEN.
Foram publicados os quatro agendamentos próprios, sem reiniciar o n8n.
O primeiro AJUDA foi confirmado no aparelho do bot. Uma repetição chegou ao
receptor, mas foi descartada pela política antes da API; a resposta ainda depende
da repetição após os ajustes abaixo.

Fonte do erro de horário: [serviço de canal da versão fixada](https://github.com/EvolutionAPI/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/services/channel.service.ts#L414-L427).

## Ajustes após a primeira mensagem real

W04 e W08 usavam 60 no campo de segundos do Schedule Trigger 1.4. Essa versão
aceita de 1 a 59; o estado publicado não significava acionador registrado.
Os dois fluxos passaram a usar um minuto, preservando a cadência pretendida.
Somente esses IDs foram republicados. A consulta de execuções confirmou sucesso
nos quatro agendamentos; os outros fluxos e o n8n compartilhado foram preservados.
O teste dos exports passou em 10 verificações.

A consulta autenticada de existência do contato retornou para o passageiro um
JID telefônico diferente do número digitado, sem o nono dígito. Foi usado
exclusivamente o retorno dessa consulta individual; não foi criada regra global
para remover dígitos. A admissão anterior não tinha perfil, mensagens, comandos
ou respostas. Uma nova identidade admitida passou a representar o JID confirmado,
com vínculo de auditoria privado à admissão anterior. Esta foi preservada; não
houve edição direta de aliases ou migração de dados.

O receptor recebeu uma nova versão do secret (`ingress-v2`), alterando somente
o identificador desse contato. Tokens, prazo e limites foram preservados. Os
arquivos `policy.mjs` e `server.mjs` passaram a registrar motivos fixos de descarte,
sem mensagem, número, LID, GPS ou credenciais. Os critérios de aceitação e o
HTTP 204 de descarte permanecem iguais. Validação: 24 testes direcionados e
revisão independente sem achados bloqueadores. Somente o receptor reiniciou;
voltou a READY e a instância continuou OPEN.

A captura temporária de stdout da Evolution foi filtrada em memória, sem salvar
conteúdo bruto. A repetição observada não apresentou erro de descriptografia ou
webhook. Não houve atualização de versão da Evolution ou Baileys. O diagnóstico
do receptor dispensa novas janelas de captura de 90 segundos.

A nova mensagem passou pela identificação, mas retornou `HML_DROP_MESSAGE_SHAPE`.
A reprodução autorizada no WhatsApp do Mac confirmou o mesmo resultado. A captura
restrita em memória encontrou os campos `conversation` e `messageContextInfo`, sem
erro de descriptografia ou webhook. O filtro exigia um único campo no corpo.

A correção aceita, somente em HML, `messageContextInfo` objeto junto de um único
conteúdo suportado e descarta essa metadata integralmente. Ela não participa da
identificação, resposta ou localização. Conteúdos mistos, wrappers, metadata
inválida e aliases desconhecidos continuam rejeitados; o modo legado foi
preservado. Teste de regressão falhou antes da correção e passou depois; suíte
direcionada 26/26, revisão independente sem bloqueadores. O retorno operacional
é `ingress-before-canonical/policy-before-metadata.mjs` e reinício apenas do receptor.

Após o ajuste, o teste autorizado no WhatsApp do Mac confirmou a resposta de ajuda
e a pergunta inicial do cadastro. A API confirmou 2 mensagens COMPLETED,
2 respostas DELIVERED e 4 callbacks STATUS COMPLETED. O cadastro ficou na pergunta
do nome; não houve aceite de aviso, vínculo com motorista ou GPS nessa rodada.
Não confundir esta prova do canal com a validação completa das jornadas do MVP.
Um eventual LID exige verificação própria; a consulta de existência de número
não comprova PN→LID.

## Proveniência

- Fontes do canal: SHA-256
  `2d48f3ecf4ef66cb6667c0cc9d869e03956678697e4d207d7cc5478008c6941c`.
- JAR administrativo: SHA-256
  `29dd480124e7e515cd47aac30b4eeea46a56bfdf134e91f62b343aa4b91f9773`.
- Bundle n8n inativo aplicado, com ajuste restrito do W07: SHA-256
  `ecd5aad4cbd9584696e0f1f0ba9decbc2c21294f88410a69a59858fd3830dfcc`.
- Ajuste de W04/W08 aplicado: SHA-256
  `4fa8c72e2609caf820b44d21d69dd24b5dbfc6819bbd318dc69f9ca805b78822`.
- Receptor atualizado: `policy.mjs` SHA-256
  `4016a1baddbbd1029b24da6f6bbd0ce550b688d083906955fdf2cf16c0da8fb6`;
  `server.mjs` SHA-256
  `6a9a595de91a25bea067dd33a0ecdf25031cbd37933ad3eeb4cab2319a58bae4`.
- Namespace dos fluxos: `vrHml6dffaf010805` (W01 a W08).
- Recibos administrativos e de importação ficam no diretório privado do operador
  na VPS, dentro de `homologacao/canal-c7cbe3d`. Não copiar seu conteúdo para issues.

## Continuação e retorno

Com OPEN, agendamentos, perfil básico e correções de conversa confirmados,
validar vínculo, presença e GPS com um participante motorista. O
[ensaio de passageiro](cadastro-passageiro-sem-motorista.md) registra 15 respostas
entregues acumuladas e a repetição das quatro interações após a atualização. Registrar estados e contagens sem guardar
a conversa na evidência pública. A inclusão dos colegas exige confirmação dos contatos e
admissão na mesma homologação, sem compartilhar credenciais administrativas.

Se necessário suspender o canal, despublicar somente os quatro agendamentos
deste namespace, desligar o webhook da instância dedicada e remover apenas os
serviços da stack do canal. Revogar as credenciais técnicas da rodada pelo
operador. Preservar volumes e recibos para diagnóstico. Não reiniciar n8n,
excluir dados de outros projetos ou alterar as stacks da API e do APK.
