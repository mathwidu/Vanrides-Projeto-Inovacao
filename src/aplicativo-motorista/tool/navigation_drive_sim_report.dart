import 'dart:convert';
import 'dart:io';

import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_drive_simulation.dart';

Future<void> main(List<String> args) async {
  final parsed = _Args.parse(args);
  final result = await const DriverNavigationDriveSimulation().run();

  if (parsed.checkOnly) {
    stdout.writeln(
      'NAVIGATION_DRIVE_SIM_SMOKE_OK '
      'steps=${result.steps.length} '
      'evidence_root=output/navigation-drive-sim',
    );
    return;
  }

  final outputDir = Directory(parsed.outputDir);
  outputDir.createSync(recursive: true);

  final reportFile = File('${outputDir.path}/navigation-drive-sim.md');
  final jsonFile = File('${outputDir.path}/navigation-drive-sim.json');

  reportFile.writeAsStringSync(result.toMarkdown());
  jsonFile.writeAsStringSync(
    const JsonEncoder.withIndent('  ').convert(result.toJson()),
  );

  stdout.writeln(
    'NAVIGATION_DRIVE_SIM_EVIDENCE_OK '
    'steps=${result.steps.length} '
    'output_dir=${outputDir.path}',
  );
}

class _Args {
  const _Args({required this.checkOnly, required this.outputDir});

  final bool checkOnly;
  final String outputDir;

  static _Args parse(List<String> args) {
    var checkOnly = false;
    var outputDir = 'output/navigation-drive-sim/latest';

    for (var index = 0; index < args.length; index += 1) {
      final arg = args[index];
      switch (arg) {
        case '--check-only':
          checkOnly = true;
        case '--output-dir':
          index += 1;
          if (index >= args.length) {
            _usageError('--output-dir requer um caminho');
          }
          outputDir = args[index];
        case '-h':
        case '--help':
          _printUsage();
          exit(0);
        default:
          _usageError('Opcao desconhecida: $arg');
      }
    }

    return _Args(checkOnly: checkOnly, outputDir: outputDir);
  }

  static Never _usageError(String message) {
    stderr.writeln(message);
    _printUsage();
    exit(2);
  }

  static void _printUsage() {
    stdout.writeln(
      'Uso: dart run tool/navigation_drive_sim_report.dart '
      '[--check-only] [--output-dir <path>]',
    );
  }
}
