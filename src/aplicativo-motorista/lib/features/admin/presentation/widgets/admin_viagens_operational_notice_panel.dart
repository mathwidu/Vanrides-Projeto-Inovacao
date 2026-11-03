import 'package:flutter/material.dart';

import '../../domain/admin_viagem_bulk_create_result.dart';
import '../admin_viagens_review_intent.dart';
import 'admin_viagens_generation_result_widgets.dart';
import 'admin_viagens_review_intent_banner.dart';

Widget? adminViagensOperationalNoticePanel({
  required AdminViagensReviewIntent? reviewIntent,
  required AdminViagemBulkCreateResult? generationResult,
  required bool isGeneratingAutomatic,
  required VoidCallback onGenerateAutomatic,
  required ValueChanged<int> onOpenTrip,
}) {
  final children = <Widget>[
    if (reviewIntent != null)
      AdminViagensReviewIntentBanner(
        intent: reviewIntent,
        isGeneratingAutomatic: isGeneratingAutomatic,
        onGenerateAutomatic: onGenerateAutomatic,
      ),
    if (reviewIntent != null && generationResult != null)
      const SizedBox(height: 24),
    if (generationResult != null)
      AdminViagensGenerationResultPanel(
        result: generationResult,
        onOpenTrip: onOpenTrip,
      ),
  ];

  if (children.isEmpty) {
    return null;
  }
  return Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: children,
  );
}
