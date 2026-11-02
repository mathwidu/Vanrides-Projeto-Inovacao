#if VANRIDES_DRIVER_MVP
import Flutter
import Foundation
import UIKit

final class DriverSessionBridge: NSObject, FlutterPlugin {
  static let controller: DriverSessionController? = {
    let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    let origin = Bundle.main.object(forInfoDictionaryKey: "VANRIDES_PAIRING_API_ORIGIN") as? String
    return try? DriverSessionController(configuration: DriverSessionConfiguration(origin: origin, appVersion: version),
      store: DriverSessionKeychainStore(), keys: DriverSessionSecureEnclaveKeys(), http: DriverSessionURLSessionHTTP())
  }()
  private var controller: DriverSessionController? { Self.controller }


  static func register(with registrar: FlutterPluginRegistrar) {
    let bridge = DriverSessionBridge()
    let channel = FlutterMethodChannel(name: "dev.matheusduarte.vanrides.driver/session.v1",
      binaryMessenger: registrar.messenger())
    registrar.addMethodCallDelegate(bridge, channel: channel)
    let launchChannel = FlutterMethodChannel(name: "dev.matheusduarte.vanrides.driver/launch.v1",
      binaryMessenger: registrar.messenger())
    registrar.addMethodCallDelegate(DriverLauncherVisitBridge(), channel: launchChannel)
  }

  func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    guard ["getState", "beginPairing", "syncSession", "refreshSession", "revokeSession", "showPairingCode"].contains(call.method)
    else { result(FlutterMethodNotImplemented); return }
    guard call.arguments == nil || call.arguments is NSNull else {
      result(FlutterError(code: DriverSessionFailure.invalidArguments.rawValue, message: nil, details: nil)); return
    }
    guard let controller = controller else {
      result(FlutterError(code: DriverSessionFailure.storage.rawValue, message: nil, details: nil)); return
    }
    // No URL handler calls beginPairing. Only the explicit Flutter pairing action
    // reaches it; returning from WhatsApp calls syncSession for the existing record.
    // Flutter obtains the driver's confirmation before invoking revokeSession.
    Task {
      do {
        let value: Any?
        switch call.method {
        case "getState": value = await controller.snapshot()
        case "showPairingCode": value = await controller.showPairingCode()
        default: value = try await controller.perform(call.method)
        }
        DispatchQueue.main.async { result(value) }
      } catch {
        let code = (error as? DriverSessionFailure ?? .unavailable).rawValue
        DispatchQueue.main.async { result(FlutterError(code: code, message: nil, details: nil)) }
      }
    }
  }
}

private final class DriverLauncherVisitBridge: NSObject, FlutterPlugin {
  static func register(with registrar: FlutterPluginRegistrar) {}

  func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    guard call.method == "isLauncherVisit" else { result(FlutterMethodNotImplemented); return }
    guard call.arguments == nil || call.arguments is NSNull else {
      result(FlutterError(code: DriverSessionFailure.invalidArguments.rawValue, message: nil, details: nil)); return
    }
    // Becoming visible does not establish an icon launch: UIScene may be returning
    // from a link. Until that provenance is proved, require a cold icon visit.
    result(false)
  }
}

// Driver-only screen cover for app-switcher snapshots. The Flutter lifecycle also
// hides the transient code; this cover protects the native window during transition.
final class DriverSessionPrivacyCover {
  static let shared = DriverSessionPrivacyCover()
  private var covers: [UIView] = []
  private var observers: [NSObjectProtocol] = []

  private init() {}

  func install() {
    guard observers.isEmpty else { return }
    for name in [UIApplication.willResignActiveNotification, UIScene.willDeactivateNotification] {
      observers.append(NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
        self?.cover()
      })
    }
    for name in [UIApplication.didBecomeActiveNotification, UIScene.didActivateNotification] {
      observers.append(NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
        self?.covers.forEach { $0.removeFromSuperview() }; self?.covers.removeAll()
      })
    }
  }

  private func cover() {
    covers.forEach { $0.removeFromSuperview() }; covers.removeAll()
    for scene in UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }) {
      for window in scene.windows where !window.isHidden {
        let cover = UIView(frame: window.bounds)
        cover.backgroundColor = .black
        cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        cover.accessibilityElementsHidden = true
        cover.isUserInteractionEnabled = false
        window.addSubview(cover); covers.append(cover)
      }
    }
  }
}
#endif
