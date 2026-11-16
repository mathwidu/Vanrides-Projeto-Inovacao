# Privacidade e recuperação do MVP

Estado em 16/11/2026: arquitetura técnica escolhida para concluir #83 e #84.
Este documento não atesta exclusão nos fornecedores nem aprovação jurídica.
O aviso deve refletir os prazos antes da ativação com pessoas reais.

## Regras de armazenamento

| Família | Prazo e destino |
| --- | --- |
| Posição atual e três amostras de aproximação | Remover ao encerrar, parar, revogar ou expirar a coleta. |
| Recibos GPS sem coordenadas | Sete dias em sessão encerrada, mantendo sequência e geração contra replay. |
| Conversa, rascunhos e conteúdo transitório de mensagens | Sete dias de inatividade humana; preservar pausa, chaves e controle necessário. |
| Perfil, alias, ponto e suas cópias | Enquanto ativos; descarte em até 30 dias do fechamento registrado. |
| Presença, decisões, auditoria e suporte encerrado | Noventa dias do fato/encerramento; ocorrências futuras não vencem pela data de criação. |
| Credenciais, provas, pareamentos e intents | Revogar no fechamento; descartar após validade sem perder proteção de replay e parada. |
| Backups próprios do MVP | Sete dias, com inventário e exclusões reaplicadas antes de restaurar o uso. |

Os prazos vêm da baseline do MVP. O enquadramento do conteúdo transitório em
sete dias e do suporte encerrado em 90 dias é a definição técnica desta entrega.
Metadados mínimos que ainda impedem replay ou sustentam uma referência válida
permanecem como registros sem conteúdo pessoal; isso deve ser distinguido de
exclusão física completa.

## Descarte sem alterar a história de outros passageiros

Excluir a identidade inteira quebra referências entre presença, embarque e
viagem. Apagar apenas o nome ou o ponto também deixa cópias nos snapshots e
nas mensagens. A V44 terá ciclo explícito de fechamento/descarte, registro de
exclusões e projeção terminal restrita dos campos pessoais.

A escolha é conservar UUIDs, donos, ordem e fatos operacionais necessários,
removendo nome, contato roteável, coordenadas e referências da pessoa fechada.
Campos apagados usam ausência explícita, sem coordenadas fictícias. Os guards
continuam rejeitando alterações operacionais: somente uma transição monotônica
de descarte, autorizada pelo registro de exclusão e no estado terminal, pode
remover os campos definidos. Não haverá desativação de triggers nem um bypass
genérico. Fechar vínculo, parar GPS e pausar suporte não fecham o perfil.

O job de retenção usa credencial SYSTEM_JOB, relógio do servidor e lote máximo
100. Cada família informa quantidade examinada, apagada e adiada. Uma referência
ativa ou falha de journal deixa pendência explícita. A manutenção continua
possível quando novos inícios ou envios estão pausados.

## Cópias e restauração

O registro de exclusões precisa de journal privado fora do banco restaurado.
A intenção é registrada primeiro; o descarte só é confirmado após sua cópia
durável no journal. Restaurar um dump antigo exige o journal atual, com sequência
e integridade verificadas, e a reaplicação idempotente das exclusões antes de
liberar a operação. Journal ausente, incompleto ou adulterado bloqueia o retorno.

O ensaio usa schema real, API, worker, workflows/configuração próprios do n8n
e canal simulado, com versões identificadas e sem saída para contatos reais.
O retorno começa com coleta/intents/tokens e conexão de envio desabilitados;
a reabilitação é uma etapa controlada e testada. O backup simplificado anterior
continua histórico e não comprova recuperação desse conjunto.

## Prova mínima

Criar uma viagem encerrada com dois passageiros e suas mensagens/snapshots.
Fechar somente um e avançar 31 dias. Conferir que seus campos pessoais somem
em todas as cópias, que o outro passageiro permanece intacto, e que presença,
chaves e guards continuam válidos. Repetir a limpeza sem novos efeitos.

Restaurar uma cópia anterior ao descarte em banco separado, reaplicar o journal
atual e repetir as verificações. Webhook, token e submit antigos não podem
recuperar dados nem produzir novos efeitos. A evidência deverá registrar também
as famílias ainda adiadas e os limites dos serviços externos.

A implementação, revisão, ativação e resultados são registrados separadamente
na evidência da entrega; esta descrição não equivale a teste executado.
