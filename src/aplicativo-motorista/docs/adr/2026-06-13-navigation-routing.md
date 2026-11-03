# ADR: Navegacao E Roteamento Principal

Data: 2026-06-13

## Status

Aceita para planejamento. Implementacao ainda nao iniciada.

## Contexto

O app usa `MaterialApp(home: ExperienceLaunchScreen())` com uma
`navigatorKey` global em `VanRidesApp`. A navegacao principal ainda e
imperativa, baseada em `Navigator.push`, `pushReplacement`,
`pushAndRemoveUntil`, `maybePop`, `popUntil` e `MaterialPageRoute`.

Pontos criticos inventariados:

- `lib/main.dart`: instancia `MaterialApp`, registra `DeepLinkListener` e
  injeta `DeepLinkNavigationHandler` com `navigatorKey` global.
- `lib/app/experience/experience_home_router.dart`: resolve o destino inicial
  por papel e contexto de sessao, incluindo motorista, passageiro,
  responsavel, admin e papeis nao suportados.
- `lib/features/deep_linking/presentation/deep_link_navigation_handler.dart`:
  transforma `ViagemDeepLink`, `RastreioDeepLink` e `ConviteDeepLink` em telas
  concretas e carrega argumentos de sessao manualmente.
- telas de papel e onboarding ainda abrem destinos internos diretamente via
  `MaterialPageRoute`.
- telas de dialog/modal usam `Navigator.pop` para fechar UI local; isto nao e
  alvo inicial da migracao.

## Decisao

Adotar `go_router` como direcao para a navegacao principal, mas migrar em
ondas pequenas. Nao fazer big bang e nao trocar todos os `Navigator` de uma so
vez.

O primeiro passo de implementacao deve criar uma camada de rotas nomeadas e
testes de contrato antes de substituir navegacao existente. Enquanto a camada
nao existir, `DeepLinkNavigationHandler` e `ExperienceHomeRouter` seguem como
fontes de comportamento esperado.

## Alternativas Consideradas

### Manter somente Navigator imperativo

Vantagem: menor mudanca imediata.

Desvantagem: deep links, push notifications com `data.deepLink`, cold start e
rotas por papel continuam espalhados e dependentes de construtores de tela.

### Criar registry proprio sobre Navigator

Vantagem: migra pouco de infraestrutura.

Desvantagem: recria parte do que `go_router` ja oferece, incluindo rotas
nomeadas, parametros, redirects e integracao com deep links.

### Migrar para GoRouter em big bang

Vantagem: estado final chega rapido no papel.

Desvantagem: risco alto para login, onboarding, roles, deep links, back button
do iOS e navegacao admin. Rejeitado para esta fase.

## Plano De Migracao

1. Congelar contrato de rotas.
   - definir nomes, paths, parametros e payload minimo de sessao;
   - manter testes existentes como baseline: `experience_home_router_test` e
     `deep_link_navigation_handler_test`;
   - adicionar testes unitarios para o futuro `AppRouter` antes da primeira
     troca em producao.

2. Migrar launch/auth/cadastro.
   - introduzir `MaterialApp.router`;
   - mapear `/launch`, `/login`, `/cadastro/motorista`,
     `/cadastro/passageiro` e `/admin/login`;
   - preservar `DeepLinkListener` e session hydration.

3. Migrar destinos home por papel.
   - mapear `/motorista/home`, `/passageiro/home`, `/guardian/home` e
     `/admin`;
   - manter `ExperienceHomeRouter` como builder/adaptador ate todos os papeis
     ficarem cobertos por testes.

4. Migrar deep links e push notification routing.
   - converter `ViagemDeepLink`, `RastreioDeepLink` e `ConviteDeepLink` em
     chamadas de rota nomeada;
   - manter validacoes de papel e contexto de sessao antes do `go`/`push`;
   - garantir que deep link anonimo de convite continue caindo no login com
     codigo inicial.

5. Migrar navegacao interna por papel.
   - motorista: viagem, mapa, presencas e logout;
   - passageiro: presenca, localizacao, detalhes de viagem e logout;
   - responsavel: passageiro, localizacao, presencas e logout;
   - admin: shell, login admin e destinos de portal.

6. Remover `MaterialPageRoute` de navegacao principal.
   - manter `Navigator.pop`/`maybePop` para dialogos, modais e sheets;
   - remover somente rotas imperativas entre telas de produto.

## Testes Bloqueadores

Antes de qualquer migracao executavel, estes testes devem passar:

```bash
flutter test test/app/experience_home_router_test.dart test/features/deep_linking/deep_link_navigation_handler_test.dart
```

Antes de remover o roteamento imperativo antigo, adicionar:

- testes de `AppRouter` para `/launch`, `/login`, `/admin/login` e cadastros;
- testes de role redirect para motorista, passageiro, responsavel e admin;
- testes de deep link para viagem, rastreio e convite anonimo;
- smoke manual por papel, incluindo back button no iOS;
- smoke de cold start com deep link ou push `data.deepLink`.

## Fora De Escopo Desta ADR

- adicionar `go_router` ao `pubspec.yaml`;
- substituir `MaterialApp` por `MaterialApp.router`;
- remover `MaterialPageRoute`;
- alterar contratos de API, payloads, auth, CORS ou WebSocket;
- alterar copy, UI visual ou fluxo funcional de login/onboarding.

## Consequencias

- A direcao arquitetural fica decidida sem iniciar uma migracao arriscada.
- O backlog D-05 deve ser splitado em cards menores por onda de migracao.
- `DeepLinkNavigationHandler` vira contrato de comportamento ate existir um
  `AppRouter` testado.
- `Navigator.pop` usado para dialogos e modais nao e divida do mesmo tipo que
  `MaterialPageRoute` entre telas.
