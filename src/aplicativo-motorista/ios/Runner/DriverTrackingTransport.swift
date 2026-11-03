#if VANRIDES_DRIVER_MVP
import Foundation
import Security
import Darwin

enum DriverTrackingClock {
  // Unlike systemUptime/mach_absolute_time, this clock includes device sleep.
  static func now() -> Double {
    var scale = mach_timebase_info_data_t()
    mach_timebase_info(&scale)
    return Double(mach_continuous_time()) * Double(scale.numer) / Double(scale.denom) / 1_000_000_000
  }

  static func wallMilliseconds() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}

struct DriverTrackingCredential: Codable {
  #if VANRIDES_GPS_SPIKE
  let baseURL: String
  let token: String

  private static let service = "dev.matheusduarte.vanrides.driver.gps-spike.v1"
  private static var query: [String: Any] {
    [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
      kSecAttrAccount as String: "test-bearer", kSecAttrSynchronizable as String: false]
  }

  static var allowSimulatorHTTP: Bool {
    #if DEBUG && targetEnvironment(simulator)
    return true
    #else
    return false
    #endif
  }

  func validatedURL() throws -> URL {
    guard token.utf8.count >= 32, token.utf8.count <= 512,
      token.utf8.allSatisfy({ (33...126).contains($0) }) else {
      throw DriverTrackingFailure.invalidCredential
    }
    return try DriverTrackingContract.endpoint(baseURL, allowSimulatorHTTP: Self.allowSimulatorHTTP)
  }

  func save() throws {
    _ = try validatedURL()
    let data = try JSONEncoder().encode(self)
    let attributes: [String: Any] = [kSecValueData as String: data,
      kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
    let result = SecItemUpdate(Self.query as CFDictionary, attributes as CFDictionary)
    if result == errSecItemNotFound {
      var item = Self.query
      attributes.forEach { item[$0.key] = $0.value }
      guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else {
        throw DriverTrackingFailure.credentialStorage
      }
    } else if result != errSecSuccess {
      throw DriverTrackingFailure.credentialStorage
    }
  }

  static func load() -> DriverTrackingCredential? {
    var item = query
    item[kSecReturnData as String] = true
    item[kSecMatchLimit as String] = kSecMatchLimitOne
    var result: CFTypeRef?
    guard SecItemCopyMatching(item as CFDictionary, &result) == errSecSuccess,
      let data = result as? Data, let value = try? JSONDecoder().decode(Self.self, from: data),
      (try? value.validatedURL()) != nil else { return nil }
    return value
  }
  #else
  let baseURL: String
  let token: String
  func save() throws { throw DriverTrackingFailure.invalidCredential }
  static func load() -> DriverTrackingCredential? { DriverTrackingCredential(baseURL: "", token: "") }
  #endif
}

final class DriverTrackingRequest {
  private let start: () -> Void
  private let cancelRequest: () -> Void
  init(start: @escaping () -> Void, cancel: @escaping () -> Void) { self.start = start; self.cancelRequest = cancel }
  func resume() { start() }
  func cancel() { cancelRequest() }
}

// This session is deliberately ephemeral, never a background upload session.
final class DriverTrackingTransport: NSObject, URLSessionTaskDelegate {
  private let production = DriverProductionTrackingWire()
  var stopReference: String? { production.stopReference }
  #if VANRIDES_GPS_SPIKE
  private lazy var session: URLSession = {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.timeoutIntervalForRequest = 8
    configuration.timeoutIntervalForResource = 8
    configuration.waitsForConnectivity = false
    configuration.urlCache = nil
    configuration.urlCredentialStorage = nil
    configuration.httpCookieStorage = nil
    configuration.httpShouldSetCookies = false
    configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
    return URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
  }()

  #endif

  func request(credential: DriverTrackingCredential, path: String, body: Data?,
    completion: @escaping (Result<[String: Any], DriverTrackingFailure>, Int?) -> Void
  ) throws -> DriverTrackingRequest {
    #if VANRIDES_GPS_SPIKE
    let base = try credential.validatedURL()
    guard let url = URL(string: path, relativeTo: base)?.absoluteURL,
      url.host == base.host, url.scheme == base.scheme, url.port == base.port else {
      throw DriverTrackingFailure.invalidEndpoint
    }
    var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 8)
    request.httpMethod = body == nil ? "GET" : "POST"
    request.setValue("Bearer " + credential.token, forHTTPHeaderField: "Authorization")
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.setValue("application/json", forHTTPHeaderField: "Accept")
    request.httpBody = body
    let task = session.dataTask(with: request) { data, response, error in
      let response = response as? HTTPURLResponse
      let status = response?.statusCode
      let result: Result<[String: Any], DriverTrackingFailure>
      if error != nil { result = .failure(.network) }
      else if status != 200 { result = .failure(.serverRejected) }
      else if let data = data, data.count <= 65_536,
        response?.mimeType?.lowercased() == "application/json",
        let object = try? JSONSerialization.jsonObject(with: data), let json = object as? [String: Any] {
        result = .success(json)
      } else { result = .failure(.invalidContract) }
      DispatchQueue.main.async { completion(result, status) }
    }
    return DriverTrackingRequest(start: { task.resume() }, cancel: { task.cancel() })
    #else
    let route = path == "/driver-app/v1/status" ? "/driver-app/v1/tracking-status" : path
    let outgoing = try production.outgoing(path: route, data: body)
    var operation: Task<Void, Never>?
    return DriverTrackingRequest(start: { [weak self] in
      operation = Task { @MainActor [weak self] in
        guard let self = self else { return }
        do {
          guard let controller = DriverSessionBridge.controller else { throw DriverSessionFailure.unavailable }
          let data = try await controller.trackingRequest(path: route, body: outgoing.0, idempotency: outgoing.1)
          completion(.success(try self.production.incoming(path: route, data: data)), 200)
        } catch let failure as DriverTrackingFailure {
          completion(.failure(failure), 200)
        } catch DriverSessionFailure.busy {
          completion(.failure(.network), 429)
        } catch let failure as DriverSessionRemoteFailure {
          completion(.failure(.serverRejected), failure.status)
        } catch {
          let failure: DriverTrackingFailure = (error as? DriverSessionFailure) == .network ? .network : .serverRejected
          completion(.failure(failure), failure == .network ? nil : 401)
        }
      }
    }, cancel: { operation?.cancel() })
    #endif
  }

  func urlSession(_ session: URLSession, task: URLSessionTask,
    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
    completionHandler: @escaping (URLRequest?) -> Void
  ) { completionHandler(nil) }
}
#endif
