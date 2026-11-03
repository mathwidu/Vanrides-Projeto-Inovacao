import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';

class DriverLocationSignalHint extends StatelessWidget {
  const DriverLocationSignalHint({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Icon(
          Icons.info_outline_rounded,
          size: 16,
          color: VanRidesMapTokens.amberWarning,
        ),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: VanRidesMapTokens.amberWarning,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ],
    );
  }
}
