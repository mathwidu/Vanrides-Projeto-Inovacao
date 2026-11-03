Map<String, Object?> energySnapshot({
  required int elapsedMs,
  int batteryPercent = 80,
  String powerSource = 'NONE',
}) => {
  'elapsedMs': elapsedMs,
  'batteryPercent': batteryPercent,
  'chargeState': powerSource == 'NONE' ? 'DISCHARGING' : 'CHARGING',
  'powerSource': powerSource,
  'powerSaveMode': false,
  'bootCount': 7,
};

Map<String, Object?> nativeEnergySession() => {
  'state': 'STOP_CONFIRMED',
  'observationMode': 'SESSION_BROADCASTS',
  'start': energySnapshot(elapsedMs: 1000, powerSource: 'USB'),
  'stop': energySnapshot(elapsedMs: 3601200, batteryPercent: 75),
  'unpluggedStart': energySnapshot(elapsedMs: 1100),
  'unpluggedEnd': energySnapshot(elapsedMs: 3601200, batteryPercent: 75),
  'unpluggedWindowState': 'STOP_CONFIRMED',
  'powerSaveModeChanged': false,
  'powerStateUncertain': false,
};
