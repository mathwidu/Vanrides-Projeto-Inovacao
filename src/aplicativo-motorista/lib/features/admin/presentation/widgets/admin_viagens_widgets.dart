import 'package:flutter/material.dart';

export 'admin_viagens_list_widgets.dart';
export 'admin_viagens_day_status_panel.dart';
export 'admin_viagens_generation_result_widgets.dart';
export 'admin_viagens_status_widgets.dart';

import 'admin_portal_layout.dart';

class AdminViagensFieldLabel extends StatelessWidget {
  const AdminViagensFieldLabel({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: const TextStyle(
        color: AdminPortalColors.textMuted,
        fontSize: 14,
        fontWeight: FontWeight.w700,
      ),
    );
  }
}

class AdminViagensDateField extends StatelessWidget {
  const AdminViagensDateField({
    super.key,
    required this.label,
    required this.value,
    required this.onTap,
  });

  final String label;
  final String value;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AdminViagensFieldLabel(label: label),
        const SizedBox(height: 8),
        InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(18),
          child: InputDecorator(
            decoration: adminViagensInputDecoration(hintText: value),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    value,
                    style: const TextStyle(
                      color: AdminPortalColors.text,
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                const Icon(
                  Icons.calendar_today_outlined,
                  color: AdminPortalColors.textMuted,
                  size: 18,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

InputDecoration adminViagensInputDecoration({required String hintText}) {
  return InputDecoration(
    hintText: hintText,
    filled: true,
    fillColor: AdminPortalColors.input,
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
      borderSide: const BorderSide(color: AdminPortalColors.blueBright),
    ),
    contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
    hintStyle: const TextStyle(color: AdminPortalColors.textFaint),
  );
}

Widget adminViagensDatePickerBuilder(BuildContext context, Widget? child) {
  return Theme(
    data: Theme.of(context).copyWith(
      colorScheme: const ColorScheme.dark(
        primary: AdminPortalColors.blueBright,
        onPrimary: AdminPortalColors.canvas,
        surface: AdminPortalColors.surfaceSoft,
        onSurface: AdminPortalColors.text,
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: AdminPortalColors.surfaceSoft,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(foregroundColor: AdminPortalColors.blue),
      ),
    ),
    child: child ?? const SizedBox.shrink(),
  );
}

class AdminViagensDisabledField extends StatelessWidget {
  const AdminViagensDisabledField({
    super.key,
    required this.label,
    required this.value,
    required this.helper,
  });

  final String label;
  final String value;
  final String helper;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AdminViagensFieldLabel(label: label),
        const SizedBox(height: 8),
        InputDecorator(
          decoration: adminViagensInputDecoration(hintText: value),
          child: Text(
            value,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 16,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          helper,
          style: const TextStyle(
            color: AdminPortalColors.textMuted,
            fontSize: 11,
          ),
        ),
      ],
    );
  }
}

class AdminViagensDarkInfoLine extends StatelessWidget {
  const AdminViagensDarkInfoLine({
    super.key,
    required this.label,
    required this.value,
  });

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: Colors.white54,
            fontSize: 12,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          value,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 17,
            fontWeight: FontWeight.w700,
            height: 1.4,
          ),
        ),
      ],
    );
  }
}

class AdminViagensDarkMetricRow extends StatelessWidget {
  const AdminViagensDarkMetricRow({
    super.key,
    required this.leftLabel,
    required this.leftValue,
    required this.rightLabel,
    required this.rightValue,
  });

  final String leftLabel;
  final String leftValue;
  final String rightLabel;
  final String rightValue;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                leftLabel,
                style: const TextStyle(
                  color: Colors.white54,
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                leftValue,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                rightLabel,
                style: const TextStyle(
                  color: Colors.white54,
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                rightValue,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
