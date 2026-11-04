# Integração da equipe e conferência da homologação — 04/11/2026

O pedido foi conferir o que está hospedado, explicar o fluxo e os acessos do grupo,
incorporar as melhorias de cadastro já integradas à main e atualizar os guias.
Esta rodada não alterou a VPS, os contatos autorizados nem os aparelhos.

## Código e preservação do trabalho

- Base da homologação: `c44f916de7a117ab9ae0ec50ead00189c6209406`.
- Main consultada: `0393030`, merge do PR #105, com as mudanças do colega sobre
  SIM/NÃO, aviso de resposta não compreendida e testes de cadastro.
- Integração local: `5a2563cb3bd184c539e5c8dbcc1b29d75a6ae69b`, na branch
  `codex/integracao-equipe-homologacao`. Merge com os dois pais preservados,
  sem rebase, force-push ou seleção integral de um lado.
- Único conflito textual: total da matriz PostgreSQL. A homologação tinha 581;
  o colega acrescentou três testes à classe de cadastro, de 9 para 12. O total
  combinado é **584 em 75 classes**. A matriz completa não foi executada nesta rodada.
- `WaPassengerSetup` conserva o retorno `Step`/aviso de resposta inválida do
  colega e a orientação para passageiro sem vínculo já publicada. O menu vazio
  de presença continua liberando a conversa. Revisão independente sem defeito
  semântico confirmado na integração.
- PR #106 permanece separado: dias nomeados e novas opções ainda estão em revisão.
  PR #104 contém parte das mudanças agora reunidas nesta branch; não foi fechado
  nem integrado automaticamente. A revisão do grupo deve considerar essa sobreposição.

## Verificação da integração local

| Verificação | Resultado | Limite |
| --- | --- | --- |
| Java: Wizard, Help, ConversationPolicy e EmptySelection | 20 testes, sem falhas/erros/skips | Comportamento unitário |
| PostgreSQL: Wizard, Business, Fencing, Isolation, Ownership e Snapshots | 42 testes, sem falhas/erros/skips | Banco descartável local, não a VPS |
| Configuração do gate (`ci_quality_gate_test.py`) | 43 testes aprovados | Confere a matriz e o encadeamento, não executa os 584 testes |
| Agente Node: deterministic e passenger-setup | 105 testes aprovados | Sem WhatsApp, modelo pago ou usuário real |
| Programa e perfil do workspace | 59 + 16 testes aprovados | Controle organizacional, sem validação de runtime |
| Documentos, backlog e escopo | Checks aprovados; 7 avisos conhecidos de baseline/branch e mapas adiados | Nenhum card ou critério de piloto encerrado |

Os 62 testes Java foram executados em duas rodadas (60 + 2), no código que formou
o merge `5a2563c`, com Java 17, lock do Maven, um fork e limite de heap de 512 MiB.
Nenhum aplicativo pessoal foi fechado. A suíte canônica completa e as seis
jornadas HTTP deverão ser avaliadas pela CI do PR; estes testes direcionados
não substituem esse resultado. Logs locais não são anexados ao Git.

## Conferência do ambiente já publicado

| Item observado novamente | Resultado |
| --- | --- |
| API | HTTPS 200/UP, fonte `acc572f`, imagem `4648c6c43401` |
| Endpoint do aparelho sem credencial | 401 |
| APK | HTTPS 200, 68.903.599 bytes, SHA-256 `c438bb3566f87d3ca836096c2d962963ae5de12edbd6d2c2c67104ff9e0210a9` |
| Swarm da homologação | Nove serviços com 1/1 réplica |
| Worker | Saúde privada HTTP 200 |
| Evolution | Instância dedicada OPEN |
| n8n | W01/W04/W06/W08 e W07 publicados; agendas com success nos 15 minutos consultados |
| Subfluxos n8n | W02/W03/W05 sem publicação/agendamento próprios |
| Contatos admitidos no receptor | Um, passageiro; janela aberta até 01/12/2026 00:00 UTC |
| Avisos de aproximação | `WA_APPROACH_ENABLED=false` |

A rodada anterior comprovou 15 respostas entregues acumuladas no WhatsApp e
perfil básico preservado. Esta conferência foi de leitura: não enviou novas
mensagens nem atribui aquela prova à integração `5a2563c`.

## Entrega para o grupo e retorno

O [guia da equipe](../../desenvolvimento/homologacao-da-equipe.md) reúne URLs,
admissão individual, roteiros de passageiro e motorista, fluxo entre serviços,
limites, diagnóstico e a diferença entre código no Git e binário hospedado.
README, CONTRIBUTING, arquitetura, resultados e plano foram alinhados a ele.
O escopo documental foi registrado no card ativo sem fechar critérios de
motorista, percurso, iPhone ou piloto.

A publicação proposta é somente push desta branch e PR para main, com CI.
Sem merge, deploy, alteração de contatos, secrets, banco, n8n ou Evolution.
Para desfazer a integração antes do merge remoto, manter as branches originais
e abandonar a branch/PR de integração; não reescrever a main nem o trabalho do colega.
A API permanece na imagem publicada até uma atualização separada e autorizada.


## Primeira CI e ajustes dos ensaios

PR #107 aberto em `2a96424`, sem conflitos com a main. Documentação, programa e
WhatsApp passaram. Dois ensaios apontaram expectativas desatualizadas; a VPS
permaneceu inalterada:

- O auxiliar de homologação emitia `+00:00`; o parser do operador exige `Z`.
  Corrigido somente o formato do auxiliar, com dois testes que falharam antes e
  passaram depois (5/5 no arquivo). O ensaio real do JAR com PostgreSQL
  descartável passou em `055a1d9`, incluindo duas credenciais, isolamento,
  revogação, replay e persistência após reinício. Limpeza concluída.
- A jornada de conversa ainda esperava a ajuda genérica anterior à correção
  `acc572f`. Atualizado somente o literal esperado para passageiro ativo sem
  vínculo; mantidas igualdade exata, unicidade, autorização e checks posteriores.
  O cenário de texto desconhecido já passara na CI. Suporte, envio e reinício
  posteriores precisam da reexecução completa, não são aprovados pelo ajuste textual.

As correções de teste não alteram runtime, contratos da API ou comportamento
publicado. A segunda execução da CI deve ser registrada no PR antes do aceite.
