import 'package:flutter/material.dart';

class AdminPortalTextField extends StatelessWidget {
  const AdminPortalTextField({
    super.key,
    required this.label,
    this.hint,
    this.controller,
    this.initialValue,
    this.validator,
    this.obscureText = false,
    this.keyboardType,
    this.maxLines = 1,
    this.required = false,
    this.onChanged,
    this.enabled = true,
  });

  final String label;
  final String? hint;
  final TextEditingController? controller;
  final String? initialValue;
  final String? Function(String?)? validator;
  final bool obscureText;
  final TextInputType? keyboardType;
  final int? maxLines;
  final bool required;
  final ValueChanged<String>? onChanged;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final labelText = required ? '$label *' : label;
    return TextFormField(
      controller: controller,
      initialValue: initialValue,
      validator: validator,
      obscureText: obscureText,
      keyboardType: keyboardType,
      maxLines: maxLines,
      onChanged: onChanged,
      enabled: enabled,
      decoration: InputDecoration(
        labelText: labelText,
        hintText: hint,
        border: const OutlineInputBorder(),
      ),
    );
  }
}
