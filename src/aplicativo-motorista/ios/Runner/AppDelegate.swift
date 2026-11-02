import Flutter
import UIKit
#if !VANRIDES_DRIVER_MVP
import GoogleMaps
#endif

#if VANRIDES_DRIVER_MVP
// Observe raw URLs before app_links/Uri can normalize percent-encoded input.
enum DriverRunEntry {
  static var authorization: DriverRunLink?
  static func observe(_ urls: [URL]) {
    let host = Bundle.main.object(forInfoDictionaryKey: "VANRIDES_PAIRING_LINK_HOST") as? String ?? ""
    authorization = urls.count == 1 ? DriverRunLink.parse(urls[0].absoluteString, host: host) : nil
  }
}

@objc(DriverSceneDelegate)
class DriverSceneDelegate: FlutterSceneDelegate {
  override func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
    DriverRunEntry.observe(connectionOptions.urlContexts.map(\.url) + connectionOptions.userActivities.compactMap(\.webpageURL))
    super.scene(scene, willConnectTo: session, options: connectionOptions)
  }
  override func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
    DriverRunEntry.observe(URLContexts.map(\.url))
    super.scene(scene, openURLContexts: URLContexts)
  }
  override func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
    DriverRunEntry.observe(userActivity.webpageURL.map { [$0] } ?? [])
    super.scene(scene, continue: userActivity)
  }
}

// Xcode 27 requires UIScene. Keep the companion lifecycle independent of legacy.
@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    _ = DriverTrackingCoordinator.shared
    DriverSessionPrivacyCover.shared.install()
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
    if let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "DriverTrackingBridge") {
      DriverTrackingBridge.register(with: registrar)
    }
    if let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "DriverSessionBridge") {
      DriverSessionBridge.register(with: registrar)
    }
  }
}
#else
@main
@objc class AppDelegate: FlutterAppDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {

    // ✅ Inicializa o SDK do Google Maps usando a chave injetada no Info.plist.
    if let mapsApiKey = Bundle.main.object(forInfoDictionaryKey: "GOOGLE_MAPS_API_KEY") as? String,
       !mapsApiKey.isEmpty {
      GMSServices.provideAPIKey(mapsApiKey)
    } else {
      print("[VanRides][WARN] GOOGLE_MAPS_API_KEY ausente. O mapa pode nao funcionar no iOS.")
    }

    GeneratedPluginRegistrant.register(with: self)
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
#endif
