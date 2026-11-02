class GuardianLocalizacaoSnapshot {
  const GuardianLocalizacaoSnapshot({
    required this.latitude,
    required this.longitude,
    required this.dataHora,
  });

  final double latitude;
  final double longitude;
  final DateTime dataHora;

  factory GuardianLocalizacaoSnapshot.fromJson(Map<String, dynamic> json) {
    return GuardianLocalizacaoSnapshot(
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      dataHora: DateTime.parse(json['dataHora'] as String).toLocal(),
    );
  }
}
