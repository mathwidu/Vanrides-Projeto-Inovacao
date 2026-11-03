import 'package:flutter/material.dart';

class OnboardingBlurDot extends StatelessWidget {
  const OnboardingBlurDot({
    super.key,
    required this.color,
    required this.size,
    this.top,
    this.left,
    this.right,
    this.bottom,
  });

  final Color color;
  final double size;
  final double? top;
  final double? left;
  final double? right;
  final double? bottom;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: top,
      left: left,
      right: right,
      bottom: bottom,
      child: IgnorePointer(
        child: Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(size),
          ),
        ),
      ),
    );
  }
}
