# ADR: Labels, Pins E Densidade Do Mapa No App

Data: 2026-06-18

## Status

Aceita para a proxima fase de implementacao.

## Contexto

O mapa do VanRides precisa parecer produto proprio, nao apenas Google Maps
embutido. Ao mesmo tempo, o usuario precisa entender rapido:

- onde esta a van;
- onde esta sua parada;
- quantas paradas faltam;
- se o sinal esta confiavel;
- qual e a proxima acao.

O risco do card era enriquecer o mapa com nomes, fotos e labels demais,
poluindo a tela e expondo dado pessoal sem necessidade operacional.

## Decisao

### Marcadores

- Substituir pins genericos por marcadores de produto:
  - van/onibus pequeno para motorista/veiculo;
  - pessoa/parada do passageiro para o passageiro selecionado;
  - marcadores numerados compactos para paradas.
- O marker da van deve ter estados visuais para `LIVE`, `DELAYED`, `STALE`,
  `PAUSED` e `UNAVAILABLE`.
- O marker do passageiro deve indicar selecao/foco, nao virar um pin padrao.

### Labels

- Nao mostrar nomes completos sempre visiveis no mapa.
- Mostrar label curta somente em foco, tap ou sheet contextual.
- Priorizar copy operacional:
  - `Voce`;
  - `Minha parada`;
  - `Proxima parada`;
  - `Van`;
  - `Sinal ao vivo`;
  - `Atualizado ha X min`.
- Para responsavel, o nome do passageiro selecionado pode aparecer no sheet ou
  tooltip de foco, mas nao em todos os marcadores ao mesmo tempo.

### Fotos

- Foto/avatar de passageiro fica fora da primeira implementacao de mapa.
- O app pode usar foto no perfil e em listas/sheets, mas o mapa deve preferir
  simbolos claros ate existirem regras de privacidade, fallback e tamanho.
- Foto no marker so volta em card futuro se houver:
  - imagem validada no perfil;
  - fallback consistente;
  - controle de exposicao para responsavel/passageiro;
  - teste visual com multiplas paradas.

### Densidade

- Passageiro ve a van, sua parada e o trecho relevante.
- Responsavel ve o passageiro selecionado e evita multiplas labels competindo.
- Motorista ve proxima parada e rota, nao dados pessoais desnecessarios.
- Em zoom baixo, reduzir marcadores de parada para clusters/contagem.
- Em zoom alto, permitir numeros das proximas paradas.

## Contrato Esperado Do Backend

O app nao precisa de nome completo de todos os passageiros no mapa. O contrato
ideal e entregar:

- `trackingState.locationFreshness`;
- `trackingState.stopsBeforePassenger`;
- `trackingState.stopsRemaining`;
- `trackingState.nextStopId`;
- parada selecionada do passageiro atual;
- proxima parada com label operacional curta quando o papel permitir;
- foto/avatar apenas como campo futuro opcional, nao requerido para renderizar.

## Consequencias

- O app fica mais premium e menos poluido.
- A UI evita expor nome/foto sempre visivel sem necessidade.
- A proxima implementacao deve focar nos estados de marker e sheet antes de
  adicionar midia.
- O card de notificacoes operacionais pode usar a mesma linguagem: proximo,
  chegando, parada concluida.

## Referencias Internas

- `docs/design/map-visual-system-prototype.md`
- workspace root `docs/research/2026-06-14-map-navigation-references-and-costs.md`
- workspace root `docs/architecture/2026-06-14-route-eta-cost-control.md`
