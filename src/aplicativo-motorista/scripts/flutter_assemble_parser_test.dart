// Real Flutter 3.38.5 command and decoder, synthetic data, fake BuildSystem.
// No Dart compilation target, Gradle, bot configuration or signing is executed.
import 'dart:convert';
import 'dart:io';

import 'package:args/args.dart';
import 'package:flutter_tools/src/base/common.dart';
import 'package:flutter_tools/src/build_info.dart';
import 'package:flutter_tools/src/build_system/build_system.dart';
import 'package:flutter_tools/src/cache.dart';
import 'package:flutter_tools/src/commands/assemble.dart';
import 'package:flutter_tools/src/context_runner.dart';

import 'flutter_assemble_entrypoint.dart';

class CaptureBuildSystem extends BuildSystem {
  Map<String, String>? defines;
  @override
  Future<BuildResult> build(
    Target target,
    Environment environment, {
    BuildSystemConfig buildSystemConfig = const BuildSystemConfig(),
  }) async {
    defines = Map.of(environment.defines);
    return BuildResult(success: true);
  }

  @override
  Future<BuildResult> buildIncremental(
    Target target,
    Environment environment,
    BuildResult? previousBuild,
  ) => throw StateError('UNEXPECTED_BUILD_OPERATION');
}

class FixtureCommand extends CompatAssembleCommand {
  FixtureCommand({required super.buildSystem});
  ArgResults? parsed;
  @override
  ArgResults? get argResults => parsed;
  @override
  String packageConfigPath() =>
      File('.dart_tool/package_config.json').absolute.path;
}

class OriginalFixtureCommand extends AssembleCommand {
  OriginalFixtureCommand({required super.buildSystem});
  ArgResults? parsed;
  @override
  ArgResults? get argResults => parsed;
  @override
  String packageConfigPath() =>
      File('.dart_tool/package_config.json').absolute.path;
}

void check(bool condition) {
  if (!condition) throw StateError('SYNTHETIC_PARSER_ASSERTION_FAILED');
}

Future<void> main() async {
  final root = Platform.environment['FLUTTER_ROOT'];
  if (root == null) throw StateError('EXPLICIT_FLUTTER_ROOT_REQUIRED');
  Cache.flutterRoot = root;
  final previous = Directory.current;
  final fixture = Directory.systemTemp.createTempSync(
    'vanrides-assemble-parser-',
  );
  try {
    Directory.current = fixture;
    File('pubspec.yaml').writeAsStringSync(
      'name: synthetic_fixture\nenvironment:\n  sdk: ">=3.7.0 <4.0.0"\n',
    );
    Directory('.dart_tool').createSync();
    File(
      '.dart_tool/package_config.json',
    ).writeAsStringSync('{"configVersion":2,"packages":[]}');
    await runInContext(() async {
      final values = [
        'VANRIDES_PAIRING_LINK_HOST=links.example',
        'VANRIDES_WHATSAPP_BOT_NUMBER=SYNTHETIC_BOT',
        'FLUTTER_VERSION=3.38.5',
        'FLUTTER_CHANNEL=SYNTHETIC_CHANNEL',
        'FLUTTER_GIT_URL=SYNTHETIC_SOURCE',
      ];
      for (final payload in [
        values,
        values.map((value) {
          while (utf8.encode(value).length % 3 != 0) {
            value += 'x';
          }
          return value;
        }).toList(),
      ]) {
        final encoded = payload
            .map((value) => base64.encode(utf8.encode(value)))
            .join(',');
        final build = CaptureBuildSystem();
        final command = FixtureCommand(buildSystem: build);
        command.parsed = command.argParser.parse([
          '--output=output',
          '-dDartDefines=$encoded',
          'release_android_application',
        ]);
        await command.runCommand();
        check(build.defines?[kDartDefines] == encoded);
        check(
          jsonEncode(decodeDartDefines(build.defines!, kDartDefines)) ==
              jsonEncode(payload),
        );
      }
      final originalBuild = CaptureBuildSystem();
      final original = OriginalFixtureCommand(buildSystem: originalBuild);
      final equivalent = FixtureCommand(buildSystem: CaptureBuildSystem());
      check(
        jsonEncode(original.argParser.options.keys.toList()) ==
            jsonEncode(equivalent.argParser.options.keys.toList()),
      );
      for (final entry in original.argParser.options.entries) {
        final replacement = equivalent.argParser.options[entry.key]!;
        check(entry.value.type == replacement.type);
        check(
          jsonEncode(entry.value.defaultsTo) ==
              jsonEncode(replacement.defaultsTo),
        );
        check(
          jsonEncode(entry.value.aliases) == jsonEncode(replacement.aliases),
        );
        check(
          entry.key == 'define'
              ? !replacement.splitCommas
              : entry.value.splitCommas == replacement.splitCommas,
        );
      }
      final encoded = values
          .map((value) => base64.encode(utf8.encode(value)))
          .join(',');
      original.parsed = original.argParser.parse([
        '--output=output',
        '-dDartDefines=$encoded',
        'release_android_application',
      ]);
      try {
        await original.runCommand();
        throw StateError('ORIGINAL_SPLIT_WAS_NOT_REPRODUCED');
      } on ToolExit {
        check(originalBuild.defines == null);
      }

      File(
        'defines.json',
      ).writeAsStringSync(jsonEncode({'SYNTHETIC_KEY': 'SYNTHETIC_VALUE'}));
      final officialFile = OriginalFixtureCommand(buildSystem: originalBuild);
      officialFile.parsed = officialFile.argParser.parse([
        '--output=output',
        '--dart-define-from-file=defines.json',
        'release_android_application',
      ]);
      try {
        await officialFile.runCommand();
        throw StateError('OFFICIAL_FILE_WAS_NOT_REJECTED');
      } on ToolExit {
        check(originalBuild.defines == null);
      }
    });
    stdout.writeln(
      'PASS: original split/file limitations reproduced; host, bot and Flutter metadata preserved in both synthetic payloads; zero build targets executed.',
    );
  } finally {
    Directory.current = previous;
    fixture.deleteSync(recursive: true);
  }
}
