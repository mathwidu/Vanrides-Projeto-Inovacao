import 'dart:convert';

import 'package:http/http.dart' as http;

String decodeApiResponseBody(http.Response response) {
  try {
    return utf8.decode(response.bodyBytes);
  } on FormatException {
    return response.body;
  }
}
