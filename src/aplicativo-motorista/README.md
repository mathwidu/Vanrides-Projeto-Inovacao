# Aplicativo VanRides

Raiz Flutter completa do aplicativo. O MVP WhatsApp usa o companion do
motorista em `lib/main_driver_mvp.dart`, com o flavor `driver`. O pacote Dart
`vanrides_frontend_dev`, canais nativos e aplicação legada foram preservados
para manter imports, persistência e contratos compatíveis.

Comece pelo [guia de desenvolvimento](../../docs/desenvolvimento/aplicativo.md).
O [mapa do código](docs/architecture.md), as [fronteiras Dart](lib/README.md) e
os testes explicam a implementação. Prioridade e critérios atuais ficam no
[programa da equipe](../../program/README.md); `backlog/` é referência histórica
de QA e entrada de testes, não uma segunda fila de trabalho.

```bash
flutter --version
flutter pub get --enforce-lockfile
./scripts/ci_quality_gate.sh
flutter build apk --debug --flavor driver --target lib/main_driver_mvp.dart
```

Versão de referência: Flutter **3.38.5** / Dart **3.10.4**. A API legada usa
loopback por padrão; o driver permanece `UNCONFIGURED` até receber configuração
de pareamento explícita. Analyzer e testes unitários não precisam de chave de
mapas, conta Apple ou servidor remoto. O GPS Android usa Google FLP; no iOS,
CoreLocation. Maps/Waze e mapas próprios não integram o MVP.

A CI verifica Android debug e iOS sem assinatura; resultados ficam nos logs.
Build aprovado não comprova GPS em segundo plano ou aceite físico. O teste
do iPhone permanece pausado.

Origem: `mathwidu/vanrides-app`, commit
`b9fcedbbf949ad9f1684efcc1a2b0f2176cf7877`. Nesta migração foram adaptados
caminhos de CI/Git, configuração local e documentação. Protótipos Figma,
marketing, histórico Git, caches, dependências instaladas, assinaturas e
evidências operacionais privadas não fazem parte desta cópia.
