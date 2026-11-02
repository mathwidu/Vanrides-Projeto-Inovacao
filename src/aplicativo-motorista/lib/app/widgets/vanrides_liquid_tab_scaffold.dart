import 'package:flutter/material.dart';

import '../theme/vanrides_design_tokens.dart';
import '../theme/vanrides_motion.dart';

class VanRidesLiquidTabScaffold extends StatelessWidget {
  const VanRidesLiquidTabScaffold({
    super.key,
    required this.selectedIndex,
    required this.pages,
    this.backgroundColor = VanRidesDesignTokens.backgroundDeep,
    this.extendBody = true,
    this.overlays = const [],
  });

  static const bodyKey = ValueKey('vanrides.liquidTab.body');
  static const transitionKey = ValueKey('vanrides.liquidTab.transition');

  final int selectedIndex;
  final List<Widget> pages;
  final Color backgroundColor;
  final bool extendBody;
  final List<Widget> overlays;

  @override
  Widget build(BuildContext context) {
    final motion = VanRidesMotionPolicy.of(context);

    return Scaffold(
      extendBody: extendBody,
      backgroundColor: backgroundColor,
      body: Stack(
        children: [
          Positioned.fill(
            child: IndexedStack(
              key: bodyKey,
              index: selectedIndex,
              children: [
                for (var index = 0; index < pages.length; index += 1)
                  TickerMode(
                    enabled: index == selectedIndex,
                    child: pages[index],
                  ),
              ],
            ),
          ),
          Positioned.fill(
            child: IgnorePointer(
              child: TweenAnimationBuilder<double>(
                key: ValueKey('vanrides-tab-transition-$selectedIndex'),
                tween: Tween<double>(
                  begin: motion.shouldAnimate ? 1 : 0,
                  end: 0,
                ),
                duration: motion.duration(VanRidesMotionTokens.standard),
                curve: motion.curve(VanRidesMotionTokens.easeOutQuart),
                builder: (context, value, child) {
                  return ColoredBox(
                    key: transitionKey,
                    color: backgroundColor.withValues(alpha: value * 0.10),
                  );
                },
              ),
            ),
          ),
          ...overlays,
        ],
      ),
    );
  }
}
