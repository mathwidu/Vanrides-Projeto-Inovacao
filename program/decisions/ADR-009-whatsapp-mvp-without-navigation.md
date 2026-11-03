# ADR-009 — MVP sem integração de navegação externa

- status: `accepted`
- data: 02/11/2026
- responsáveis: proprietário, pelo pedido nesta conversa; executor, pelo registro
- cards relacionados: VR-WA-060, VR-WA-110, VR-WA-130, VR-WA-180, VR-WA-190

## Contexto

A coleta do aplicativo já envia posições diretamente à API: Google Fused Location
no Android e Core Location no iPhone. A documentação ainda oferecia integração
opcional com Maps/Waze, gerando dúvida sobre o papel do app. O proprietário
pediu retirar essa parte do MVP. A abertura de navegação não foi entregue nem
é necessária para captar GPS e avaliar aproximação.

## Decisão

Excluir desta primeira entrega a geração de links de rota, abertura programada
de Maps/Waze e preparação de dados destinada exclusivamente à navegação externa.
No backlog acadêmico, T76/T77 saem do conjunto executável; registros completos
ficam no histórico, sem renumerar os demais IDs e sem contabilizar conclusão.

Manter o link WhatsApp → aplicativo VanRides para iniciar a coleta. Manter
Google Fused Location/Core Location, os pontos de embarque, seus vínculos,
a ordem usada pela rotina e os cálculos necessários ao acompanhamento e aviso
de aproximação. Esses componentes não são integração com Google Maps.

O motorista pode usar aplicativos de sua escolha por conta própria; o MVP não
fornece nem depende dessa navegação. A eventual integração posterior exige nova
decisão de escopo. Não executar remoção de dependências nativas ou código legado
por simples semelhança dos nomes Google/Maps.

## Alternativas consideradas

Manter a integração como opcional ampliaria o que precisa ser implementado e
explicado, sem ajudar a provar o aviso. Remover também a ordem dos pontos ou o
serviço de localização quebraria requisitos que permanecem centrais. Ambas as
alternativas foram descartadas para este ajuste documental.

## Consequências

O MVP concentra cadastro, presença, coleta, consulta autorizada e aproximação,
com encerramento e recuperação. O backlog acadêmico passa a 65 tarefas e três
propostas de escopo restantes. Não muda o estado dos 20 cards técnicos, seus
critérios de GPS/percurso ou os HOLDs. Testes físicos iOS continuam pausados.
Os testes de bateria e continuidade permanecem; navegação externa deixa de ser
um cenário obrigatório de aceitação desta entrega.

Nenhuma mudança de runtime, infraestrutura ou dependência foi executada. A
implementação corrente da coleta e os dados necessários às regras permanecem.
Documentos históricos preservam as alternativas estudadas e devem ser lidos
com este ajuste de escopo.

## Validade e revisão

Válida para a primeira entrega do MVP WhatsApp. Revisar somente se a equipe
propuser integração de navegação como nova funcionalidade. Reverter esta decisão
exige reavaliar tarefas e critérios; não restaurar automaticamente IDs ao plano
nem reescrever os registros históricos como implementação concluída.
