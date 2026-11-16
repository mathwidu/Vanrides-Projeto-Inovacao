# Apoio de UI do MVP no iPhone físico

Este runner separado opera o aplicativo normal instalado com
`--flavor driver --target lib/main_driver_mvp.dart`. Não instala/reinicia o
driver, não simula GPS, não redefine permissões e não recebe credenciais.
O principal coordena builds e é o único operador dos aparelhos.

## Candidato do aplicativo

Com SDKs locais selecionados e configuração revisada na sessão do principal,
executar em série, quando houver janela de recursos. As variáveis abaixo
representam configuração privada já preparada; não colar valores reais no chat.
O número do bot e o host do Dart precisam corresponder à configuração nativa.

```sh
flutter pub get --enforce-lockfile
flutter build apk --release --flavor driver --target lib/main_driver_mvp.dart \
  --target-platform android-arm64 \
  --dart-define="VANRIDES_PAIRING_LINK_HOST=$VANRIDES_PAIRING_LINK_HOST" \
  --dart-define="VANRIDES_WHATSAPP_BOT_NUMBER=$VANRIDES_WHATSAPP_BOT_NUMBER"
```

No Android, `VANRIDES_PAIRING_API_ORIGIN`, `VANRIDES_PAIRING_LINK_HOST` e
`VANRIDES_DRIVER_SIGNING_FILE` precisam estar exportadas para o processo Gradle.
Pub permanece habilitado no build para gerar o registrant de release oficial;
conferir locks/fontes antes e depois. O [guia Android](../../docs/build-android-mvp.md)
explica o home isolado e a compatibilidade da versão fixa do Flutter.
O APK é `build/app/outputs/flutter-apk/app-driver-release.apk`. Comparar o
certificado do candidato com o app instalado antes de usar `adb install -r`.
Não substituir o certificado nem desinstalar para contornar incompatibilidade.

```sh
flutter build ios --config-only --release --no-codesign --no-pub \
  --flavor driver --target lib/main_driver_mvp.dart \
  --dart-define="VANRIDES_PAIRING_LINK_HOST=$VANRIDES_PAIRING_LINK_HOST" \
  --dart-define="VANRIDES_WHATSAPP_BOT_NUMBER=$VANRIDES_WHATSAPP_BOT_NUMBER"
(cd ios && pod install --deployment)
xcodebuild -workspace ios/Runner.xcworkspace -scheme driver -configuration Release-driver \
  -sdk iphoneos -destination "id=$VANRIDES_IOS_DEVICE_ID" -derivedDataPath "$MVP_APP_DERIVED" \
  DEVELOPMENT_TEAM="$VANRIDES_UI_TEAM" \
  VANRIDES_PAIRING_API_ORIGIN="$VANRIDES_PAIRING_API_ORIGIN" \
  VANRIDES_PAIRING_LINK_HOST="$VANRIDES_PAIRING_LINK_HOST" \
  -allowProvisioningUpdates build
```

Usar DerivedData privado fora do Git. O candidato fica em
`$MVP_APP_DERIVED/Build/Products/Release-driver-iphoneos/Runner.app`.
O principal confere flavor, bundle, configuração compilada, hash, assinatura e
validade do perfil antes de `xcrun devicectl device install app` nesse artefato.
Os comandos acima são procedimento de reprodução, não evidência de execução.
O helper legado `run_dev_iphone.sh` não seleciona esse flavor/target.

O fluxo é: abrir app pelo ícone → `pair` → `copy-code` → `open-whatsapp` →
confirmar vínculo na conversa autorizada → retornar e `refresh` → abrir link
privado de saída → `confirm-start` → `inspect` → `stop`.
`copy-code` toca os controles normais do app e deixa o código transitório no
clipboard para a conversa; não lê nem exporta seu conteúdo. O runner não envia
mensagens. WhatsApp, permissões do sistema, confiança, desbloqueio, assinatura
e percurso são etapas separadas. A confirmação de start é sempre explícita.

## Preparação sem operar o aparelho

Use o Ruby/gems já preparados no Mac. Configure `VANRIDES_UI_TEAM` apenas na
sessão privada; equipe, aparelho e artefatos ficam fora do Git. Para gerar:

```sh
ruby integration_test/driver_mvp_ios/generate_project.rb --output "$MVP_UI_PROJECT"
```

`MVP_UI_PROJECT` é um diretório privado novo fora de qualquer checkout. O
gerador não faz build nem assinatura. Na janela de execução coordenada, o
principal constrói somente o runner com o mesmo Personal Team, scheme
`DriverMVP`, configuração `Release`, destino iPhone físico e DerivedData
privado; usa `build-for-testing` e `-allowProvisioningUpdates` quando cabível.
O runner gratuito também precisa de perfil vigente e vaga de app no aparelho.

Depois do build, preparar uma ação:

```sh
python3 scripts/qa_driver_mvp_ios.py prepare \
  --source-xctestrun "$MVP_UI_SOURCE_RUN" \
  --runner-app "$MVP_UI_RUNNER_APP" \
  --output-dir "$MVP_UI_ACTION" --command inspect
```

Cada ação usa diretório novo. `command.json` contém somente comando e hash do
fonte; `run.xctestrun` referencia o driver e o runner já instalados, sem paths
para reinstalar o driver, argumentos ou ambiente injetados. Comandos:
`inspect`, `pair`, `copy-code`, `open-whatsapp`, `refresh`, `confirm-start`, `stop`.

## Execução pelo principal

O principal instala apenas o runner e copia `command.json` para `Documents` do
container desse runner, usando `xcrun devicectl device copy to` com domínio
`appDataContainer`. Em seguida executa `xcodebuild test-without-building
-xctestrun <run.xctestrun> -destination id=<id privado>
-parallel-testing-enabled NO -collect-test-diagnostics never
-resultBundlePath <diretório privado novo>`. Não incluir o app driver como
produto dependente nem usar uma chamada que reinstale o app sob teste.

O XCTest consome e remove o comando. Copiar de volta somente
`Documents/result.json` do runner. O resultado contém booleans de UI e códigos
de erro constantes; não contém código de pareamento, clipboard, token, URL ou
coordenadas. Logs/xcresult completos continuam privados e não devem ir ao Git.
Anexos de sistema/usuário e cobertura ficam desabilitados. `inspect` observa a
UI; não substitui confirmação de posição no backend ou recibo do WhatsApp.

Para abrir um retorno ou link real de saída sem colocá-lo no chat/comando:
criar fora do Git um JSON 0600 com chaves `device` e `url`, usando valores
privados locais, e executar a ação explícita:

```sh
python3 scripts/qa_driver_mvp_ios.py open-link --input "$MVP_UI_LINK_INPUT" --host "$MVP_LINK_HOST"
```

Sem `--host`, somente o scheme `vanridesdriver` é aceito. Com host revisado,
aceita também os caminhos HTTPS exatos de retorno/saída. A URL é entregue ao
app e capturas da ferramenta são suprimidas. Abrir por devicectl não comprova
o toque real no WhatsApp; validar esse percurso separadamente. O link contém
a autorização da saída e deve permanecer no arquivo privado.

## Verificação offline

```sh
python3 -B -m unittest discover -s scripts -p qa_driver_mvp_ios_test.py
```

Esses testes usam fixtures sintéticas e subprocess mockado. Comprovam
isolamento do alvo, permissões dos arquivos e recusa de links/targets
indevidos. Não comprovam assinatura, instalação, toques, GPS ou segundo plano.
