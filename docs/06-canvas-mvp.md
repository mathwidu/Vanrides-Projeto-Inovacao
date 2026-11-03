# Canvas MVP v2 proposto para discussão

**Estado:** proposta local para revisão em 03/11/2026; não aplicada ao Miro.
Preserva F09/F10/F02 na Onda 1 e F05/F11 na Onda 2 do sequenciador observado.
O template do board menciona sete blocos, mas a O4 exige nove; os nove estão
organizados abaixo. O retorno deve confirmar com o professor como apresentar
as duas ondas do MVP completo sem exceder três cartões no bloco da Onda 1.

## 1 Proposta do MVP

Organizar pelo WhatsApp o cadastro e a presença de estudantes de um transporte
universitário recorrente, evoluindo para avisos de aproximação baseados no GPS
do motorista, para reduzir a necessidade de perguntar onde a van está.

## 2 Personas

Passageiro universitário que utiliza transporte fretado e confirma a própria
presença; motorista que atende um grupo recorrente, com horários e pontos
habituais. O piloto começa com um motorista. Jorge Silva e Luciano Ferreira
são personas do Miro, não participantes de pesquisa comprovados por este pacote.

## 3 Jornadas

Passageiro: receber convite, cadastrar nome e ponto de embarque, obter aprovação,
informar presença/ausência e consultar sua situação. Motorista: cadastrar-se,
definir rotina, convidar e aprovar passageiros, consultar a lista do dia.

Na segunda onda: motorista inicia a saída pelo WhatsApp, abre o link de ativação
do aplicativo do motorista e permite a coleta; o sistema confirma início após GPS válido.
Passageiro recebe aviso de aproximação. A finalização encerra a coleta, com
contingência explícita em caso de falha. Essa segunda jornada ainda requer
implementação e validação completas.

## 4 Funcionalidades

Três cartões da primeira onda:

1. **F09 Cadastrar motorista:** identificar motorista e organizar sua rotina.
2. **F10 Cadastrar passageiro:** vincular passageiro e confirmar ponto de embarque.
3. **F02 Confirmar presença:** registrar a resposta por dia e sentido e mostrar
   os habituais sem resposta como pendentes.

Segunda onda do sequenciador: **F05 acompanhar localização do motorista** e
**F11 avisar aproximação**. Esses cartões são necessários para o resultado de
aproximação do MVP completo; não foram agrupados artificialmente na Onda 1.
Cancelamento/correção, recuperação e encerramento no plano técnico devem ser
reconciliados no sequenciador com a equipe e comunicados ao professor. A
navegação externa saiu do recorte do MVP em 02/11/2026; a ordem dos pontos
necessária ao acompanhamento e aos avisos permanece.

## 5 Resultado esperado

Na primeira onda, manter a lista do dia compreensível e corrigível pelo próprio
passageiro. No MVP completo, avisar a aproximação sem exigir que o motorista
envie sua posição manualmente a cada parada. Redução de espera e de mensagens
é um resultado a medir; ainda não foi demonstrado com usuários reais.

## 6 Métricas

**Proposta de protocolo para aprovação, sem resultados coletados:**

| Indicador | Definição e alvo inicial | Como registrar |
|---|---|---|
| Cadastro autônomo | Pelo menos 80% dos passageiros convidados concluem cadastro e ponto sem assistência em até 5 minutos | Medir início/fim e pedidos de ajuda, usando identificadores de teste |
| Presença compreendida | Pelo menos 90% das respostas de teste resultam no estado esperado; 100% das ambiguidades de risco pedem confirmação | Cenários com data/sentido, confirmação da pessoa e recibo técnico |
| Lista correta | Nenhuma exclusão automática de passageiro habitual por silêncio | Comparar lista esperada com snapshot antes da saída |
| Aproximação útil | Meta provisória de 90% dos avisos elegíveis recebidos antes da parada; zero avisos duplicados por saída/ponto/pessoa | Observador registra chegada e entrega; GPS desatualizado não conta como alerta válido |
| Necessidade de contato manual | Comparar quantidade de perguntas sobre localização por passageiro/saída antes e durante o piloto | Medição consentida e agregada, sem copiar conversas pessoais |

Sugestão exploratória: um motorista, cinco passageiros voluntários, três
percursos e pelo menos vinte oportunidades de aproximação por plataforma,
compatibilizando a amostra com os critérios técnicos. Não se promete significância
estatística. Não respostas, falhas, exclusões e denominadores serão registrados;
a aprovação técnica do rastreamento antecede o piloto de produto.

## 7 Custo e cronograma

A equipe já possui VPS, n8n e Evolution configurados. Custos incrementais
incluem linha dedicada, consumo da API de IA e eventual serviço de rotas.
A linha dedicada já está ativa no WhatsApp Business do Redmi. O pareamento e a entrega real aguardam ensaio.
O custo da linha não representa o custo total do projeto.
Limites mensais, custos existentes e horas por integrante precisam ser fechados.

Em 03/11: revisar este pacote e o recorte com o professor. Em seguida, confirmar
capacidade da equipe, atualizar os artefatos acadêmicos e preparar o ensaio do bot.
Depois: concluir canal real, aproximação/fim, percurso/bateria nas duas
plataformas, ambiente integrado e piloto. Datas de entrega serão acordadas após
esses recursos e estimativas; não há uma data final publicada no cronograma
consultado que possa ser presumida. Este bloco continua pendente de orçamento
e compromisso de prazo da equipe para ser considerado final.

## 8 Hipótese central

Estudantes e motorista conseguem organizar cadastro e presença pelo WhatsApp
com pouca assistência; com coleta automática confiável e avisos oportunos,
reduzem a necessidade de contato manual para saber onde a van está. A primeira
parte será refutada se as metas de cadastro/presença não forem atingidas; a
segunda não poderá ser considerada validada se o GPS ou a entrega falharem,
ou se não houver redução observada de contato manual em relação à base medida.

## 9 Aprendizado esperado

Identificar onde o cadastro conversacional gera dúvidas, se a regra de presença
pendente corresponde ao uso real, quanto esforço o aplicativo do motorista impõe ao motorista
e se avisos baseados em localização confiável são úteis no embarque. Registrar
o que funcionou, o que falhou e quais mudanças foram motivadas pelos dados.

## Changelog v2 proposto

Estes itens resultam das decisões do produto e da implementação atual. Não são
atribuídos ao professor nem substituem as notas de feedback da P4.

1. Blocos 1 e 3: adotar WhatsApp como interface cotidiana e aplicativo do motorista mínimo do
   motorista para coleta de GPS.
2. Blocos 2 e 4: explicitar piloto com passageiro próprio, motorista e rotina
   recorrente; manter F09/F10/F02 na primeira onda e F05/F11 na segunda.
3. Blocos 5 e 6: distinguir resultado esperado de teste técnico e propor métricas
   mensuráveis, com denominadores e falhas registradas.
4. Bloco 7: registrar custo conhecido da linha como autorização, recursos já
   existentes e pendências de prazo/orçamento.
5. Blocos 8 e 9: explicitar hipótese contestável e aprendizado esperado.

## Feedback a recuperar

| Origem | Item | Tratamento | Evidência necessária |
|---|---|---|---|
| Professor/turma na P4 | Notas ainda não localizadas | Em aberto | Registrar fala ou anotação original, data e decisão da equipe |
| Equipe e implementação | GPS exige coletor próprio; WhatsApp permanece interface principal | Incorporar na proposta | testes de localização |
| Equipe e implementação | Regras não devem depender de interpretação livre do modelo | Incorporar na proposta | arquitetura e testes de autorização |
| Planejamento antigo | Email, empresa e localização contínua do passageiro | Fora do recorte atual; justificar revisão | Comparar sequenciador e BACKLOG histórico com escopo aprovado pelo proprietário |

Após revisão, atualizar no Miro o frame identificado como **Canvas MVP v2** e
posicionar o texto **Changelog v2** abaixo do bloco Custo e Cronograma, conforme
a O4. Preservar a versão anterior. A revisão local não substitui essa etapa.
