# Portal Admin S1 — Design Brief para Google Stitch

Objetivo: gerar um pacote completo de telas para o `Portal Empresa` do VanRides, cobrindo tanto o redesign consistente das páginas já existentes quanto as novas páginas de `Rotas` e `Viagens`.

Conceito importante:
- não queremos telas soltas
- queremos um sistema visual único para o portal administrativo
- isso reduz retrabalho no Figma e facilita a implementação fiel no Flutter Web

## Direção visual do portal

### Identidade
- produto: `VanRides`
- experiência: `Portal Empresa`
- plataforma: `Flutter Web`
- uso principal: desktop/laptop

### Linguagem visual desejada
- clean, acolhedora e operacional
- mais produto real do que dashboard genérico
- visual leve, confiável e humano
- foco em legibilidade e ação, não em decoração excessiva

### Referências do sistema já existente
- o app usa `SF Pro Display`
- fundo claro, levemente quente
- azul institucional do VanRides
- laranja como acento administrativo do portal
- cards com cantos amplos
- sombras suaves
- layout com bastante respiro
- hierarquia clara entre navegação, contexto e ação

### Paleta de referência
- azul principal: `#2D6CDF`
- azul escuro de texto: `#161B36`
- laranja administrativo: `#FF8C00`
- creme claro de apoio: `#FFF1D6`
- dourado claro de apoio: `#FFE1B0`
- fundo geral: `#F6F4EE`
- superfícies: branco

### Regras de UX
- desktop-first, mas responsivo para tablet/laptop menor
- sidebar fixa
- header superior com contexto da empresa e usuário admin
- componentes administrativos consistentes entre todas as páginas
- tabelas/listas com filtros simples
- páginas de detalhe em leitura confortável
- formulários com boa hierarquia e agrupamento lógico
- feedbacks claros para sucesso/erro/ações destrutivas

## Escopo do pacote de design

### 1. Redesign das telas já existentes
Queremos redesenhar, mantendo a mesma arquitetura funcional:

1. `AdminLoginScreen`
2. `AdminShellScreen`
3. `AdminDashboardScreen`
4. `AdminMotoristasScreen`
5. `AdminPassageirosScreen`
6. `AdminResponsaveisScreen`
7. `AdminVinculosScreen`

### 2. Novas telas a criar
8. `RotasListScreen`
9. `RotaFormScreen`
10. `RotaDetalheScreen`
11. `ViagensListScreen`
12. `ViagemCreateScreen`

### 3. Componentes base que o design deve prever
- sidebar do portal
- topbar do portal
- cards de resumo
- tabela/lista administrativa
- painel de detalhe
- formulário em card ou modal largo
- estados vazios
- banners de feedback
- dialogs de confirmação
- badges de status

## Estrutura esperada de cada tela

### `AdminLoginScreen`
Objetivo:
- autenticação administrativa

Conteúdo:
- hero institucional do portal
- formulário de login
- ação secundária para bootstrap do primeiro admin

### `AdminShellScreen`
Objetivo:
- layout padrão do portal

Conteúdo:
- sidebar consistente
- header/topbar
- área principal com largura confortável

### `AdminDashboardScreen`
Objetivo:
- leitura geral da operação administrativa

Conteúdo:
- cards-resumo
- ações rápidas
- destaques operacionais
- preparação para incluir `Rotas` e `Viagens`

### `AdminMotoristasScreen`
Objetivo:
- gestão administrativa de motoristas

Estrutura:
- lista/tabela
- busca/filtro simples
- painel de detalhe
- CTA “Novo motorista”

### `AdminPassageirosScreen`
Objetivo:
- gestão administrativa de passageiros

Estrutura:
- lista/tabela
- destaque do motorista vinculado
- detalhe do passageiro
- CTA de criação e edição

### `AdminResponsaveisScreen`
Objetivo:
- gestão administrativa de responsáveis

Estrutura:
- lista/tabela
- detalhe com passageiros vinculados
- ações de criar, editar e remover

### `AdminVinculosScreen`
Objetivo:
- gerir relações do domínio

Estrutura:
- bloco `passageiro -> motorista`
- bloco `responsável -> passageiro`
- clareza operacional, sem visual confuso

### `RotasListScreen`
Objetivo:
- listar e gerenciar `rotas-base`

Informações exibidas:
- nome da rota
- motorista
- quantidade de paradas
- dias de operação
- geração automática ativa/inativa
- resumo do destino final

Ações:
- criar rota
- abrir detalhe
- editar

### `RotaFormScreen`
Objetivo:
- criar/editar `rota-base`

Campos:
- nome da rota
- motorista responsável
- passageiros da rota em ordem
- dias de operação
- geração automática ativa
- destino final:
  - nome
  - endereço
  - latitude
  - longitude

Conceito importante:
- essa tela precisa parecer a “central de preparação da rota”
- o agrupamento de campos deve deixar claro o que é:
  - identidade da rota
  - composição da rota
  - agenda da rota
  - destino final operacional

### `RotaDetalheScreen`
Objetivo:
- leitura completa da `rota-base`

Blocos:
- resumo da rota
- motorista
- paradas ordenadas
- destino final
- dias de operação
- geração automática
- CTA “Criar viagem do dia”

### `ViagensListScreen`
Objetivo:
- listar viagens criadas

Informações exibidas:
- data da operação
- rota-base
- motorista
- status
- quantidade de paradas

Ações:
- filtrar por data
- criar viagem manual
- abrir detalhe futuro

### `ViagemCreateScreen`
Objetivo:
- criar manualmente a viagem do dia

Campos:
- rota-base
- data de operação

Resumo visual desejado:
- mostrar motorista da rota
- mostrar quantidade de paradas
- mostrar destino final herdado da rota

## Estados obrigatórios
Para todo o pacote, gerar também variações ou orientação visual para:
- `loading`
- `empty state`
- `error state`
- `success feedback`
- `confirmation dialog`

## Prompt pronto para colar no Google Stitch

```text
Crie um pacote completo de design desktop-first para o “Portal Empresa” do app VanRides, pensado para implementação em Flutter Web.

Contexto do produto:
VanRides é uma plataforma de transporte por vans. Já existe um app operacional mobile para motorista, passageiro e responsável. O portal web é usado pela empresa para gerenciar pessoas, vínculos, rotas e viagens do dia.

Objetivo desta rodada:
Gerar um sistema visual consistente para o portal administrativo inteiro, redesenhando as telas já existentes e criando as novas telas de Rotas e Viagens.

Direção visual obrigatória:
- clean, confiável, acolhedora e operacional
- desktop-first
- sem aparência de dashboard genérico de template
- bastante respiro visual
- cards grandes, cantos arredondados, sombras suaves
- tipografia forte e legível
- hierarquia clara entre navegação, contexto e ação
- fundo claro e levemente quente
- superfícies brancas
- azul institucional do produto para ações principais
- laranja para acento administrativo do portal
- sensação de produto real, humano e organizado

Paleta de referência:
- azul principal `#2D6CDF`
- azul escuro de texto `#161B36`
- laranja administrativo `#FF8C00`
- creme claro `#FFF1D6`
- dourado claro `#FFE1B0`
- fundo geral `#F6F4EE`

Tipografia e tom:
- moderna, forte, amigável
- evitar excesso de texto
- clareza operacional acima de decoração

Estrutura do portal:
- sidebar fixa
- topbar/header
- área principal responsiva
- componentes administrativos reutilizáveis

Desenhe as seguintes telas:

1. AdminLoginScreen
- hero institucional do portal
- login administrativo
- ação secundária para bootstrap do primeiro admin

2. AdminShellScreen
- layout padrão do portal
- sidebar
- topbar
- área de conteúdo pronta para todas as seções

3. AdminDashboardScreen
- cards resumo
- ações rápidas
- destaques operacionais
- espaço consistente para as seções de Rotas e Viagens

4. AdminMotoristasScreen
- lista/tabela de motoristas
- busca/filtro simples
- painel de detalhe
- CTA de novo motorista

5. AdminPassageirosScreen
- lista/tabela de passageiros
- detalhe do passageiro
- destaque de motorista vinculado
- CTA de criar/editar

6. AdminResponsaveisScreen
- lista/tabela de responsáveis
- detalhe com passageiros vinculados
- ações de criar, editar e remover

7. AdminVinculosScreen
- bloco passageiro -> motorista
- bloco responsável -> passageiro
- clareza de gestão de relacionamento

8. RotasListScreen
- lista de rotas-base da empresa
- busca
- filtro por motorista
- colunas/cartões com:
  - nome da rota
  - motorista
  - quantidade de paradas
  - dias de operação
  - geração automática ativa/inativa
  - resumo do destino final
- CTA “Nova rota”

9. RotaFormScreen
- formulário de criação/edição de rota-base
- seções claras para:
  - identidade da rota
  - motorista responsável
  - passageiros da rota em ordem
  - dias de operação
  - geração automática
  - destino final
- destino final com campos:
  - nome
  - endereço
  - latitude
  - longitude

10. RotaDetalheScreen
- resumo da rota
- motorista
- lista ordenada de paradas
- destino final configurado
- dias de operação
- geração automática
- CTA “Criar viagem do dia”

11. ViagensListScreen
- listagem de viagens criadas
- filtro por data
- leitura de:
  - data
  - rota-base
  - motorista
  - status
  - quantidade de paradas
- CTA “Criar viagem”

12. ViagemCreateScreen
- formulário simples para criar a viagem do dia a partir de uma rota-base
- campos:
  - rota-base
  - data de operação
- resumo visual da rota selecionada:
  - motorista
  - quantidade de paradas
  - destino final

Estados obrigatórios para o sistema:
- loading
- empty state
- error state
- success feedback
- confirmation dialog

O que é importante no resultado:
- coerência total entre todas as telas
- mesmo sistema de componentes
- visual forte o bastante para parecer produto final
- preparo para implementação fiel em Flutter Web
- não use dark mode
- mantenha o design com linguagem administrativa, mas humana e agradável
```

## Resultado esperado depois do Stitch
- um pacote visual único do portal
- frames suficientes para levar ao Figma
- componentes reutilizáveis já pensados
- base pronta para implementação sequencial no app
