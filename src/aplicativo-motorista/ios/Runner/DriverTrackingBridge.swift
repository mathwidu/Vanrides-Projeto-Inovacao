#if VANRIDES_DRIVER_MVP
import Flutter
import Foundation

final class DriverTrackingBridge: NSObject, FlutterPlugin {
  static func register(with registrar: FlutterPluginRegistrar) {
    let bridge = DriverTrackingBridge()
    let channel = FlutterMethodChannel(name: "vanrides.driver/tracking.v1", binaryMessenger: registrar.messenger())
    registrar.addMethodCallDelegate(bridge, channel: channel)
  }

  func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    let coordinator = DriverTrackingCoordinator.shared
    let reply: DriverTrackingCoordinator.Completion = { value in
      switch value {
      case .success(let snapshot): result(snapshot)
      case .failure(let failure): result(FlutterError(code: failure.rawValue, message: nil, details: nil))
      }
    }
    switch call.method {
    case "configureCredential":
      #if VANRIDES_GPS_SPIKE
      guard let args = call.arguments as? [String: Any], Set(args.keys) == ["kind", "baseUrl", "token"],
        args["kind"] as? String == "TEST_BEARER", let baseURL = args["baseUrl"] as? String,
        let token = args["token"] as? String else { reply(.failure(.invalidArguments)); return }
      coordinator.configure(DriverTrackingCredential(baseURL: baseURL, token: token), completion: reply)
      #else
      result(FlutterMethodNotImplemented)
      #endif
    case "startRun":
      guard let args = call.arguments as? [String: Any], Set(args.keys) == ["intentId", "expectedVersion", "intentToken"],
        let id = args["intentId"] as? String, (try? DriverSessionCodec.uuid(id)) == id,
        let token = args["intentToken"] as? String, DriverSessionCodec.canonicalToken(token),
        let version = try? DriverTrackingContract.integer(args["expectedVersion"]), version <= 9_007_199_254_740_991
      else { reply(.failure(.invalidArguments)); return }
      guard DriverRunEntry.authorization?.matches(id: id, version: version, token: token) == true
      else { reply(.failure(.invalidArguments)); return }
      coordinator.start(intentID: id, activation: ["expectedVersion": version,
        "foregroundConfirmed": "CONFIRMED", "intentToken": token], completion: reply)
    case "start":
      guard let args = call.arguments as? [String: Any], Set(args.keys) == ["intentId"],
        let intentID = args["intentId"] as? String, DriverTrackingContract.identifier(intentID) else {
        reply(.failure(.invalidArguments)); return
      }
      coordinator.start(intentID: intentID, completion: reply)
    case "requestPermission", "stop", "getStatus", "syncStatus":
      guard call.arguments == nil || call.arguments is NSNull else { reply(.failure(.invalidArguments)); return }
      switch call.method {
      case "requestPermission": coordinator.requestPermission(completion: reply)
      case "stop": coordinator.stop(); reply(.success(coordinator.snapshot()))
      case "getStatus": reply(.success(coordinator.snapshot()))
      default: coordinator.syncStatus(completion: reply)
      }
    default: result(FlutterMethodNotImplemented)
    }
  }
}
#endif
