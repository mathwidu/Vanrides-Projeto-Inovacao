# Aplicativo motorista

Este módulo integra o repositório Git único. Leia `../../AGENTS.md`,
`../../program/README.md`, o card ativo e
`../../docs/desenvolvimento/aplicativo.md` antes de alterar código.

- Preserve `lib/`, integrações nativas, canais, IDs de aplicação e o legado;
  nome do diretório não autoriza extração/refatoração de funcionalidades.
- O MVP usa `--flavor driver --target lib/main_driver_mvp.dart`. FLP e
  CoreLocation continuam responsáveis pelo GPS. Maps/Waze ficam fora do MVP.
- `backlog/` conserva referências e entradas dos testes. Prioridades e novas
  evidências pertencem ao programa da raiz e às issues da equipe.
- De dentro deste módulo: `flutter pub get --enforce-lockfile` e
  `./scripts/ci_quality_gate.sh`. Da raiz: `bash scripts/check_app.sh`.
- Preserve `pubspec.lock`, `ios/Podfile.lock` e o ratchet. Não atualize
  dependências ou enfraqueça assertions para fazer a migração passar.
- Commits, evidências de SHA/tree e status usam o Git da raiz. Não crie `.git`
  aninhado. O validador de proveniência resolve blobs relativos ao módulo.
- Nunca versionar tokens, localização coletada, arquivos de pareamento,
  certificados, keystores, equipes Apple, IDs de aparelhos ou configurações
  locais. Use apenas fixtures sintéticas e diretórios ignorados.
- Teste físico do iPhone está pausado. Builds sem assinatura não são prova
  de instalação, comportamento de GPS ou aceite de aparelho.
- Alterações de API exigem gates dos produtores/consumidores e smoke do
  mesmo SHA. Não usar CI histórica da origem como prova do destino.

Para análise estrutural, use a skill `graphify` na raiz deste checkout e
confirme as relações nos fontes. Não inicialize outro índice Git neste módulo.
