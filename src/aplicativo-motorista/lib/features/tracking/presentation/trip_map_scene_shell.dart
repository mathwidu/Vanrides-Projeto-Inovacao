import 'package:flutter/material.dart';

class TripMapSceneShell extends StatelessWidget {
  const TripMapSceneShell({
    super.key,
    required this.map,
    required this.topBar,
    required this.sheetChild,
    this.sheetMaxHeightFactor = 0.46,
    this.horizontalPadding = 16,
    this.floatingAction,
    this.floatingActionBottomInset = 122,
    this.sheetColor = const Color(0xF7FFFFFF),
    this.sheetBorderRadius = 30,
    this.showSheetHandle = true,
    this.sheetPadding = const EdgeInsets.fromLTRB(22, 14, 22, 22),
    this.topPadding = 16,
    this.sheetBottomInset = 12,
  });

  final Widget map;
  final Widget topBar;
  final Widget sheetChild;
  final double sheetMaxHeightFactor;
  final double horizontalPadding;
  final Widget? floatingAction;
  final double floatingActionBottomInset;
  final Color sheetColor;
  final double sheetBorderRadius;
  final bool showSheetHandle;
  final EdgeInsets sheetPadding;
  final double topPadding;
  final double sheetBottomInset;

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final maxSheetHeight = mediaQuery.size.height * sheetMaxHeightFactor;

    return Stack(
      children: [
        Positioned.fill(child: map),
        Positioned.fill(
          child: IgnorePointer(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.black.withValues(alpha: 0.04),
                    Colors.transparent,
                    Colors.black.withValues(alpha: 0.16),
                  ],
                  stops: const [0, 0.32, 1],
                ),
              ),
            ),
          ),
        ),
        SafeArea(
          child: Padding(
            padding: EdgeInsets.fromLTRB(
              horizontalPadding,
              topPadding,
              horizontalPadding,
              0,
            ),
            child: topBar,
          ),
        ),
        if (floatingAction != null)
          Positioned(
            left: horizontalPadding,
            right: horizontalPadding,
            bottom: floatingActionBottomInset,
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 520),
                child: floatingAction!,
              ),
            ),
          ),
        SafeArea(
          child: Align(
            alignment: Alignment.bottomCenter,
            child: Padding(
              padding: EdgeInsets.fromLTRB(
                horizontalPadding,
                0,
                horizontalPadding,
                sheetBottomInset,
              ),
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: 760,
                  maxHeight: maxSheetHeight,
                ),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(sheetBorderRadius),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x1A161B36),
                        blurRadius: 30,
                        offset: Offset(0, 16),
                      ),
                    ],
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(sheetBorderRadius),
                    child: DecoratedBox(
                      decoration: BoxDecoration(color: sheetColor),
                      child: SingleChildScrollView(
                        padding: sheetPadding,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            if (showSheetHandle) ...[
                              Align(
                                child: Container(
                                  width: 54,
                                  height: 6,
                                  decoration: BoxDecoration(
                                    color: const Color(0xFFE4E7EF),
                                    borderRadius: BorderRadius.circular(99),
                                  ),
                                ),
                              ),
                              const SizedBox(height: 14),
                            ],
                            sheetChild,
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
