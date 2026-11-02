import 'package:flutter/material.dart';

import '../data/driver_tracking_platform.dart';
import 'driver_tracking_setup.dart';

class DriverSetupApp extends StatelessWidget {
  const DriverSetupApp({
    this.platform = const NativeDriverTrackingPlatform(),
    super.key,
  });
  final DriverTrackingPlatform platform;

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'VanRides Motorista',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF176B52)),
    ),
    home: DriverTrackingSetup(platform: platform),
  );
}
