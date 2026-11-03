# ADR-007 — Desenvolvimento com prioridade Android durante a pausa física iOS

- Status: aceita para sequência de desenvolvimento local.
- Data: 31/10/2026.
- Responsável: executor principal, conforme pedido explícito do proprietário
  para pausar os testes no iPhone físico, avançar no Android e preparar o iPhone.
- Cards: VR-WA-030, 040, 160 e 180.

## Contexto

030 comprova o coletor, com aceites separados por plataforma. O proprietário
pausou os testes no iPhone físico. No Android, o candidato D07 já tem evidências
parciais de continuidade após Home, recuperação de rede, expiração offline e
encerramento forçado. O bloqueio estacionado foi validado depois em D07;
aquisição inicial consistente, percurso, aproximação, destino e energia ainda
precisam de seus próprios resultados e aceites.

040 constrói contratos, persistência e autorização no backend. Esse trabalho
pode usar fixtures sintéticas e o contrato estabelecido em020. Não precisa
de uma posição real do iPhone para validar RBAC, idempotência ou constraints.
A dependência anterior040→030 impedia também esse desenvolvimento local.

## Decisão

1. Respeitar a pausa física iOS: nenhuma instalação, abertura ou teste no
   aparelho durante a pausa. Revisão, testes host e preparação podem continuar.
2. Encerrar primeiro os ensaios Android autônomos em curso, com coleta parada,
   evidência e pendências explícitas. Enquanto houver trabalho executável nessa
   frente,030 continua ativo. Quando depender de participação física do
   proprietário, passa para `waiting_human`, sem declarar M1 aprovado.
3. A dependência técnica de040 passa a ser020. Sua entrada em WIP ainda exige
   DoR, Impact Analysis atual, árvore limpa no backend e gates próprios.
   Apenas um card continua `in_progress`/`validating`; não implementar040 em
   paralelo com030.
4. Acrescentar030 como dependência explícita de160 e180. A preparação da
   release integrada e os testes finais continuam exigindo M1 bilateral.
   Os demais sucessores mantêm suas dependências técnicas.
5. Preservar todos os critérios físicos, os resultados negativos e os HOLDs
   de piloto/produção. Não há aceite de risco, waiver ou autorização para uma
   nova publicação externa nesta decisão.

## Alternativas consideradas

- Manter toda a implementação aguardando o iPhone: impediria trabalho local
  independente solicitado pelo proprietário.
- Concluir030 apenas pelo Android: invalidaria o aceite bilateral; rejeitado.
- Implementar dois cards simultaneamente: desnecessário e contrário ao WIP.
- Remover030 do caminho de release: permitiria preparar entrega sem M1;
  rejeitado. As novas arestas160/180 preservam essa barreira no controlador.

## Consequências

O domínio e o canal podem avançar sem coleta iOS ativa. Continua possível
retrabalho em pareamento, armazenamento seguro, lease e contratos de
telemetria quando a prova física for retomada. Mitigar com adapters separados,
fixtures de contrato e validação de produtores/consumidores antes de integrar.
Nenhum teste de DTO, banco ou Android serve como prova física iOS.

Migrations continuam aditivas e alocadas no card em execução, após conferir a
última versão. Nada desta decisão altera dados ou serviços na VPS. Em caso de
reversão desta decisão antes da primeira ativação de040, restaurar040→030. Se040 já
tiver sido executado, essa aresta isolada não bloqueia seus sucessores: nesse
caso, vincular030 ao primeiro sucessor ainda não iniciado e registrar a espera
explicitamente, preservando resultados e commits. Não apagar evidências nem
remover gates finais.

## Validade e revisão

Válida enquanto a pausa/participação física estiver pendente e para a primeira
entrega controlada do MVP. Revisar ao retomar030, antes160 e se algum ensaio
mostrar inviabilidade do coletor. Uma falha relevante mantém a entrega HOLD.
O controlador, a revisão independente e os registros dos cards devem confirmar
ausência de ciclos, um WIP e permanência do gate bilateral.

## Pareamento e coleta após a conclusão de 080

Em 31/10/2026, o 040 já produz pareamento e sessão, enquanto o 110 produz
a família de endpoints para execução e coleta. Para permitir a implementação
Android e a preparação iOS solicitadas, o 060 consome 040 e 080 e comprova a
sessão, link de retorno, estado de preparo e integração com a inbox. O 110
continua dependente de 060 e 100. Nesse ponto o companion passa a usar o
fluxo do percurso real. Os critérios físicos de percurso, bateria, segundo
plano, retomada, permissão e fim continuam obrigatórios em 030/180. O ambiente
170 testa as associações publicadas e mensagens no número autorizado. Os gates
160 e 180 continuam dependentes de 030. Nenhum desses gates foi dispensado.
