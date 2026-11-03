> Registro histórico sanitizado. Não é receita de execução deste checkout nem prova da nova revisão. Use os guias em `docs/desenvolvimento/`.

# Preflight do MVP WhatsApp — 30/10/2026

Card: VR-WA-000-PREFLIGHT. Escopo local de controle/configuração, sem implementação de negócio ou publicação.

## Preservação e atribuição

Backup privado: `~/.local/share/vanrides/preflight/20261030T163110Z/`. Contém 16 arquivos de entrada, diffs binários staged/unstaged, HEAD/status por repositório e manifest SHA-256. Inclui dashboard/status anteriores. Os 14 dirty paths eram o padrão Graphify (.gitignore, .graphifyignore, config, bloco AGENTS e wrappers); foram revisados e adotados, sem inferir autoria. Nenhuma configuração pessoal global ou skill foi modificada.

Restauração: criar checkout separado no SHA de `head.txt` de cada módulo; aplicar os patches com verificação (`git apply --check` antes de aplicar) e copiar os arquivos não rastreados de `files/`, conferindo hashes do manifest. Não sobrescrever o checkout que já recebeu trabalho posterior. Para desfazer só este card, reverter seus commits explícitos; isso não remove os backups. Estado do harness tem cópia própria e deve ser sincronizado com o programa ao restaurar.

## Baseline de origem preservado

- coordination: `codex/vanrides-next-quality-phase` / `3376b72a88433ad5ad43c5f6d3b41bb935160f80`; evidência remota=False.
- backend: `dev` / `138558662c61468036840e7381fdbd67b2c73ff6`; evidência remota=False.
- app: `quality/app-quality-stack-2026-06-13` / `11eb09bfdc54143b8c7866830b193b40a4c64406`; evidência remota=False.
- maps: `main` / `a0c2a659eee262758191dff83300ef5b1045ef08`; evidência remota=False.

## Baseline local reconciliado

- coordination: `codex/organizacao-m5` / `ed0482e1f8c7d6fc05c24ec15c5f67ac3024d0ea`.
- backend: `codex/organizacao-m5` / `a262154fdee8c811563dc70b223ddea1b9aa1f2a`.
- app: `codex/organizacao-m5` / `91ea136693bc727eac4ba87dd1469fe71dd616fb`.

O coordenador usa o HEAD de entrada do card como ponto de origem; os commits de execução/fechamento são registrados no próprio card. Atualizar baseline local não transforma o código em release remoto. Não foram ampliados knownDirtyPaths, removidos gates nem alteradas decisões de piloto/produção.

## Verificação e limites

PASS: 59 testes program_control e 11 workspace_profile; check core sem erros no escopo ativo. Wrappers: bash -n, rejeição de valor inválido e force=1 no caminho gerenciado, retorno 2 explícito; configuração preserva fallback legado. Execução normal retornou CURRENT nos SHAs dos módulos: app 1000 arquivos/17179 nós/22474 arestas; backend 714 arquivos/8690 nós/27320 arestas. Semântica pendente em 210/100 documentos, lidos diretamente quando necessários. Revisão independente dos scripts/AGENTS sem achados relevantes.

Runtime Flutter/Maven/build/dispositivos: NOT_RUN em 000, conforme contrato do card. Gate completo, Postgres local, SDK Android e primeira instalação pertencem a 010. A disponibilidade de Flutter3.38.5/Dart3.10.4, Java17, Pods1.16.2 e Xcode27.1 foi inventariada; Android Studio/SDK/adb ausentes nos caminhos observados, espaço livre aproximado75GiB. Nenhum build do MVP ou teste de GPS ocorreu.

## Proveniência remota — somente leitura

Preflight oficial confirmou mathwidu@srv1397065, Docker29.5.0 e manager Swarm, status inspection_ok/deployment_tested=false. Consulta limitada confirmou n8n2.45.4, Evolution2.3.7 e serviços 1/1; APIs remotas VanRides continuam tags dev-b438e2f/prod-7231574, diferentes dos fontes locais. Isso comprova inventário/conexão, não saúde de jornada, conexão WhatsApp ou CI dos novos commits. Nenhum secret/env foi consultado; nenhuma alteração externa ou envio executado.

## Próximo passo

VR-WA-010-TOOLCHAINS: instalar Android Studio/SDK já autorizados, alinhar matriz de build, preparar runtime e builds mínimos, validar gates e instalar no Redmi8/iPhone. Acesso aos aparelhos e login Apple serão solicitados com o procedimento concreto pronto.
