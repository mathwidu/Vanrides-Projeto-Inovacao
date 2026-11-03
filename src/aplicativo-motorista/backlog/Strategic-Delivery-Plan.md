# Strategic Delivery Plan — VanRides App

## Objetivo
Manter uma leitura clara do que já virou produto validável no app e do que ainda falta para o VanRides se comportar como operação real, sem depender de memória oral da equipe.

Conceito importante:
- o backlog de cards continua sendo a fonte de rastreabilidade
- este documento define a estratégia de priorização do frontend
- quando a prioridade oficial mudar, este documento deve ser revisado antes de mover a fila das issues

## Estado atual do produto

### O que já existe como produto validável
- login, cadastro e verificação de e-mail
- experiência motorista, passageiro e responsável com leitura da viagem do dia
- shell `map-first` já iniciado
- rota de ruas, `minha parada` e consumo de realtime por viagem já existem
- lifecycle operacional já está parcialmente consumido pelo app do motorista

### O que ainda gera sensação de produto incompleto
- a empresa ainda não prepara a operação do dia em um fluxo oficial simples dentro do produto
- o bloco `map-first` da corrida existe em partes, mas ainda não foi consolidado como jornada final
- parte do QA manual e da bateria E2E ainda está aberta

## Leitura honesta da experiência atual

### MVP atual da corrida
Hoje já existe um MVP bom da experiência da corrida:
- mapa real
- acompanhamento do dia
- destaque de `minha parada`
- realtime por viagem
- ações operacionais principais do motorista

Mas ainda não existe o fechamento completo da experiência:
- preparação da corrida pela empresa ainda não está confortável no fluxo oficial
- a UI final de `planejamento da corrida` e `corrida em andamento` ainda pede consolidação
- notificações operacionais da corrida ainda não estão fechadas

## Próximo foco oficial recomendado

### Fase S1 — Operação administrativa ponta a ponta no portal
Objetivo:
- a empresa conseguir preparar rota e viagem sem depender de backend manual

Leitura estratégica:
- este é o maior gargalo de produto hoje
- sem isso, o restante da experiência da corrida continua parecendo “demo preparada”

Frente principal:
- frontend/web portal

Escopo recomendado:
- gestão de `rota-base`
- criação e leitura de `viagem do dia`
- atribuição/validação de motorista e passageiros no tenant correto
- smoke real com empresa preparando a operação e depois motorista/passageiro consumindo o resultado

Gap mapeado em `2026-04-14`:
- o portal empresa já possui fluxo real para `motoristas`, `passageiros`, `responsaveis` e `vinculos`
- o portal empresa ainda não possui telas, navegação, services ou formulários para `rotas-base` e `viagens`
- o backend já suporta o fluxo administrativo mínimo com:
  - `GET/POST /company-admins/rotas-base`
  - `GET/POST /company-admins/viagens`
- o backend atual ainda não oferece um campo explícito de `destino final` na `rota-base`
- hoje, a navegação da viagem calcula o destino final usando a última parada da lista, o que funciona para um MVP técnico, mas não atende bem o caso de produto em que a empresa quer definir um destino operacional próprio para o motorista

Decisão recomendada para S1:
1. abrir primeiro um pequeno ajuste de contrato no backend para suportar `destino final` explícito na `rota-base`
2. depois implementar no portal as telas de `Rotas` e `Viagens`
3. validar ponta a ponta: empresa cria `rota-base` com destino final, cria `viagem`, motorista inicia corrida e recebe a navegação calculada com esse destino

Recorte recomendado de implementação:
- `S1.1` contrato backend de `rota-base` com destino final
- `S1.2` `RotasListScreen` + `RotaFormScreen` + `RotaDetalheScreen`
- `S1.3` `ViagensListScreen` + `ViagemCreateScreen`
- `S1.4` smoke administrativo + operacional do fluxo completo

Critério para considerar S1 concluída:
- um usuário empresa consegue preparar o dia sem intervenção manual do mantenedor

### Fase S2 — Fechar a experiência `map-first` da corrida
Objetivo:
- transformar o que hoje já existe em partes em uma jornada final e estável

Escopo recomendado:
- consolidar `0132` a `0136`
- revisar a tela de `planejamento da corrida`
- lapidar a `corrida em andamento`
- consolidar `minha parada` e estados do passageiro/responsável
- fechar QA manual e integration tests relevantes

### Fase S3 — Notificações operacionais e engajamento
Somente depois de S1 e S2:
- notificações operacionais da corrida
- push geral
- chat
- suporte
- billing

## Regras de decisão daqui para frente

1. Não tratar refinamento visual como prioridade máxima quando o fluxo oficial do produto ainda exige passo manual fora da UI.
2. Diferenciar sempre:
   - UI já implementada
   - fluxo validado localmente
   - fluxo validado com backend publicado
   - fluxo pronto para usuário final
3. Quando uma feature já existe “em partes”, a prioridade deve ser consolidação ou reclassificação, e não recriação do zero.
4. O próximo bloco deve ser escolhido pelo maior gargalo de produto, não pelo card mais bonito de implementar.

## Próxima recomendação objetiva

### Escolha recomendada
Seguir primeiro com a operação administrativa ponta a ponta no portal.

Por quê:
- destrava o uso real da corrida
- reduz a dependência de seed manual/backend manual
- dá contexto real para o QA do motorista, passageiro e responsável
- transforma a experiência atual de corrida em algo repetível no dia a dia

### Depois disso
Voltar para a consolidação do bloco `map-first` com menos incerteza e menos retrabalho.

### O que não abrir agora
- billing
- chat
- turn-by-turn nativo
- mais um grande redesign visual sem fechar primeiro o fluxo operacional
