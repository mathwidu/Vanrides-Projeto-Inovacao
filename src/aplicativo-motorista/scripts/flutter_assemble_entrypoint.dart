// Flutter 3.38.5 compatibility at one parser option. The SDK command is reused;
// its implementation, build targets and native build system are not copied.
import 'dart:io';

import 'package:args/args.dart';
import 'package:flutter_tools/runner.dart' as runner;
import 'package:flutter_tools/src/base/context.dart';
import 'package:flutter_tools/src/base/template.dart';
import 'package:flutter_tools/src/build_system/build_targets.dart';
import 'package:flutter_tools/src/cache.dart';
import 'package:flutter_tools/src/commands/assemble.dart';
import 'package:flutter_tools/src/globals.dart' as globals;
import 'package:flutter_tools/src/hook_runner.dart';
import 'package:flutter_tools/src/isolated/build_targets.dart';
import 'package:flutter_tools/src/isolated/mustache_template.dart';
import 'package:flutter_tools/src/isolated/native_assets/test/native_assets.dart';
import 'package:flutter_tools/src/native_assets.dart';
import 'package:flutter_tools/src/build_system/targets/hook_runner_native.dart';
import 'package:flutter_tools/src/pre_run_validator.dart';

ArgParser assembleParser(ArgParser original) {
  if (original.commands.isNotEmpty)
    throw StateError('UNSUPPORTED_ASSEMBLE_PARSER');
  final result = ArgParser(
    allowTrailingOptions: original.allowTrailingOptions,
    usageLineLength: original.usageLineLength,
  );
  for (final option in original.options.values) {
    if (option.isMultiple) {
      result.addMultiOption(
        option.name,
        abbr: option.abbr,
        help: option.help,
        valueHelp: option.valueHelp,
        allowed: option.allowed,
        allowedHelp: option.allowedHelp,
        defaultsTo: option.defaultsTo as List<String>?,
        callback: option.callback as void Function(List<String>)?,
        splitCommas: option.name == 'define' ? false : option.splitCommas,
        hide: option.hide,
        aliases: option.aliases,
      );
    } else if (option.isFlag) {
      result.addFlag(
        option.name,
        abbr: option.abbr,
        help: option.help,
        defaultsTo: option.defaultsTo as bool?,
        negatable: option.negatable!,
        callback: option.callback as void Function(bool)?,
        hide: option.hide,
        hideNegatedUsage: option.hideNegatedUsage!,
        aliases: option.aliases,
      );
    } else {
      result.addOption(
        option.name,
        abbr: option.abbr,
        help: option.help,
        valueHelp: option.valueHelp,
        allowed: option.allowed,
        allowedHelp: option.allowedHelp,
        defaultsTo: option.defaultsTo as String?,
        callback: option.callback as void Function(String?)?,
        mandatory: option.mandatory,
        hide: option.hide,
        aliases: option.aliases,
      );
    }
  }
  return result;
}

class CompatAssembleCommand extends AssembleCommand {
  CompatAssembleCommand({required super.buildSystem}) {
    _parser = assembleParser(super.argParser);
  }
  ArgParser? _parser;
  @override
  ArgParser get argParser => _parser ?? super.argParser;
}

Future<void> main(List<String> arguments) async {
  final root = Platform.environment['FLUTTER_ROOT'];
  if (root == null || !Directory(root).isAbsolute) {
    stderr.writeln('BLOCKED: EXPLICIT_FLUTTER_ROOT_REQUIRED');
    exitCode = 2;
    return;
  }
  Cache.flutterRoot = root;
  exitCode = await runner.run(
    arguments,
    () => [CompatAssembleCommand(buildSystem: globals.buildSystem)],
    reportCrashes: false,
    overrides: <Type, Generator>{
      BuildTargets: () => const BuildTargetsImpl(),
      FlutterHookRunner: () => FlutterHookRunnerNative(),
      TemplateRenderer: () => const MustacheTemplateRenderer(),
      TestCompilerNativeAssetsBuilder:
          () => const TestCompilerNativeAssetsBuilderImpl(),
      PreRunValidator: () => PreRunValidator(fileSystem: globals.fs),
    },
    shutdownHooks: globals.shutdownHooks,
  );
}
