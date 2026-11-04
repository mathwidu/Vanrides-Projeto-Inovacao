# Cadastro de passageiro sem motorista — homologação

Ensaio de 04/11/2026, autorizado pelo proprietário nas issues #28, #29 e #91.
Cliente: WhatsApp do Mac, usando o contato pessoal já admitido. Bot: WhatsApp
Business no Redmi, conectado à Evolution dedicada. A rodada inicial usou o
backend `57a2ffd`; a repetição após a atualização aprovada usa `acc572f`.
O receptor usa a correção `fc4dea9`. Modelo pago desligado;
testes físicos do aplicativo iPhone continuam pausados.

## Resultado inicial observado

| Etapa | Resultado no WhatsApp e na API |
| --- | --- |
| Nome | Resposta confirmou atualização e apresentou o aviso de uso dos dados. Nome presente no perfil. |
| ACEITO após o aviso | Resposta confirmou o aceite. Perfil PASSENGER/ACTIVE, aviso wa-mvp-v1 e data de aceite presentes. |
| AJUDA | Respondeu, mas usou orientação antiga sobre funções futuras; melhoria preparada. |
| CONTINUAR CADASTRO sem convite | Recusou a proposta sem criar vínculo ou ponto. Texto misturou opções de motorista; melhoria preparada. |
| VOU HOJE sem rotina | Informou ausência de ocorrência. Nenhuma presença foi criada. |
| ONDE ESTÁ A VAN sem vínculo | Informou localização indisponível; não expôs coordenadas. |
| SOU MOTORISTA dentro do menu de presença | Reapresentou ausência de ocorrência; revelou que o menu vazio continuava capturando a conversa. |
| ABANDONAR | Encerrou a seleção de presença, preservando o cadastro. |
| SOU MOTORISTA fora do menu | Informou necessidade de convite autorizado. Papel e habilitação de motorista permaneceram inalterados. |

Conferência final desta rodada: conversa READY, owner READY, sem attendanceDraft.
Perfil com nome e aceite presentes, `driver_enabled=false`. Zero vínculos, pontos,
participações em rotinas e presenças para esse passageiro. O canal acumulava
11 respostas DELIVERED, com 11 tentativas DELIVERED e sem código de erro.
Essas contagens incluem as duas respostas da rodada inicial do canal.

O primeiro envio de nome foi interrompido por uso simultâneo da janela; a API
confirmou que o nome ainda não estava salvo. Após o proprietário liberar a
janela, a automação conferiu destinatário e rascunho antes de enviar. A rodada
concluída foi validada pelo cliente e por consultas limitadas aos estados, sem
copiar conversa, número, coordenadas ou credenciais para esta evidência.

## Limite e continuação

Este resultado comprova o perfil básico e as recusas sem motorista. Não conclui
os critérios das issues #28/#29 sobre dias, ponto, convite e aprovação. Essas
etapas exigem um motorista habilitado com rotina e convite válidos. O próximo
participante precisa autorizar seu próprio contato; não converter o passageiro
pessoal em motorista para contornar essa dependência.

O perfil básico pode ser reaproveitado no convite. A conversa posterior pede
preferências de avisos, rotinas, dias, pin estático, referência e confirmação.
O motorista correto aprova o vínculo e o ponto. Não criar uma empresa nem usar
localização contínua do passageiro para essa etapa.

## Correções publicadas e repetidas no WhatsApp

A orientação de ajuda/retomada agora explica que o perfil está pronto e que é
necessário pedir e enviar o convite do motorista. Ela preserva recusas e não
cria vínculo automaticamente. A consulta de presença sem ocorrências agora
encerra sua seleção vazia, permitindo outras intenções em seguida.

A tabela inicial preserva o comportamento anterior. A repetição abaixo registra
o resultado observado depois da atualização, sem refazer o cadastro.

Validação local da correção `acc572f`: 26 testes passaram, zero falhas, erros ou
skips, em 59 segundos. Foram 19 testes unitários e 7 com PostgreSQL em contêiner
isolado. Os cenários de interrupção agora usam ocorrências elegíveis para testar
um menu com opções; o menu vazio é coberto separadamente. O teste de regressão
unitário do menu vazio falhou antes da alteração e passou depois. A revisão
independente conferiu preservação de autorização e de drafts anteriores.

O JAR foi empacotado com JDK17 e conferido dentro da imagem linux/amd64 preparada.
A receita de atualização conserva os configs, banco, secrets e serviços do canal;
somente a imagem da API e seu hash de JAR foram substituídos após autorização.
A suíte canônica completa e a CI não foram repetidas nesta etapa. A publicação e a repetição real foram concluídas em 04/11/2026; o perfil
já concluído foi preservado.


| Repetição na API atualizada | Resultado |
| --- | --- |
| AJUDA | Orientou pedir e enviar convite do motorista; explicou a revisão e aprovação do vínculo. |
| CONTINUAR CADASTRO | Apresentou a mesma orientação, sem criar vínculo automaticamente. |
| VOU HOJE sem rotina | Informou ausência de ocorrência e devolveu a conversa a READY, sem attendanceDraft. |
| SOU MOTORISTA imediatamente depois | Exigiu convite autorizado, sem precisar enviar ABANDONAR e sem alterar o papel. |

As quatro respostas foram vistas no WhatsApp do Mac. O banco confirmou 15
respostas e 15 tentativas DELIVERED acumuladas, sem código de erro (11 anteriores
mais quatro desta repetição). Perfil PASSENGER/ACTIVE, nome e aceite preservados,
`driver_enabled=false`, conversa READY. Continuam zerados os vínculos, pontos,
participações em rotinas e presenças desse passageiro. As issues #28/#29 permanecem
abertas para os critérios que exigem motorista; este ensaio não comprova percurso.

A API convergiu para a imagem `4648c6c43401`, fonte `acc572f`, JAR SHA-256
`df9c642ba993af262406e03baf1939ca73d57e8a663cc5c728ce98beef3c3707`.
HTTPS retornou 200/UP; aparelho e eventos continuaram exigindo autenticação
(401). A comparação das especificações confirmou alteração somente da imagem
e do hash de JAR da API. Os outros oito serviços da homologação mantiveram
especificações e versões. Houve 502 transitório durante a inicialização da
réplica única, seguido de recuperação; não foi necessário retorno à imagem anterior.
O push até `639d70d` foi concluído; não houve execução automática de CI nessa branch.

O ponteiro privado `homologacao/current-api.json` na VPS indica o manifesto atual
`homologacao/passenger-acc572f/online.json`. O manifesto `online-57a2ffd-r2`
permanece como histórico de retorno. Operações administrativas futuras devem
usar o ponteiro atual; o auxiliar antigo do canal mantém o caminho anterior e
não deve ser repetido sem atualizar sua entrada e verificar a imagem esperada.
Recibos e cópia da especificação anterior permanecem privados, fora do Git.
