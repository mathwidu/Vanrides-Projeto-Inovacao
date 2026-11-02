import 'dart:async';

import 'package:flutter/material.dart';

import '../../data/admin_places_gateway.dart';
import 'admin_portal_layout.dart';

typedef AdminDestinationSelected =
    void Function(
      String nome,
      String? endereco,
      double latitude,
      double longitude,
    );

typedef AdminDestinationPickerDecorationBuilder =
    InputDecoration Function(bool loading);

class AdminDestinationPicker extends StatefulWidget {
  const AdminDestinationPicker({
    super.key,
    required this.placesGateway,
    required this.onSelected,
    required this.onCleared,
    this.nome,
    this.endereco,
    this.searchDecorationBuilder,
    this.debounceDuration = const Duration(milliseconds: 350),
    this.confirmedPadding = const EdgeInsets.symmetric(
      horizontal: 18,
      vertical: 16,
    ),
  });

  final AdminPlacesGateway placesGateway;
  final String? nome;
  final String? endereco;
  final AdminDestinationSelected onSelected;
  final VoidCallback onCleared;
  final AdminDestinationPickerDecorationBuilder? searchDecorationBuilder;
  final Duration debounceDuration;
  final EdgeInsetsGeometry confirmedPadding;

  @override
  State<AdminDestinationPicker> createState() => _AdminDestinationPickerState();
}

class _AdminDestinationPickerState extends State<AdminDestinationPicker> {
  final _searchController = TextEditingController();
  List<AdminPlacePrediction> _suggestions = const [];
  bool _loading = false;
  Timer? _debounce;

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _search(String query) async {
    _debounce?.cancel();
    final trimmed = query.trim();
    if (trimmed.isEmpty) {
      setState(() {
        _suggestions = const [];
        _loading = false;
      });
      return;
    }

    _debounce = Timer(widget.debounceDuration, () async {
      if (!mounted) return;
      setState(() => _loading = true);
      try {
        final suggestions = await widget.placesGateway.autocomplete(trimmed);
        if (!mounted) return;
        setState(() {
          _suggestions = suggestions;
          _loading = false;
        });
      } catch (_) {
        if (!mounted) return;
        setState(() {
          _suggestions = const [];
          _loading = false;
        });
      }
    });
  }

  Future<void> _selectPrediction(AdminPlacePrediction prediction) async {
    setState(() => _loading = true);
    try {
      final details = await widget.placesGateway.fetchDetails(
        prediction.placeId,
      );
      if (!mounted) return;
      setState(() {
        _suggestions = const [];
        _loading = false;
      });
      _searchController.clear();
      widget.onSelected(
        prediction.mainText,
        details.endereco,
        details.latitude,
        details.longitude,
      );
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _clear() {
    _debounce?.cancel();
    setState(() {
      _suggestions = const [];
      _loading = false;
      _searchController.clear();
    });
    widget.onCleared();
  }

  @override
  Widget build(BuildContext context) {
    final nome = widget.nome;
    if (nome != null) {
      return Container(
        padding: widget.confirmedPadding,
        decoration: BoxDecoration(
          color: AdminPortalColors.input,
          borderRadius: BorderRadius.circular(18),
        ),
        child: Row(
          children: [
            const Icon(
              Icons.place_rounded,
              size: 20,
              color: AdminPortalColors.blue,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    nome,
                    style: const TextStyle(
                      color: AdminPortalColors.text,
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  if (widget.endereco != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      widget.endereco!,
                      style: const TextStyle(
                        color: AdminPortalColors.textMuted,
                        fontSize: 13,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            IconButton(
              tooltip: 'Remover destino',
              onPressed: _clear,
              icon: const Icon(Icons.close_rounded),
              color: AdminPortalColors.textMuted,
            ),
          ],
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextFormField(
          controller: _searchController,
          decoration: _buildDecoration(),
          onChanged: _search,
        ),
        if (_suggestions.isNotEmpty) ...[
          const SizedBox(height: 8),
          Container(
            decoration: BoxDecoration(
              color: AdminPortalColors.surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AdminPortalColors.outline),
            ),
            child: Column(
              children: _suggestions
                  .map(
                    (prediction) => InkWell(
                      onTap: () => _selectPrediction(prediction),
                      borderRadius: BorderRadius.circular(16),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 14,
                        ),
                        child: Row(
                          children: [
                            const Icon(
                              Icons.place_outlined,
                              size: 18,
                              color: AdminPortalColors.textMuted,
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    prediction.mainText,
                                    style: const TextStyle(
                                      color: AdminPortalColors.text,
                                      fontSize: 14,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  if (prediction.secondaryText.isNotEmpty)
                                    Text(
                                      prediction.secondaryText,
                                      style: const TextStyle(
                                        color: AdminPortalColors.textMuted,
                                        fontSize: 12,
                                      ),
                                    ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
          ),
        ],
      ],
    );
  }

  InputDecoration _buildDecoration() {
    final customBuilder = widget.searchDecorationBuilder;
    if (customBuilder != null) {
      return customBuilder(_loading);
    }

    return InputDecoration(
      labelText: 'Pesquisar destino final',
      filled: true,
      fillColor: AdminPortalColors.input,
      prefixIcon: const Icon(Icons.search_rounded),
      suffixIcon: _loading ? const _DestinationPickerLoadingIcon() : null,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: BorderSide.none,
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: BorderSide.none,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: const BorderSide(color: AdminPortalColors.blue, width: 1.5),
      ),
    );
  }
}

class _DestinationPickerLoadingIcon extends StatelessWidget {
  const _DestinationPickerLoadingIcon();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.all(14),
      child: SizedBox(
        width: 16,
        height: 16,
        child: CircularProgressIndicator(strokeWidth: 2),
      ),
    );
  }
}
