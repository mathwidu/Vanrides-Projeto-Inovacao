#if VANRIDES_DRIVER_MVP || DRIVER_SESSION_STANDALONE_TESTS
import Foundation
import Security
import CryptoKit

final class DriverSessionKeychainStore: DriverSessionStore {
  private let service: String
  init(service: String = "dev.matheusduarte.vanrides.driver.session.v1") { self.service = service }

  private var query: [String: Any] {
    [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
      kSecAttrAccount as String: "atomic-session-journal-v1", kSecAttrSynchronizable as String: false]
  }

  func load() throws -> Data? {
    var query = self.query
    query[kSecReturnData as String] = true
    query[kSecMatchLimit as String] = kSecMatchLimitOne
    var result: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &result)
    if status == errSecItemNotFound { return nil }
    guard status == errSecSuccess, let data = result as? Data, data.count <= 65_536 else {
      throw DriverSessionFailure.storage
    }
    return data
  }

  func save(_ data: Data) throws {
    guard data.count <= 65_536 else { throw DriverSessionFailure.storage }
    let attributes: [String: Any] = [kSecValueData as String: data,
      kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
    let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
    if status == errSecItemNotFound {
      var item = query
      attributes.forEach { item[$0.key] = $0.value }
      guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else { throw DriverSessionFailure.storage }
    } else if status != errSecSuccess { throw DriverSessionFailure.storage }
  }
}

final class DriverSessionSecureEnclaveKeys: DriverSessionKeyProvider {
  func random(count: Int) throws -> Data {
    guard (1...128).contains(count) else { throw DriverSessionFailure.key }
    var bytes = [UInt8](repeating: 0, count: count)
    guard SecRandomCopyBytes(kSecRandomDefault, count, &bytes) == errSecSuccess else { throw DriverSessionFailure.key }
    return Data(bytes)
  }

  func key(tag: String, create: Bool) throws -> DriverSessionSigningKey {
    guard UUID(uuidString: tag) != nil else { throw DriverSessionFailure.key }
    let applicationTag = Data(("dev.matheusduarte.vanrides.driver.session.v1." + tag).utf8)
    let query: [String: Any] = [kSecClass as String: kSecClassKey,
      kSecAttrApplicationTag as String: applicationTag, kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
      kSecAttrKeyClass as String: kSecAttrKeyClassPrivate, kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
      kSecReturnRef as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
    var result: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &result)
    if status == errSecSuccess, let result = result {
      return try DriverSessionSecKey(key: result as! SecKey)
    }
    guard create, status == errSecItemNotFound else { throw DriverSessionFailure.key }
    var error: Unmanaged<CFError>?
    guard let control = SecAccessControlCreateWithFlags(nil,
      kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly, [.privateKeyUsage], &error) else {
      throw DriverSessionFailure.key
    }
    let attributes: [String: Any] = [kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
      kSecAttrKeySizeInBits as String: 256, kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
      kSecPrivateKeyAttrs as String: [kSecAttrIsPermanent as String: true,
        kSecAttrApplicationTag as String: applicationTag, kSecAttrAccessControl as String: control]]
    // Fail closed on hosts/simulators without Secure Enclave. Ephemeral keys are
    // supplied only by test fixtures, never as a runtime fallback in this class.
    guard let key = SecKeyCreateRandomKey(attributes as CFDictionary, &error) else { throw DriverSessionFailure.key }
    return try DriverSessionSecKey(key: key)
  }
}

final class DriverSessionSecKey: DriverSessionSigningKey {
  private let key: SecKey
  private let jwk: [String: String]

  init(key: SecKey) throws {
    self.key = key
    var error: Unmanaged<CFError>?
    guard SecKeyIsAlgorithmSupported(key, .sign, .ecdsaSignatureMessageX962SHA256),
      let publicKey = SecKeyCopyPublicKey(key),
      let bytes = SecKeyCopyExternalRepresentation(publicKey, &error) as Data?,
      bytes.count == 65, bytes.first == 4 else { throw DriverSessionFailure.key }
    jwk = ["kty": "EC", "crv": "P-256", "x": DriverSessionCodec.base64(bytes.subdata(in: 1..<33)),
      "y": DriverSessionCodec.base64(bytes.subdata(in: 33..<65))]
  }

  var publicJWK: [String: String] { jwk }

  func sign(_ message: Data) throws -> Data {
    var error: Unmanaged<CFError>?
    guard let der = SecKeyCreateSignature(key, .ecdsaSignatureMessageX962SHA256,
      message as CFData, &error) as Data?,
      let signature = try? P256.Signing.ECDSASignature(derRepresentation: der) else { throw DriverSessionFailure.key }
    return signature.rawRepresentation
  }
}

// One ephemeral session per bounded request. Redirects, cookies, credentials and
// caches are disabled; default platform TLS trust remains mandatory. Response
// streaming cancels at the cap instead of buffering an unbounded dataTask body.
final class DriverSessionURLSessionHTTP: DriverSessionHTTP {
  func send(_ request: URLRequest) async throws -> DriverSessionHTTPResponse {
    try await withCheckedThrowingContinuation { continuation in
      DriverSessionURLRequest.start(request, continuation: continuation)
    }
  }
}

private final class DriverSessionURLRequest: NSObject, URLSessionDataDelegate, URLSessionTaskDelegate, @unchecked Sendable {
  private var continuation: CheckedContinuation<DriverSessionHTTPResponse, Error>?
  private var session: URLSession?
  private var response: HTTPURLResponse?
  private var bytes = Data()
  private var rejectedResponse = false
  private let expectedURL: URL?

  private init(request: URLRequest, continuation: CheckedContinuation<DriverSessionHTTPResponse, Error>) {
    self.expectedURL = request.url; self.continuation = continuation
  }

  static func start(_ request: URLRequest, continuation: CheckedContinuation<DriverSessionHTTPResponse, Error>) {
    guard request.url?.scheme == "https", request.httpBody?.count ?? 0 <= 32_768 else {
      continuation.resume(throwing: DriverSessionFailure.unavailable); return
    }
    let delegate = DriverSessionURLRequest(request: request, continuation: continuation)
    let configuration = URLSessionConfiguration.ephemeral
    configuration.httpShouldSetCookies = false; configuration.httpCookieStorage = nil
    configuration.urlCredentialStorage = nil; configuration.urlCache = nil
    configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
    configuration.timeoutIntervalForRequest = 15; configuration.timeoutIntervalForResource = 25
    configuration.httpMaximumConnectionsPerHost = 1
    let session = URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
    delegate.session = session
    session.dataTask(with: request).resume()
  }

  func urlSession(_ session: URLSession, task: URLSessionTask,
                  willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                  completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }

  func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                  completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
    guard let http = response as? HTTPURLResponse, http.url == expectedURL,
      response.expectedContentLength <= 32_768,
      http.value(forHTTPHeaderField: "Content-Type")?.lowercased().split(separator: ";").first == "application/json"
    else { rejectedResponse = true; completionHandler(.cancel); return }
    self.response = http
    completionHandler(.allow)
  }

  func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
    guard bytes.count + data.count <= 32_768 else { rejectedResponse = true; dataTask.cancel(); return }
    bytes.append(data)
  }

  func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
    let continuation = self.continuation; self.continuation = nil
    defer { session.finishTasksAndInvalidate(); self.session = nil }
    if rejectedResponse {
      continuation?.resume(throwing: DriverSessionFailure.response); return
    }
    guard error == nil, let response = response else {
      continuation?.resume(throwing: DriverSessionFailure.network); return
    }
    continuation?.resume(returning: DriverSessionHTTPResponse(status: response.statusCode,
      nonce: response.value(forHTTPHeaderField: "DPoP-Nonce"), body: bytes))
  }
}
#endif
