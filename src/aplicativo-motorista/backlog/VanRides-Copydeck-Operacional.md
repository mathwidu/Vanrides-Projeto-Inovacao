# VanRides Copydeck Operacional

## Rastreabilidade

- Card de origem: `UX-COPY-001`.
- Fonte para cards futuros que adicionarem copy primaria em telas, empty states,
  snackbars, dialogos e paineis operacionais.
- Objetivo: manter a linguagem humana, direta e orientada a decisao do usuario.

## Regras de copy primaria

- Falar sobre a operacao, nao sobre a implementacao.
- Preferir "viagem", "lista", "sinal", "rota", "avisos" e "empresa".
- Evitar na UI primaria: `backend`, `polling`, `fixture`, `websocket`,
  `Unsupported`, `Bad Request`, `Conflict`, `snapshot`, `manifesto` e
  "sem contexto".
- Quando uma falha tecnica chegar crua, traduzir para acao ou estado
  compreensivel antes de exibir.

## Passageiro

| Estado | Copy preferida |
| --- | --- |
| Rota publicada | `A rota de ruas calculada ja esta pronta para a viagem de hoje.` |
| Trajeto aproximado | `O trajeto aproximado de hoje ja esta pronto.` |
| Status desconhecido | `Ainda nao recebemos um status confiavel para esta viagem.` |
| Dados temporariamente indisponiveis | `Nao conseguimos atualizar os dados agora. Tente novamente em instantes.` |

## Motorista

| Estado | Copy preferida |
| --- | --- |
| Lista do dia | `Lista de hoje` |
| Lista operacional | `Lista operacional` |
| Resumo da lista | `$totalParadas paradas - $confirmadas confirmadas - $pendentes pendentes` |
| Sem sinal ativo | `Seu sinal ao vivo ainda nao esta ativo neste aparelho.` |

## Responsavel

| Estado | Copy preferida |
| --- | --- |
| Perfil sincronizado | `Perfil recebido` |
| Localizacao aguardando sinal | `Aguardando sinal da van` |
| Passageiro vinculado | `Passageiro vinculado` |
| Sem posicao recente | `Ainda nao ha posicao recente para acompanhar.` |

## Empresa

| Estado | Copy preferida |
| --- | --- |
| Remover responsavel com vinculos | `A operacao vai bloquear a exclusao.` |
| Rota com campos parciais | `Campos ja confirmados na operacao.` |
| Cadastro por convite | `Quando os convites estiverem ativos.` |
| Viagem com confirmacoes | `Politica operacional de confirmacao.` |

## Avisos

| Estado | Copy preferida |
| --- | --- |
| Permissao concedida | `Voce pode receber avisos operacionais quando a empresa ativar essa entrega.` |
| Entrega indisponivel | `A entrega real de avisos ainda esta sendo preparada.` |

## Excecoes tecnicas

Termos tecnicos podem continuar em nomes de arquivos, classes, campos de
contrato, rotas HTTP, testes de camada de dados e fixtures. Exemplos permitidos:

- `websocket_service.dart`, porque nomeia a integracao tecnica.
- `backendRoleLabel`, porque preserva o contrato de sessao.
- `snapshotOperacional` e `routeSnapshotStatus`, porque preservam payloads ja
  existentes.
- `manifesto-preview` e `sincronizar-manifesto`, porque ainda sao rotas de API.

Quando qualquer termo tecnico precisar aparecer em teste de widget ou tela
primaria, o teste deve justificar a excecao ou trocar a copy para este copydeck.
