import 'package:flutter/widgets.dart';

mixin AdminListStateMixin<T, W extends StatefulWidget> on State<W> {
  List<T> items = const [];
  bool isLoading = false;
  String? errorMessage;

  @protected
  void startLoading() {
    setState(() {
      isLoading = true;
      errorMessage = null;
    });
  }

  @protected
  void finishLoading([List<T>? nextItems]) {
    setState(() {
      if (nextItems != null) {
        items = nextItems;
      }
      isLoading = false;
    });
  }

  @protected
  void setLoadError(String message) {
    setState(() {
      errorMessage = message;
      isLoading = false;
    });
  }

  Future<void> loadItems(
    Future<List<T>> Function() fetcher, {
    String Function(Object error)? errorMessageBuilder,
    void Function(List<T> result)? onLoaded,
  }) async {
    startLoading();
    try {
      final result = await fetcher();
      setState(() {
        items = result;
        onLoaded?.call(result);
        isLoading = false;
      });
    } catch (error) {
      setLoadError(errorMessageBuilder?.call(error) ?? error.toString());
    }
  }

  void onItemCreated(T item) {
    setState(() => items = [item, ...items]);
  }

  void onItemUpdated(T item, {required bool Function(T) where}) {
    setState(
      () =>
          items = items
              .map((current) => where(current) ? item : current)
              .toList(growable: false),
    );
  }

  void onItemDeleted({required bool Function(T) where}) {
    setState(
      () =>
          items = items
              .where((current) => !where(current))
              .toList(growable: false),
    );
  }
}
