# Cadastro de passageiro sem motorista — homologação

Ensaio de 04/11/2026, autorizado pelo proprietário nas issues #28, #29 e #91.
Cliente: WhatsApp do Mac, usando o contato pessoal já admitido. Bot: WhatsApp
Business no Redmi, conectado à Evolution dedicada. A API publicada nesta rodada
usa o backend `57a2ffd`; o receptor usa a correção `fc4dea9`. Modelo pago desligado;
testes físicos do aplicativo iPhone continuam pausados.

## Resultado observado

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

## Correções preparadas

A orientação de ajuda/retomada deve explicar que o perfil está pronto e que é
necessário pedir e enviar o convite do motorista. Ela preserva recusas e não
cria vínculo automaticamente. A consulta de presença sem ocorrências deve
encerrar sua seleção vazia, permitindo outras intenções em seguida.

A prova acima é da versão já publicada. Testes locais e a publicação das
correções devem ser registrados separadamente; não apresentar a melhoria como
validada no WhatsApp antes da nova instalação e repetição.
