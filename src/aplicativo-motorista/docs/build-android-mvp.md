# Build Android do MVP com Flutter 3.38.5

O candidato continua usando Flutter 3.38.5, seus lockfiles e
`--flavor driver --target lib/main_driver_mvp.dart`. Não atualizar SDK ou
dependências para contornar uma falha de argumentos do Gradle.

## Compatibilidade delimitada do assemble

O `BaseFlutterTaskHelper.kt` instalado na versão 3.38.5 passa
`--DartDefines=<lista codificada>` ao `flutter assemble`; o parser dessa
versão espera `-dDartDefines=<lista codificada>`. O [fonte da tag
3.38.5](https://github.com/flutter/flutter/blob/3.38.5/packages/flutter_tools/gradle/src/main/kotlin/tasks/BaseFlutterTaskHelper.kt)
permite conferir o argumento emitido.

`scripts/flutter_assemble_compat.py` muda esse prefixo e, somente para assemble,
usa `flutter_assemble_entrypoint.dart`. Uma subclasse fina reutiliza o
`AssembleCommand` instalado, mudando somente sua opção `define` para
`splitCommas=false`: o parser original faz `value.split(',')`, sem escape ou
quoting equivalente. A implementação do comando e seus targets não são
copiados ou modificados. Preserva a lista inteira, incluindo as vírgulas
entre os valores base64 e seu padding.
Não decodifica, lê configuração do bot, valida quantidade de defines, imprime
argumentos nem usa shell. Usa somente Dart, package config e fontes do Flutter
real especificado por `VANRIDES_FLUTTER_REAL_EXECUTABLE`, sem fallback para
PATH; outros comandos seguem diretamente para esse executável. Não alterar o
adapter privado antigo, o SDK ou a configuração pessoal para usar este fluxo.

## Gradle home novo e privado

O principal escolhe uma janela sem outros builds pesados. As variáveis deste
exemplo representam caminhos locais, não valores para copiar do chat:

```sh
python3 scripts/prepare_android_mvp_gradle.py \
  --directory "$MVP_GRADLE_HOME" \
  --flutter "$MVP_REAL_FLUTTER" \
  --cache-source "$MVP_EXISTING_GRADLE_HOME"
```

O destino precisa ser novo e fora de qualquer checkout Git. O preparador
reutiliza somente `caches/` e `wrapper/` como links para diretórios locais do
usuário sem escrita por grupo/outros. Recusa hooks de init dentro de uma
distribuição wrapper modificada. Não copia `init.gradle`, `init.d`,
`gradle.properties`, daemons ou outros arquivos do home anterior.

Cria seu próprio init, limitado ao módulo `:app`, tarefa
`compileFlutterBuildDriverRelease` e entrada `lib/main_driver_mvp.dart`.
O hook só funciona com opt-in explícito. O Gradle do objetivo usa um worker,
sem daemon/paralelismo e Kotlin em processo; heap padrão de 768 MiB
(`--heap-mb` permite de 256 a 2048 MiB, por decisão do principal).
O recibo `compat-receipt.json`, privado, registra hashes/caminhos, sem dados
do bot ou assinatura. Caches compartilhados exigem ausência de builds
concorrentes; os locks do Gradle continuam valendo.

## Build explícito

Somente no processo desse build, exportar:

```sh
export GRADLE_USER_HOME="$MVP_GRADLE_HOME"
export VANRIDES_FLUTTER_ASSEMBLE_COMPAT=1
export VANRIDES_FLUTTER_ASSEMBLE_ADAPTER="$MVP_APP_ROOT/scripts/flutter_assemble_compat.py"
export VANRIDES_FLUTTER_REAL_EXECUTABLE="$MVP_REAL_FLUTTER"
```

No Mac preparado, o Flutter real está em
`~/.local/share/vanrides/toolchains/flutter-3.38.5/bin/flutter`. O perfil
isolado `XDG_CONFIG_HOME=~/.local/share/vanrides/toolchains/flutter-config`
já seleciona JDK 21 e evita que Flutter escolha o JDK 25 do Android Studio.
Usar caminho absoluto expandido no ambiente; não executar `flutter config`
global nem substituir a configuração pessoal.

O processo também precisa receber a origem/host de pareamento revisados e o
caminho da assinatura existente. O responsável pelo build carrega número do
bot e assinatura dos arquivos privados autorizados, sem exibir valores.
Primeiro conferir a resolução com lockfile. O build abaixo mantém pub habilitado
para que o Flutter gere os registrants de release pelo fluxo oficial:

```sh
"$MVP_REAL_FLUTTER" pub get --enforce-lockfile
"$MVP_REAL_FLUTTER" build apk --release --flavor driver \
  --target lib/main_driver_mvp.dart --target-platform android-arm64 \
  --dart-define="VANRIDES_PAIRING_LINK_HOST=$VANRIDES_PAIRING_LINK_HOST" \
  --dart-define="VANRIDES_WHATSAPP_BOT_NUMBER=$VANRIDES_WHATSAPP_BOT_NUMBER"
```

Se um helper privado monta seu próprio ambiente, atualizar nele essas quatro
chaves: uma variável herdada não prevalece sobre `GRADLE_USER_HOME` fixado no
código. Não registrar a lista de argumentos real no chat ou no Git. Logs do
Flutter/Gradle ficam privados; o adapter não filtra a saída produzida pelos
processos filhos.

Não reutilizar `--no-pub` nessa preparação de release: na 3.38.5,
`regeneratePlatformSpecificToolingIfApplicable` não executa quando
`shouldRunPub=false`. Um registrant anterior de debug/teste pode manter
`IntegrationTestPlugin`, enquanto o Gradle release exclui corretamente os
plugins `dev_dependency`. Isso falha na compilação Java. A geração oficial
`injectPlugins(releaseMode: true)` elimina a dependência de teste; não editar o
registrant manualmente, não adicionar o plugin de teste ao release e não
atualizar SDK/dependências para contornar esse estado gerado.

O artefato esperado é `build/app/outputs/flutter-apk/app-driver-release.apk`.
Conferir isolamento MVP, assinatura, hash, revisão e ausência de diff nos
lockfiles e nos fontes lib/Android/iOS antes de instalar. Se um lockfile mudar,
reportar o diff em vez de aceitar ou desfazer automaticamente. Build não
comprova comportamento em aparelho.

## Testes leves e retorno

```sh
python3 -B -m unittest discover -s scripts -p flutter_assemble_compat_test.py
```

Os testes usam defines, SDK e caches sintéticos, sem chamar Flutter, Gradle,
assinatura ou aparelhos. Cobrem preservação de múltiplos defines, execução
explícita sem shell/eco e recusa de reutilização de init/configuração antigos.

Para comprovar o comportamento do parser e decoder reais da versão instalada:

```sh
FLUTTER_ROOT="$MVP_FLUTTER_SDK_ROOT" "$MVP_FLUTTER_SDK_ROOT/bin/cache/dart-sdk/bin/dart" \
  --packages="$MVP_FLUTTER_SDK_ROOT/packages/flutter_tools/.dart_tool/package_config.json" \
  scripts/flutter_assemble_parser_test.dart
```

Esse teste usa o comando original e um `BuildSystem` falso: nenhum target é
executado. Reproduz a falha de vírgulas e comprova que a opção oficial
`--dart-define-from-file` dessa versão não substitui esse contrato interno:
ela alimenta uma lista crua onde assemble espera base64. Captura o
`Environment.defines` real e usa `decodeDartDefines` original para conferir
host, bot e metadados sintéticos, inclusive uma rodada inteira sem padding.
Valores reais não entram no teste ou na saída. O workaround é específico da
3.38.5; outra versão requer revalidar o teste, sem atualização automática.

Ao terminar, encerrar somente processos desse Gradle home quando necessário
e remover o opt-in do ambiente do próximo comando. O retorno consiste em
descartar esse home privado após o build, removendo apenas os links e os
arquivos próprios; nunca apagar os destinos dos caches compartilhados.
O SDK, home antigo, adapter antigo e configurações pessoais permanecem
intactos. A remoção do home novo não altera o APK já produzido.
