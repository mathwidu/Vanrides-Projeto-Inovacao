> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Builds e instalação local do motorista

Escopo: `VR-WA-010-TOOLCHAINS`. O checkpoint atual apenas comprova a instalação.
Resultados, SHAs e aceite físico estão na
[evidência do card](../../../program/evidence/VR-WA-010-TOOLCHAINS.md).

## Ambiente observado

- iPhone 13, iOS 27.0.1, Personal Team gratuito, Developer Mode habilitado.
- Redmi **Note** 8, Android 10/API 29, MIUI 12, ARM64/ARMv7. A indicação inicial
  “Redmi 8” foi corrigida pela identificação do aparelho conectado.
- Emulador `VanRides_API36`, imagem Google APIs ARM64 API 36, como apoio.

A matriz e os exports de Flutter/Java/Ruby/SDK estão no
[guia do app](../../../vanrides-app/docs/driver-mvp-development.md).
Usar um terminal dedicado com esses exports, sem substituir o perfil global.
Executar os comandos a seguir dentro de `vanrides-app/`.

## Android

Reutilizar a assinatura privada criada em
`~/.local/share/vanrides/signing/driver-test/`; uma chave diferente impede
atualizar uma instalação existente. A geração inicial fica no guia do app.

```bash
export VANRIDES_DRIVER_SIGNING_FILE="$HOME/.local/share/vanrides/signing/driver-test/driver-signing.properties"
flutter build apk --release --flavor driver --target lib/main_driver_mvp.dart
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
  build/app/outputs/flutter-apk/app-driver-release.apk
shasum -a 256 build/app/outputs/flutter-apk/app-driver-release.apk
"$ANDROID_HOME/platform-tools/adb" devices -l
```

Selecionar explicitamente o serial do aparelho observado, sem registrá-lo na
documentação. Com `VANRIDES_ANDROID_DEVICE` definido nesse terminal:

```bash
"$ANDROID_HOME/platform-tools/adb" -s "$VANRIDES_ANDROID_DEVICE" install -r \
  build/app/outputs/flutter-apk/app-driver-release.apk
```

No Redmi, habilitar Depuração USB, autorizar o Mac e manter a tela desbloqueada.
A MIUI deste aparelho também exigiu **Instalar via USB** e confirmação da
instalação pelo proprietário. `INSTALL_FAILED_USER_RESTRICTED` é recusa nessa
etapa; conferir o prompt no telefone antes de repetir. Não foi necessário
desativar a verificação de apps.

Depois de testes Flutter, não usar `--no-pub` no build release: Flutter 3.38.5
precisa regenerar os registrants sem o plugin de integração exclusivo de testes.
Não executar builds Android/iOS simultaneamente no mesmo checkout, pois ambos
usam arquivos de tooling gerados pelo Flutter.

## iPhone

O proprietário entra em Xcode → Settings → Apple Accounts e conclui a
autenticação na interface Apple. Selecionar o Personal Team efetivamente
observado e conferir o aparelho pareado por USB. A configuração do driver não
herda o team antigo do Runner.

Observar o dispositivo com `xcrun devicectl list devices`; definir localmente
`VANRIDES_IOS_DEVICE` com seu UDID e `VANRIDES_APPLE_TEAM_ID` com o team observado.
Esses valores não devem ser substituídos por identificadores de exemplo.

```bash
pod install --project-directory=ios
xcodebuild -workspace ios/Runner.xcworkspace -scheme driver \
  -configuration Release-driver -destination "id=$VANRIDES_IOS_DEVICE" \
  -derivedDataPath "$HOME/.local/share/vanrides/build/wa010-ios-device" \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration \
  "DEVELOPMENT_TEAM=$VANRIDES_APPLE_TEAM_ID" CODE_SIGN_STYLE=Automatic build
```

Artefato: `~/.local/share/vanrides/build/wa010-ios-device/Build/Products/Release-driver-iphoneos/Runner.app`.
Definir `VANRIDES_IOS_APP` com esse caminho no terminal:

```bash
codesign --verify --deep --strict "$VANRIDES_IOS_APP"
xcrun devicectl device install app --device "$VANRIDES_IOS_DEVICE" "$VANRIDES_IOS_APP"
xcrun devicectl device process launch --device "$VANRIDES_IOS_DEVICE" \
  dev.matheusduarte.vanrides.driver
```

O processo acima não anexa debugger. Na primeira abertura, o proprietário pode
precisar confiar no desenvolvedor em Ajustes → Geral → VPN e Gerenciamento de
Dispositivo. Isso ocorreu neste aparelho; o certificado, os entitlements e o
registro do UDID no perfil foram conferidos antes de pedir a confirmação.

O perfil criado em 30/10 expira em **06/11/2026 às 17:45:59 UTC**. Renovar
compilando/assinando e reinstalando com a conta autenticada; registrar a nova
expiração real na evidência da versão. Não há TestFlight com esta conta gratuita.
Ausência de rede ou de confiança pode impedir a verificação do perfil no iPhone.

## Validação e continuidade

1. Executar `./scripts/qa_gate_card.sh --no-e2e` no SHA avaliado.
2. Conferir bundle/package ID, assinatura, artefato e checksum.
3. Instalar nos aparelhos identificados; abrir e observar a tela do checkpoint.
4. Desconectar o Mac, fechar o app e reabrir pelo ícone nos dois telefones.
5. Registrar a confirmação humana e reconectar para o card seguinte.

O link `vanridesdriver://preflight` exercita a abertura da variante. Neste card
ele não autoriza coleta nem pareamento; o handler autenticado é parte posterior
do plano. O teste de localização em segundo plano exige as etapas 020/030 e
percurso real, além desta instalação.

Backups locais dos arquivos anteriores e configuração dedicada ficam em
`~/.local/share/vanrides/toolchains/backups/` e `flutter-config/`. Reverter os
commits específicos recupera os fontes; não apagar assinatura ou SDKs usados por
outros projetos. O emulador é exclusivo do ensaio e pode ser encerrado ao terminar
para liberar memória, preservando seu AVD.

## Referências

- [Configurar iOS no Flutter](https://docs.flutter.dev/platform-integration/ios/setup)
- [Assinar apps Android](https://developer.android.com/studio/publish/app-signing)
- [Flavors Flutter Android](https://docs.flutter.dev/deployment/flavors)
- [Flavors Flutter iOS](https://docs.flutter.dev/deployment/flavors-ios)
