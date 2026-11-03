#if VANRIDES_DRIVER_MVP || DRIVER_SESSION_STANDALONE_TESTS
import Foundation
import CryptoKit

enum DriverSessionFailure: String, Error {
  case invalidArguments = "INVALID_ARGUMENTS"
  case unavailable = "SESSION_UNAVAILABLE"
  case busy = "BUSY"
  case network = "NETWORK_ERROR"
  case rateLimited = "RATE_LIMITED"
  case storage = "SECURE_STORAGE_UNAVAILABLE"
  case key = "DEVICE_KEY_UNAVAILABLE"
  case response = "INVALID_SESSION_RESPONSE"
}

struct DriverSessionConfiguration {
  let origin: String
  let appVersion: String

  init?(origin: String?, appVersion: String) {
    guard let origin = origin, !origin.contains("$"),
      let url = URLComponents(string: origin), url.scheme == "https",
      let host = url.host, !host.isEmpty, host.utf8.allSatisfy({ $0 < 128 }),
      url.user == nil, url.password == nil, url.query == nil, url.fragment == nil,
      url.percentEncodedPath.isEmpty, url.port == nil || (1...65535).contains(url.port!),
      url.string == origin, appVersion.range(of: "^[A-Za-z0-9.+_-]{1,40}$", options: .regularExpression) != nil
    else { return nil }
    self.origin = origin
    self.appVersion = appVersion
  }
}

protocol DriverSessionStore {
  func load() throws -> Data?
  // One Keychain item replaces the entire record atomically, including its journal.
  func save(_ data: Data) throws
}

protocol DriverSessionSigningKey {
  var publicJWK: [String: String] { get throws }
  // Exactly 64 bytes of JOSE R || S, not an ASN.1 signature.
  func sign(_ message: Data) throws -> Data
}

protocol DriverSessionKeyProvider {
  func key(tag: String, create: Bool) throws -> DriverSessionSigningKey
  func random(count: Int) throws -> Data
}

struct DriverSessionHTTPResponse {
  let status: Int
  let nonce: String?
  let body: Data
}

protocol DriverSessionHTTP {
  func send(_ request: URLRequest) async throws -> DriverSessionHTTPResponse
}

enum DriverSessionCodec {
  static func base64(_ data: Data) -> String {
    data.base64EncodedString().replacingOccurrences(of: "+", with: "-")
      .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
  }

  static func digest(_ data: Data) -> String { base64(Data(SHA256.hash(data: data))) }

  static func canonicalToken(_ value: String) -> Bool {
    guard value.range(of: "^[A-Za-z0-9_-]{43}$", options: .regularExpression) != nil,
      let bytes = Data(base64Encoded: value.replacingOccurrences(of: "-", with: "+")
        .replacingOccurrences(of: "_", with: "/") + "="), bytes.count == 32 else { return false }
    return base64(bytes) == value
  }

  static func json(_ object: [String: Any]) throws -> Data {
    try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys, .withoutEscapingSlashes])
  }

  static func object(_ data: Data, fields: Set<String>, required: Set<String>? = nil) throws -> [String: Any] {
    guard data.count <= 32_768,
      let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
      Set(object.keys).isSubset(of: fields), (required ?? fields).isSubset(of: Set(object.keys))
    else { throw DriverSessionFailure.response }
    return object
  }

  static func uuid(_ value: Any?) throws -> String {
    guard let value = value as? String, value.count == 36, UUID(uuidString: value) != nil else {
      throw DriverSessionFailure.response
    }
    return value
  }

  static func date(_ value: Any?) throws -> Date {
    guard let value = value as? String, value.count <= 40, value.hasSuffix("Z") else {
      throw DriverSessionFailure.response
    }
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let result = formatter.date(from: value) { return result }
    formatter.formatOptions = [.withInternetDateTime]
    guard let result = formatter.date(from: value) else { throw DriverSessionFailure.response }
    return result
  }

  static func iso(_ date: Date) -> String { ISO8601DateFormatter().string(from: date) }

  static func proof(key: DriverSessionSigningKey, method: String, url: String,
                    body: Data?, access: String?, nonce: String?, now: Date) throws -> String {
    let header = try json(["typ": "dpop+jwt", "alg": "ES256", "jwk": key.publicJWK])
    var claims: [String: Any] = ["jti": UUID().uuidString, "iat": Int64(now.timeIntervalSince1970),
      "htm": method, "htu": url]
    if let nonce = nonce { claims["nonce"] = nonce }
    if let body = body { claims["vr_body_sha256"] = digest(body) }
    if let access = access { claims["ath"] = digest(Data(access.utf8)) }
    let signingInput = base64(header) + "." + base64(try json(claims))
    let signature = try key.sign(Data(signingInput.utf8))
    guard signature.count == 64 else { throw DriverSessionFailure.key }
    return signingInput + "." + base64(signature)
  }
}

private struct DriverSessionTokens: Codable {
  let deviceID: String
  let access: String
  let refresh: String
  let accessExpiry: Date
  let refreshExpiry: Date
}

private struct DriverSessionOperation: Codable {
  let kind: String
  let body: Data
  let idempotency: String
  let createdAt: Date
}

private struct DriverSessionRecord: Codable {
  // v1 keys were provisioned WhenUnlocked. Never relabel a live v1 binding:
  // its next explicitly initiated pairing provisions a fresh v2 key instead.
  var version = 2
  let origin: String
  let keyTag: String
  var state = "UNPAIRED"
  var verifier: String?
  var pairingID: String?
  var userCode: String?
  var pairingExpiry: Date?
  var tokens: DriverSessionTokens?
  var journal: DriverSessionOperation?
  var revocationRequested: Bool?
}

// Actor + busy guard serialize mutation across awaits. A second channel call never
// creates a second refresh operation while the first one is awaiting the network.
actor DriverSessionController {
  private let configuration: DriverSessionConfiguration?
  private let store: DriverSessionStore
  private let keys: DriverSessionKeyProvider
  private let http: DriverSessionHTTP
  private let now: () -> Date
  private var record: DriverSessionRecord?
  private var nonce: String?
  private var busy = false
  private var sessionVerified = false

  init(configuration: DriverSessionConfiguration?, store: DriverSessionStore,
       keys: DriverSessionKeyProvider, http: DriverSessionHTTP, now: @escaping () -> Date = Date.init) throws {
    self.configuration = configuration; self.store = store; self.keys = keys; self.http = http; self.now = now
    // Missing build configuration is deliberately inert, even when old storage exists.
    if let configuration = configuration, let data = try store.load() {
      guard data.count <= 65_536,
        let loaded = try? JSONDecoder().decode(DriverSessionRecord.self, from: data),
        [1, 2].contains(loaded.version), loaded.origin == configuration.origin,
        UUID(uuidString: loaded.keyTag) != nil,
        ["UNPAIRED", "REQUESTING", "PENDING", "SETUP_REQUIRED", "READY", "EXPIRED", "REVOKING", "REVOKED"].contains(loaded.state),
        Self.valid(loaded)
      else { throw DriverSessionFailure.storage }
      self.record = loaded
    }
  }

  private static func valid(_ value: DriverSessionRecord) -> Bool {
    if let id = value.pairingID, UUID(uuidString: id) == nil { return false }
    if let code = value.userCode,
      code.range(of: "^[BCDFGHJKMNPQRSTVWXYZ]{8}$", options: .regularExpression) == nil { return false }
    if let verifier = value.verifier, !DriverSessionCodec.canonicalToken(verifier) { return false }
    if let tokens = value.tokens,
      UUID(uuidString: tokens.deviceID) == nil || !DriverSessionCodec.canonicalToken(tokens.access)
        || !DriverSessionCodec.canonicalToken(tokens.refresh) { return false }
    if let operation = value.journal {
      guard ["begin", "redeem", "refresh", "revoke"].contains(operation.kind), operation.body.count <= 32_768,
        UUID(uuidString: operation.idempotency) != nil else { return false }
      switch operation.kind {
      case "begin": if value.verifier == nil || value.tokens != nil || value.state != "REQUESTING" { return false }
      case "redeem": if value.verifier == nil || value.pairingID == nil || value.tokens != nil { return false }
      case "refresh": if value.tokens == nil { return false }
      case "revoke": if value.tokens == nil || value.state != "REVOKING" { return false }
      default: return false
      }
    }
    if ["READY", "SETUP_REQUIRED"].contains(value.state), value.tokens == nil { return false }
    if value.state == "PENDING", value.pairingID == nil || value.pairingExpiry == nil { return false }
    if value.state == "REVOKING", value.journal?.kind != "revoke", value.revocationRequested != true { return false }
    if value.state == "REQUESTING", value.journal?.kind != "begin" { return false }
    if value.state == "REVOKED", value.tokens != nil || value.journal != nil { return false }
    return true
  }

  func snapshot() -> [String: Any] {
    guard configuration != nil else { return ["state": "UNCONFIGURED"] }
    guard let record = record else { return ["state": "UNPAIRED"] }
    var state = record.state
    if state == "PENDING", let expiry = record.pairingExpiry, expiry <= now(), record.journal == nil { state = "EXPIRED" }
    if ["READY", "SETUP_REQUIRED"].contains(state), let token = record.tokens {
      if token.refreshExpiry <= now() { state = "EXPIRED" }
      else if !sessionVerified || token.accessExpiry <= now() { state = "SETUP_REQUIRED" }
    }
    var result: [String: Any] = ["state": state]
    if state == "PENDING", let expiry = record.pairingExpiry { result["expiresAt"] = DriverSessionCodec.iso(expiry) }
    return result
  }

  func showPairingCode() -> String? {
    guard snapshot()["state"] as? String == "PENDING", record?.journal == nil else { return nil }
    return record?.userCode
  }

  func perform(_ method: String) async throws -> [String: Any] {
    guard ["beginPairing", "syncSession", "refreshSession", "revokeSession"].contains(method) else {
      throw DriverSessionFailure.invalidArguments
    }
    guard configuration != nil, !busy else { return snapshot() }
    busy = true
    defer { busy = false }
    do {
      // Only an explicit new-pairing action may replace a terminated/expired
      // session. A recoverable refresh journal always wins ordinary reconciliation.
      if method == "beginPairing", let tokens = record?.tokens,
        record?.state == "EXPIRED" || tokens.refreshExpiry <= now() {
        try begin()
        try await replayJournal()
        return snapshot()
      }
      if record?.state == "EXPIRED", method != "beginPairing" { return snapshot() }
      if method == "revokeSession", record?.tokens != nil, record?.state != "REVOKED",
        record?.revocationRequested != true {
        try change { $0.revocationRequested = true; $0.state = "REVOKING" }
      }
      if record?.revocationRequested == true || record?.state == "REVOKING" {
        if record?.revocationRequested != true { try change { $0.revocationRequested = true } }
        try await resumeRevocation()
        return snapshot()
      }
      // Every retry starts with the pending durable operation, before another action.
      if record?.journal != nil {
        try await replayJournal()
        if record?.journal != nil { return snapshot() }
        // A user may confirm revoke while recovering an ambiguous refresh.
        if method != "revokeSession" { return try await synchronize() }
      }
      switch method {
      case "beginPairing":
        guard record?.tokens == nil || snapshot()["state"] as? String == "EXPIRED",
          record?.pairingID == nil || snapshot()["state"] as? String == "EXPIRED" || record?.state == "REVOKED"
        else { return snapshot() }
        try begin()
        try await replayJournal()
        return snapshot()
      case "revokeSession":
        return snapshot()
      case "refreshSession":
        if record?.tokens != nil { try await refresh() }
        return try await synchronize()
      default: return try await synchronize()
      }
    } catch let remote as DriverSessionRemoteFailure {
      if remote.code == "INTENT_EXPIRED",
        record?.tokens == nil || ["redeem", "refresh"].contains(record?.journal?.kind ?? "") {
        try change { $0.state = "EXPIRED"; $0.journal = nil; $0.userCode = nil; $0.verifier = nil }
        return snapshot()
      } else if remote.status == 401, remote.code == "INVALID_CREDENTIAL", record?.revocationRequested == true {
        try change { $0.state = "EXPIRED"; $0.journal = nil }
        return snapshot()
      } else if remote.status == 401, remote.code == "INVALID_CREDENTIAL", record?.tokens != nil,
                record?.journal?.kind != "revoke" {
        // A failed rotating-token recovery never retries with a new idempotency key.
        try change { $0.state = "EXPIRED" }
      }
      throw remote.status == 429 ? DriverSessionFailure.rateLimited : DriverSessionFailure.unavailable
    }
  }

  private func resumeRevocation() async throws {
    guard record?.state != "EXPIRED", record?.tokens != nil else { return }
    var refreshed = false
    if record?.journal?.kind == "refresh" {
      try await replayJournal()
      refreshed = true
    }
    guard let tokens = record?.tokens else { return }
    if tokens.accessExpiry > now() {
      if record?.journal == nil { try journal("revoke", body: [:], state: "REVOKING") }
      do { try await replayJournal(); return }
      catch let failure as DriverSessionRemoteFailure where failure.status == 401 && failure.code == "INVALID_CREDENTIAL" {
        if refreshed { throw failure }
        // The old access can expire at ingress before the local clock observes it.
      }
    }
    guard !refreshed, tokens.refreshExpiry > now() else {
      try change { $0.state = "EXPIRED"; $0.journal = nil }
      return
    }
    // Replacing the old revoke journal is atomic with the durable human intent.
    // Never reuse its access token or report READY while recovering authorization.
    let operation = DriverSessionOperation(kind: "refresh", body: try DriverSessionCodec.json(["refreshToken": tokens.refresh]),
      idempotency: UUID().uuidString.lowercased(), createdAt: now())
    try change { $0.journal = operation; $0.state = "REVOKING"; $0.revocationRequested = true }
    sessionVerified = false
    try await replayJournal()
    try journal("revoke", body: [:], state: "REVOKING")
    try await replayJournal()
  }

  private func commit(_ updated: DriverSessionRecord) throws {
    do {
      let data = try JSONEncoder().encode(updated)
      guard data.count <= 65_536 else { throw DriverSessionFailure.storage }
      try store.save(data)
      record = updated
    } catch { throw DriverSessionFailure.storage }
  }

  private func change(_ update: (inout DriverSessionRecord) -> Void) throws {
    guard var updated = record else { throw DriverSessionFailure.storage }
    update(&updated)
    try commit(updated)
  }

  private func begin() throws {
    guard let configuration = configuration else { return }
    // Called only for an explicit eligible new pairing. A v1 key remains bound
    // throughout status, token rotation, replay and revocation. Persist the v2 tag
    // before key creation so a crash/retry cannot rotate it a second time.
    let keyTag = record?.version == 1 ? UUID().uuidString : record?.keyTag ?? UUID().uuidString
    var updated = DriverSessionRecord(origin: configuration.origin, keyTag: keyTag)
    // Save the immutable key identity first; a crash can leave an unused key, never
    // an unjournaled POST or a pairing tied to a replacement key.
    try commit(updated)
    let key = try keys.key(tag: updated.keyTag, create: true)
    let verifier = DriverSessionCodec.base64(try keys.random(count: 32))
    updated.verifier = verifier
    let body = try DriverSessionCodec.json(["publicJwk": key.publicJWK,
      "pkceChallenge": DriverSessionCodec.digest(Data(verifier.utf8)), "pkceMethod": "S256",
      "platform": "IOS", "appVersion": configuration.appVersion])
    updated.state = "REQUESTING"
    updated.journal = DriverSessionOperation(kind: "begin", body: body,
      idempotency: UUID().uuidString.lowercased(), createdAt: now())
    try commit(updated)
  }

  private func journal(_ kind: String, body: [String: Any], state: String? = nil) throws {
    guard record?.journal == nil else { throw DriverSessionFailure.unavailable }
    let operation = DriverSessionOperation(kind: kind, body: try DriverSessionCodec.json(body),
      idempotency: UUID().uuidString.lowercased(), createdAt: now())
    try change { $0.journal = operation; if let state = state { $0.state = state } }
  }

  private func replayJournal() async throws {
    guard let operation = record?.journal else { return }
    let path = operation.kind == "begin" || operation.kind == "redeem"
      ? "/driver-app/v1/pairings/" + operation.kind : "/driver-app/v1/session/" + operation.kind
    // Revoke keeps the exact access token until the ACK; other operations have no Authorization.
    let response = try await request(path, body: operation.body,
      access: operation.kind == "revoke" ? record?.tokens?.access : nil, idempotency: operation.idempotency)
    switch operation.kind {
    case "begin":
      let value = try DriverSessionCodec.object(response, fields: ["pairingId", "userCode", "expiresAt", "serverTime"])
      let id = try DriverSessionCodec.uuid(value["pairingId"])
      let expiry = try DriverSessionCodec.date(value["expiresAt"])
      let server = try DriverSessionCodec.date(value["serverTime"])
      guard let code = value["userCode"] as? String,
        code.range(of: "^[BCDFGHJKMNPQRSTVWXYZ]{8}$", options: .regularExpression) != nil,
        expiry > server, expiry.timeIntervalSince(server) <= 300 else { throw DriverSessionFailure.response }
      try change { $0.pairingID = id; $0.userCode = code; $0.pairingExpiry = expiry; $0.state = "PENDING"; $0.journal = nil }
    case "redeem", "refresh":
      let value = try DriverSessionCodec.object(response, fields: ["deviceId", "accessToken", "refreshToken", "tokenType", "accessExpiresAt", "refreshExpiresAt", "serverTime"])
      let device = try DriverSessionCodec.uuid(value["deviceId"])
      let accessExpiry = try DriverSessionCodec.date(value["accessExpiresAt"])
      let refreshExpiry = try DriverSessionCodec.date(value["refreshExpiresAt"])
      let server = try DriverSessionCodec.date(value["serverTime"])
      guard let access = value["accessToken"] as? String, DriverSessionCodec.canonicalToken(access),
        let refresh = value["refreshToken"] as? String, DriverSessionCodec.canonicalToken(refresh),
        value["tokenType"] as? String == "DPoP", accessExpiry > server,
        accessExpiry.timeIntervalSince(server) <= 300, refreshExpiry > server,
        refreshExpiry.timeIntervalSince(server) <= 30 * 86_400,
        operation.kind != "refresh" || device == record?.tokens?.deviceID,
        operation.kind != "refresh" || refresh != record?.tokens?.refresh
      else { throw DriverSessionFailure.response }
      let tokens = DriverSessionTokens(deviceID: device, access: access, refresh: refresh,
        accessExpiry: accessExpiry, refreshExpiry: refreshExpiry)
      try change {
        $0.tokens = tokens; $0.journal = nil; $0.userCode = nil; $0.verifier = nil
        $0.pairingID = nil; $0.pairingExpiry = nil
        $0.state = $0.revocationRequested == true ? "REVOKING" : "SETUP_REQUIRED"
      }
      sessionVerified = false
    case "revoke":
      let value = try DriverSessionCodec.object(response, fields: ["deviceId", "state", "serverTime"])
      let device = try DriverSessionCodec.uuid(value["deviceId"])
      _ = try DriverSessionCodec.date(value["serverTime"])
      guard value["state"] as? String == "REVOKED", device == record?.tokens?.deviceID else { throw DriverSessionFailure.response }
      try change { $0.tokens = nil; $0.verifier = nil; $0.userCode = nil; $0.pairingID = nil
        $0.pairingExpiry = nil; $0.journal = nil; $0.state = "REVOKED"; $0.revocationRequested = nil }
    default: throw DriverSessionFailure.storage
    }
  }

  private func refresh() async throws {
    guard let tokens = record?.tokens, tokens.refreshExpiry > now() else {
      if record != nil { try change { $0.state = "EXPIRED" } }
      return
    }
    try journal("refresh", body: ["refreshToken": tokens.refresh])
    sessionVerified = false
    try await replayJournal()
  }

  private func ensureAccess() async throws {
    if let tokens = record?.tokens, tokens.accessExpiry <= now().addingTimeInterval(15) { try await refresh() }
    guard let tokens = record?.tokens, tokens.accessExpiry > now() else { throw DriverSessionFailure.unavailable }
  }

  private func synchronize() async throws -> [String: Any] {
    if let tokens = record?.tokens {
      guard record?.state != "REVOKED" else { return snapshot() }
      try await ensureAccess()
      guard let access = record?.tokens?.access else { throw DriverSessionFailure.unavailable }
      let response: Data
      do { response = try await request("/driver-app/v1/status", access: access) }
      catch let failure as DriverSessionRemoteFailure where failure.status == 401 && failure.code == "INVALID_CREDENTIAL" {
        // Expiry can win between the local clock check and ingress. One rotating
        // refresh distinguishes a stale access token from a terminated session.
        try await refresh()
        guard let refreshed = record?.tokens?.access, refreshed != access else { throw failure }
        response = try await request("/driver-app/v1/status", access: refreshed)
      }
      let value = try DriverSessionCodec.object(response,
        fields: ["deviceId", "driverId", "displayName", "profileStatus", "deviceState", "readiness", "runId", "serverTime"],
        required: ["deviceId", "driverId", "profileStatus", "deviceState", "readiness", "runId", "serverTime"])
      let device = try DriverSessionCodec.uuid(value["deviceId"])
      _ = try DriverSessionCodec.uuid(value["driverId"])
      _ = try DriverSessionCodec.date(value["serverTime"])
      guard device == tokens.deviceID, let readiness = value["readiness"] as? String,
        ["READY", "SETUP_REQUIRED"].contains(readiness),
        let deviceState = value["deviceState"] as? String, ["ACTIVE", "REVOKED"].contains(deviceState),
        let profile = value["profileStatus"] as? String,
        ["ONBOARDING", "ACTIVE", "SUSPENDED", "CLOSED"].contains(profile)
      else { throw DriverSessionFailure.response }
      if deviceState == "REVOKED" {
        try change { $0.tokens = nil; $0.journal = nil; $0.state = "REVOKED" }
      } else {
        try change { $0.state = profile == "SUSPENDED" || profile == "CLOSED" ? "EXPIRED" : readiness }
        sessionVerified = true
      }
    } else if let id = record?.pairingID {
      let response = try await request("/driver-app/v1/pairings/" + id + "/status")
      let value = try DriverSessionCodec.object(response, fields: ["pairingId", "state", "expiresAt", "serverTime"])
      guard try DriverSessionCodec.uuid(value["pairingId"]) == id else { throw DriverSessionFailure.response }
      _ = try DriverSessionCodec.date(value["expiresAt"])
      _ = try DriverSessionCodec.date(value["serverTime"])
      switch value["state"] as? String {
      case "APPROVED":
        guard let verifier = record?.verifier else { throw DriverSessionFailure.storage }
        try journal("redeem", body: ["pairingId": id, "pkceVerifier": verifier])
        try await replayJournal()
        return try await synchronize()
      case "PENDING": break
      case "EXPIRED", "REDEEMED": try change { $0.state = "EXPIRED"; $0.userCode = nil }
      case "REVOKED": try change { $0.state = "REVOKED"; $0.userCode = nil; $0.verifier = nil }
      default: throw DriverSessionFailure.response
      }
    }
    return snapshot()
  }

  // Called by the native collector, never exposes credentials to Flutter.
  func trackingRequest(path: String, body: Data?, idempotency: String?) async throws -> Data {
    guard !busy else { throw DriverSessionFailure.busy }
    guard configuration != nil, record?.tokens != nil,
      record?.revocationRequested != true,
      !["REVOKING", "REVOKED", "EXPIRED"].contains(record?.state ?? "") else { throw DriverSessionFailure.unavailable }
    guard path == "/driver-app/v1/tracking-status" ||
      path.range(of: "^/driver-app/v1/(start-intents/[0-9a-f-]{36}/activate|tracking-sessions/[0-9a-f-]{36}/(state|samples))$", options: .regularExpression) != nil else { throw DriverSessionFailure.invalidArguments }
    busy = true
    defer { busy = false }
    if record?.journal != nil {
      guard record?.journal?.kind == "refresh" else { throw DriverSessionFailure.unavailable }
      try await replayJournal()
    }
    try await ensureAccess()
    guard let access = record?.tokens?.access else { throw DriverSessionFailure.unavailable }
    do { return try await request(path, body: body, access: access, idempotency: idempotency) }
    catch let failure as DriverSessionRemoteFailure where failure.status == 401 && failure.code == "INVALID_CREDENTIAL" {
      try await refresh()
      guard let access = record?.tokens?.access else { throw failure }
      return try await request(path, body: body, access: access, idempotency: idempotency)
    }
  }

  private func request(_ path: String, body: Data? = nil, access: String? = nil,
                       idempotency: String? = nil) async throws -> Data {
    guard let configuration = configuration, let record = record,
      path.hasPrefix("/driver-app/v1/"), let url = URL(string: configuration.origin + path),
      (body?.count ?? 0) <= 32_768 else { throw DriverSessionFailure.unavailable }
    let key = try keys.key(tag: record.keyTag, create: false)
    for attempt in 0..<3 {
      var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: path == "/driver-app/v1/tracking-status" || path.contains("/tracking-sessions/") || path.contains("/start-intents/") ? 8 : 15)
      request.httpMethod = body == nil ? "GET" : "POST"
      request.httpBody = body
      request.setValue("application/json", forHTTPHeaderField: "Accept")
      if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
      if let access = access { request.setValue("DPoP " + access, forHTTPHeaderField: "Authorization") }
      if let idempotency = idempotency { request.setValue(idempotency, forHTTPHeaderField: "Idempotency-Key") }
      let currentNonce = nonce; nonce = nil
      request.setValue(try DriverSessionCodec.proof(key: key, method: request.httpMethod!, url: url.absoluteString,
        body: body, access: access, nonce: currentNonce, now: now()), forHTTPHeaderField: "DPoP")
      let response: DriverSessionHTTPResponse
      do { response = try await http.send(request) }
      catch let failure as DriverSessionFailure { throw failure }
      catch { throw DriverSessionFailure.network }
      guard response.body.count <= 32_768 else { throw DriverSessionFailure.response }
      if let received = response.nonce {
        guard DriverSessionCodec.canonicalToken(received) else { throw DriverSessionFailure.response }
        nonce = received
      }
      if response.status == 200 { return response.body }
      let code = (try? JSONSerialization.jsonObject(with: response.body) as? [String: Any])?["code"] as? String
      if response.status == 401, code == "PROOF_NONCE_REQUIRED", nonce != nil, attempt < 2 { continue }
      throw DriverSessionRemoteFailure(status: response.status, code: code ?? "")
    }
    throw DriverSessionFailure.unavailable
  }
}

struct DriverSessionRemoteFailure: Error {
  let status: Int
  let code: String
}
#endif
