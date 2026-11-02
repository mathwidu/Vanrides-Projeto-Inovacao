# Desenvolver o aplicativo

O companion motorista do MVP é `src/aplicativo-motorista`, com Flutter 3.38.5
e Dart 3.10.4. A pasta contém a raiz Flutter, código legado, assets, testes e
plataformas nativas necessários para analisar e compilar sem repositório privado.
Instalação de SDKs fica a critério de cada integrante; primeiro confira o que
já existe. Este roteiro não comprova execução nas máquinas dos colegas.

## Preparação Windows

No PowerShell, confirme as ferramentas antes de instalar/configurar:

```powershell
git --version
flutter --version
flutter doctor -v
python --version
node --version
java -version
```

Use Flutter 3.38.5 do [arquivo oficial de SDKs](https://docs.flutter.dev/install/archive),
Python 3.10 ou superior, Node.js 22 e Git for Windows. Para Android, configure
Android Studio/SDK conforme o [guia Flutter Android](https://docs.flutter.dev/platform-integration/android/setup).
O projeto usa Java 21 no CI, Gradle 8.14, AGP 8.11.1 e Kotlin 2.2.20;
compileSdk/targetSdk 36, build-tools 35.0.0 e NDK 28.2.13676358.

Na raiz do clone:

```powershell
cd src/aplicativo-motorista
flutter pub get --enforce-lockfile
flutter analyze --no-fatal-infos --no-fatal-warnings
flutter test
python scripts/quality_metrics_test.py
python scripts/quality_metrics.py --root . --baseline quality/ratchet-baseline.json --check
flutter build apk --debug --flavor driver --target lib/main_driver_mvp.dart --target-platform android-arm64
```

Esses comandos funcionam no PowerShell. O gate completo também valida contratos
de scripts Bash: execute-o no **Git Bash**, com `flutter`, `python3` e `node`
acessíveis nessa sessão:

```bash
bash scripts/check_app.sh
```

O último comando é executado na raiz do repositório. No Windows, se o Python
só estiver disponível como `python`/`py`, ajuste o PATH da sessão para expor
`python3` antes de usar os scripts Bash. O CI Ubuntu é a execução canônica
desse gate; não há simulação de iOS no Windows.

## Preparação macOS

Confira `git --version`, `flutter --version`, `flutter doctor -v`,
`python3 --version`, `node --version`, `java -version`, `xcodebuild -version`
e `pod --version`. Android requer os mesmos componentes acima. iOS exige
Xcode e CocoaPods **1.16.2**, conforme `ios/Podfile.lock`. Não troque o Ruby
global nem instale ferramentas sobre uma configuração existente sem revisão.

```bash
cd src/aplicativo-motorista
flutter pub get --enforce-lockfile
./scripts/ci_quality_gate.sh
flutter build apk --debug --flavor driver --target lib/main_driver_mvp.dart --target-platform android-arm64
flutter build ios --config-only --release --no-codesign --no-pub --flavor driver --target lib/main_driver_mvp.dart
cd ios
pod install --deployment
cd ..
flutter build ios --release --no-codesign --no-pub --flavor driver --target lib/main_driver_mvp.dart
git diff --exit-code -- pubspec.lock ios/Podfile.lock
```

O build iOS da CI seleciona Xcode 26.6 no runner padrão `macos-26`; nenhuma
equipe Apple, provisioning profile ou chave de mapas é fornecida. Uma eventual
instalação física exige autorização e assinatura próprias. O iPhone do
proprietário permanece **pausado**.

## Ambiente e segurança dos testes

`lib/services/api_constants.dart` usa `http://127.0.0.1:8080` como fallback do
legado. Pode-se fornecer `--dart-define=VANRIDES_API_BASE_URL=<URL_LOCAL>` para
um backend local de teste. O loopback do emulador é diferente do host; no Android,
`10.0.2.2` aponta para o host do emulador. A política do driver é independente:
sem `VANRIDES_PAIRING_API_ORIGIN`/host revisados no build, ele permanece sem
pareamento. Um APK de CI não acessa automaticamente a VPS.

As chaves locais opcionais do legado ficam em `ios/Flutter/Secrets.xcconfig`
e variáveis de build, sempre ignoradas. O MVP driver não usa UI de mapas,
Places ou navegação Waze; Google FLP e CoreLocation coletam GPS. Bibliotecas
legadas de mapas continuam no lockfile para preservar o aplicativo existente.

Fixtures em `test/`, `integration_test/` e testes nativos são cenários
controlados com IDs, contatos e coordenadas estáticas. Não substitua esses
valores por contatos ou GPS de pessoas. Relatórios e builds vão para `output/`
e `build/`, ignorados pelo Git. Antes de publicar evidência, revise o conteúdo.

## O que a CI verifica

O [workflow do aplicativo](../../.github/workflows/aplicativo.yml) roda no push,
PR e acionamento manual com `contents: read`, actions fixadas por SHA, sem
segredos e sem deploy. `scripts/check_app.sh` chama o gate de QA original
adaptado: contratos dos scripts, analyzer, testes unitários/widgets e ratchet.
Warnings/infos permanecem dívida registrada; erros, testes e regressões do
ratchet bloqueiam. Os locks precisam continuar sem diff.

Depois do gate, a CI compila Android `driver`/`main_driver_mvp.dart`, testa
o código nativo de produção e a variante explícita de regressão do spike,
verificando o isolamento de cada artefato. iOS recebe testes host e build
genérico sem assinatura, também com inspeção do artefato. Os logs e resumos
incluem o SHA para rastreabilidade. Não há upload de binários nem cache remoto
de dependências; os arquivos de build existem apenas durante a execução.

Esses resultados comprovam preparação de build e testes de laboratório. GPS
em segundo plano, tela bloqueada, consumo de bateria e o percurso real ainda
dependem de prova física autorizada. Piloto e produção têm gates separados.

Referências conferidas durante a preparação: [Flutter action](https://github.com/subosito/flutter-action),
[runners públicos padrão](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
e [imagem macOS 26](https://github.com/actions/runner-images/blob/main/images/macos/macos-26-arm64-Readme.md).
