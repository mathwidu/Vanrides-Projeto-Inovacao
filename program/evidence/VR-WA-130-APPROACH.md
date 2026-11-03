# Localização e aproximação — implementação e validação

Data: 03/11/2026. Issues #33–36 e #75–77; preparação de transporte em #91.
Base centralizada: `187b3adbd65ed57783720fb3e16dce4ab6898bdc`.
Branch: `codex/mvp-localizacao-aproximacao`.

## Resultado implementado

Motorista dono e passageiro elegível podem consultar a posição atual pelo
WhatsApp. A API resolve o veículo a partir da identidade autenticada e revalida
o GPS antes de liberar um pin estático. Coordenadas não passam pelo modelo nem
ficam na outbox. A resposta sem GPS recente explica a indisponibilidade.

A aproximação considera três aquisições recentes, sentido, precisão, próximo
ponto e progressão monotônica. Passageiros do mesmo ponto são agrupados e
recebem avisos individuais, sem duplicação por mudança de versão da política.
A autorização é conferida novamente antes do envio. A funcionalidade permanece
desligada por padrão; rotinas precisam de revisão explícita. Os parâmetros são
hipóteses a calibrar em percurso, não uma comprovação de utilidade.

O transporte do pin usa o contrato `STATIC_PIN` e o endpoint `sendLocation` da
Evolution. O ensaio integrado inclui agora seis jornadas e preserva um recibo
do mesmo JAR. Um workflow manual prepara o binário e provas sanitizadas para
o futuro ensaio real; não publica serviço nem conecta contas.

## Verificações anteriores ao gate completo

- Testes focados do backend: 35 cenários PostgreSQL novos, 8 da política e
  135 do contrato passaram, conforme execução dos responsáveis pelo módulo.
- `python3 scripts/check_whatsapp.py`: 689 testes Node e 300 Python passaram;
  contratos e receptor GPS sintético incluídos. Nenhum contato real foi acionado.
- Programa: 59 testes; perfis: 16; backlog: 4 — todos passaram.
- `check_repository.py` passou: 808 referências de tarefas e 520 células do
  backlog coerentes. `git diff --check` passou.
- Revisão independente corrigiu consulta do motorista, ponto compartilhado
  fora de ordem, continuidade durante permanência, contenção entre inbox e GPS
  e resposta textual após expiração do rastreamento. Há regressões específicas.
- Revisão do pacote de evidência: 53 testes Python e 13 Node passaram; relação
  entre produtor e exportador conferida com XML sintético. GitHub real ainda
  não foi consultado para uma execução deste workflow.

O gate completo e as seis jornadas serão executados após fixar o commit limpo.
Resultados de testes focados não substituem essa comprovação integrada.

## Limites e próximos passos

Não há novo deploy, pareamento, callback real ou percurso nesta evidência.
O número do bot já está ativo no WhatsApp Business do Redmi; falta conectá-lo
ao ambiente preparado e comprovar o envio autorizado e seu recibo. Os testes
físicos do iPhone continuam pausados. Nenhuma issue foi encerrada.

A etapa de utilidade exige antecedência/cobertura e ausência de destinatário
incorreto em percursos reais. Geografia ambígua, como pisos sobrepostos, não é
resolvida por distância GPS; a rotina fica suprimida até tratamento adequado.
Ver [contrato do backend](../../src/backend/docs/whatsapp-approach.md) e
[preparação do transporte](../../src/whatsapp/lab/live-runtime/README.md).
