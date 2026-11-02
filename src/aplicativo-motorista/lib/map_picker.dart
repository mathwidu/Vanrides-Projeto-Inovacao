import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

import 'app/theme/vanrides_design_tokens.dart';
import 'features/tracking/presentation/widgets/vanrides_glass_surface.dart';
import 'features/tracking/presentation/widgets/vanrides_dark_map_style.dart';

class MapPickerScreen extends StatefulWidget {
  final LatLng initialPosition;
  final Function(LatLng) onConfirm;

  const MapPickerScreen({
    super.key,
    required this.initialPosition,
    required this.onConfirm,
  });

  @override
  State<MapPickerScreen> createState() => _MapPickerScreenState();
}

class _MapPickerScreenState extends State<MapPickerScreen> {
  late LatLng currentPosition;
  GoogleMapController? mapController;

  @override
  void initState() {
    super.initState();
    currentPosition = widget.initialPosition;
  }

  void onMapCreated(GoogleMapController controller) {
    mapController = controller;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: VanRidesDesignTokens.backgroundDeep,
      appBar: AppBar(
        backgroundColor: VanRidesDesignTokens.backgroundDeep,
        foregroundColor: VanRidesDesignTokens.textPrimary,
        surfaceTintColor: Colors.transparent,
        title: const Text('Ajustar Localização'),
      ),
      body: Stack(
        children: [
          GoogleMap(
            style: vanRidesDarkGoogleMapStyle,
            initialCameraPosition: CameraPosition(
              target: currentPosition,
              zoom: 16,
            ),
            onMapCreated: onMapCreated,
            markers: {
              Marker(
                markerId: const MarkerId('selected-location'),
                position: currentPosition,
                icon: BitmapDescriptor.defaultMarkerWithHue(
                  BitmapDescriptor.hueAzure,
                ),
                draggable: true,
                onDragEnd: (LatLng newPosition) {
                  setState(() {
                    currentPosition = newPosition;
                  });
                },
              ),
            },
          ),
          Positioned(
            bottom: 30,
            left: 20,
            right: 20,
            child: SafeArea(
              top: false,
              child: VanRidesGlassSurface.action(
                borderRadius: 22,
                padding: const EdgeInsets.all(8),
                child: FilledButton.icon(
                  icon: const Icon(Icons.check_rounded),
                  label: const Text('Confirmar Localização'),
                  onPressed: () {
                    widget.onConfirm(currentPosition);
                    Navigator.of(context).pop();
                  },
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(56),
                    backgroundColor: VanRidesDesignTokens.greenAction,
                    foregroundColor: VanRidesDesignTokens.onAccent,
                    textStyle: Theme.of(context).textTheme.titleMedium
                        ?.copyWith(fontWeight: FontWeight.w800),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(18),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
