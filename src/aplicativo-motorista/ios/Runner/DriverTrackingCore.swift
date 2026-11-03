import Foundation

// Pure contract/state helpers, shared by the native host and executable tests.
enum DriverTrackingFailure: String, Error {
  case invalidArguments = "INVALID_ARGUMENTS"
  case invalidEndpoint = "INVALID_ENDPOINT"
  case invalidCredential = "INVALID_CREDENTIAL"
  case credentialStorage = "CREDENTIAL_STORAGE"
  case credentialRequired = "CREDENTIAL_REQUIRED"
  case foregroundRequired = "FOREGROUND_REQUIRED"
  case permissionRequired = "PERMISSION_REQUIRED"
  case busy = "BUSY"
  case cancelled = "CANCELLED"
  case network = "NETWORK_ERROR"
  case serverRejected = "SERVER_REJECTED"
  case invalidContract = "INVALID_CONTRACT"
  case reconciliationRequired = "RECONCILIATION_REQUIRED"
}

enum DriverTrackingContract {
  static let controlKeys: Set<String> = ["schemaVersion", "environment", "sessionId", "generation",
    "serverTime", "leaseExpiresAt", "action", "gpsState"]

  static func identifier(_ value: String) -> Bool {
    !value.isEmpty && value.utf8.count <= 80 && value.utf8.allSatisfy {
      (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || $0 == 45 || $0 == 95
    }
  }

  static func integer(_ value: Any?) throws -> Int64 {
    guard let number = value as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID(),
      !String(cString: number.objCType).contains("f"),
      !String(cString: number.objCType).contains("d"),
      number.doubleValue >= 0, number.doubleValue < Double(Int64.max) else {
      throw DriverTrackingFailure.invalidContract
    }
    return number.int64Value
  }

  #if VANRIDES_GPS_SPIKE || DRIVER_TRACKING_STANDALONE_TESTS
  static func endpoint(_ raw: String, allowSimulatorHTTP: Bool) throws -> URL {
    guard var parts = URLComponents(string: raw), parts.user == nil, parts.password == nil,
      parts.query == nil, parts.fragment == nil,
      parts.percentEncodedPath.isEmpty || parts.percentEncodedPath == "/" else {
      throw DriverTrackingFailure.invalidEndpoint
    }
    let secure = parts.scheme == "https" && parts.host == "gps-spike.vanrides.matheusduarte.dev.br"
      && (parts.port == nil || parts.port == 443)
    let simulator = allowSimulatorHTTP && parts.scheme == "http" && parts.host == "127.0.0.1"
      && parts.port == 18765
    guard secure || simulator else { throw DriverTrackingFailure.invalidEndpoint }
    parts.path = ""
    guard let url = parts.url else { throw DriverTrackingFailure.invalidEndpoint }
    return url
  }
  #endif
}

import CoreFoundation

struct DriverTrackingControl {
  let sessionID: String
  let generation: Int64
  let serverTime: Int64
  let leaseExpiresAt: Int64
  let action: String
  let gpsState: String

  func isWithinCredential(expiry: Int64?) -> Bool {
    guard let expiry = expiry else { return false }
    return leaseExpiresAt <= expiry
  }

  init(_ json: [String: Any], additionalKeys: Set<String> = []) throws {
    guard Set(json.keys) == DriverTrackingContract.controlKeys.union(additionalKeys),
      try DriverTrackingContract.integer(json["schemaVersion"]) == 1,
      ["gps-spike", "driver-mvp"].contains(json["environment"] as? String ?? ""),
      let id = json["sessionId"] as? String, DriverTrackingContract.identifier(id),
      let action = json["action"] as? String, ["CONTINUE", "STOP"].contains(action),
      let gps = json["gpsState"] as? String, ["UNKNOWN", "FRESH", "STALE", "CLOCK_SKEW"].contains(gps) else {
      throw DriverTrackingFailure.invalidContract
    }
    self.sessionID = id
    self.generation = try DriverTrackingContract.integer(json["generation"])
    self.serverTime = try DriverTrackingContract.integer(json["serverTime"])
    self.leaseExpiresAt = try DriverTrackingContract.integer(json["leaseExpiresAt"])
    guard generation >= 1, leaseExpiresAt - serverTime <= 300_000 else {
      throw DriverTrackingFailure.invalidContract
    }
    self.action = action
    self.gpsState = gps
  }
}

struct DriverTrackingLease {
  private(set) var expiresAt: Int64 = 0
  private(set) var deadline: Double = 0

  mutating func update(_ control: DriverTrackingControl, requestStarted: Double, now: Double) throws {
    guard control.action == "CONTINUE", control.leaseExpiresAt > control.serverTime,
      deadline == 0 || now < deadline else { throw DriverTrackingFailure.invalidContract }
    let candidate = requestStarted + Double(control.leaseExpiresAt - control.serverTime) / 1000
    // An old/replayed expiry may shorten the deadline, but can never extend it.
    if deadline == 0 || control.leaseExpiresAt > expiresAt {
      deadline = candidate
    } else {
      deadline = min(deadline, candidate)
    }
    expiresAt = max(expiresAt, control.leaseExpiresAt)
    guard now < deadline else { throw DriverTrackingFailure.invalidContract }
  }
}

// STOP retries retain the original native stop record and never extend/restart collection.
struct DriverTrackingStopRequest {
  let sessionID: String
  let generation: Int64
  let body: [String: Any]

  init(sessionID: String, generation: Int64, reason: String, requestedAt: Int64, stoppedAt: Int64, stopRequestRef: String? = nil) {
    self.sessionID = sessionID
    self.generation = generation
    var value: [String: Any] = ["schemaVersion": 1, "generation": generation, "state": "STOPPED", "reason": reason,
      "stopRequestedAt": requestedAt, "stoppedAt": stoppedAt]
    if let ref = stopRequestRef { value["stopRequestRef"] = ref }
    body = value
  }
}

struct DriverTrackingStopRetry {
  private(set) var nextAttemptAt: Double?
  private var nextDelay: Double = 2

  mutating func failed(now: Double) -> Double {
    let delay = nextDelay
    nextAttemptAt = now + delay
    nextDelay = min(30, delay * 2)
    return delay
  }

  func remainingDelay(now: Double) -> Double {
    max(0, (nextAttemptAt ?? now) - now)
  }

  mutating func reset() { self = Self() }
}

struct DriverTrackingSample {
  let sequence: Int64
  let capturedAt: Int64
  let latitude: Double
  let longitude: Double
  let accuracyM: Double
  let speedMps: Double?
  let headingDeg: Double?
  let expires: Double

  var json: [String: Any] {
    var value: [String: Any] = ["sequence": sequence, "capturedAt": capturedAt,
      "latitude": latitude, "longitude": longitude, "accuracyM": accuracyM]
    if let speed = speedMps { value["speedMps"] = speed }
    if let heading = headingDeg { value["headingDeg"] = heading }
    return value
  }
}

struct DriverTrackingQueue {
  private(set) var samples: [DriverTrackingSample] = []
  private(set) var dropped = 0

  mutating func purge(now: Double) {
    let old = samples.count
    samples.removeAll { now >= $0.expires }
    dropped += old - samples.count
  }

  mutating func append(_ sample: DriverTrackingSample, now: Double) {
    purge(now: now)
    guard now < sample.expires else { dropped += 1; return }
    samples.append(sample)
    if samples.count > 120 {
      dropped += samples.count - 120
      samples.removeFirst(samples.count - 120)
    }
  }

  mutating func remove(_ sequences: Set<Int64>) -> Set<Int64> {
    let present = Set(samples.filter { sequences.contains($0.sequence) }.map(\.sequence))
    samples.removeAll { sequences.contains($0.sequence) }
    return present
  }

  mutating func clear() {
    dropped += samples.count
    samples.removeAll(keepingCapacity: false)
  }
}

struct DriverTrackingBatch {
  let body: Data
  let sequences: Set<Int64>
  var acknowledged: Set<Int64> = []

  init(samples: [DriverTrackingSample], sessionID: String, generation: Int64, deviceTime: Int64) throws {
    var selected: [DriverTrackingSample] = []
    var encoded = Data()
    let batchID = UUID().uuidString.lowercased()
    for sample in samples.prefix(30) {
      let candidate = selected + [sample]
      let data = try JSONSerialization.data(withJSONObject: ["schemaVersion": 1, "sessionId": sessionID,
        "generation": generation, "batchId": batchID, "deviceTime": deviceTime,
        "samples": candidate.map(\.json)], options: [.sortedKeys])
      if data.count > 32_768 { break }
      selected = candidate
      encoded = data
    }
    guard !selected.isEmpty else { throw DriverTrackingFailure.invalidContract }
    body = encoded
    sequences = Set(selected.map(\.sequence))
  }

  func acknowledgements(_ json: [String: Any]) throws -> (Set<Int64>, Set<Int64>, Set<Int64>) {
    guard let accepted = json["accepted"] as? [Any], let duplicate = json["duplicate"] as? [Any],
      let rejected = json["rejected"] as? [[String: Any]] else { throw DriverTrackingFailure.invalidContract }
    let a = try accepted.map { try DriverTrackingContract.integer($0) }
    let d = try duplicate.map { try DriverTrackingContract.integer($0) }
    let r = try rejected.map { item -> Int64 in
      guard Set(item.keys) == ["sequence", "code"], let code = item["code"] as? String,
        code.utf8.count <= 64, DriverTrackingContract.identifier(code) else {
        throw DriverTrackingFailure.invalidContract
      }
      return try DriverTrackingContract.integer(item["sequence"])
    }
    let all = a + d + r
    guard Set(all).count == all.count, Set(all).isSubset(of: sequences) else {
      throw DriverTrackingFailure.invalidContract
    }
    return (Set(a), Set(d), Set(r))
  }
}


// Spring110 boundary: dates stay ISO8601 on the wire and monotonic inside the engine.
final class DriverProductionTrackingWire {
  private(set) var stopReference: String?
  private var retries: [String: String] = [:]
  private func iso(_ value: Any?) throws -> String {
    let milliseconds = try DriverTrackingContract.integer(value)
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter.string(from: Date(timeIntervalSince1970: Double(milliseconds) / 1000))
  }
  private func millis(_ value: Any?) throws -> Int64 {
    guard let text = value as? String, text.hasSuffix("Z") else { throw DriverTrackingFailure.invalidContract }
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let date = formatter.date(from: text) ?? ISO8601DateFormatter().date(from: text)
    guard let date = date else { throw DriverTrackingFailure.invalidContract }
    return Int64((date.timeIntervalSince1970 * 1000).rounded())
  }
  func outgoing(path: String, data: Data?) throws -> (Data?, String?) {
    guard let data = data else { return (nil, nil) }
    guard var value = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw DriverTrackingFailure.invalidContract }
    let key: String
    if path.hasSuffix("/activate") {
      guard Set(value.keys) == ["expectedVersion", "foregroundConfirmed", "intentToken"],
        value["foregroundConfirmed"] as? String == "CONFIRMED" else { throw DriverTrackingFailure.invalidContract }
      key = String(path.split(separator: "/").dropLast().last ?? "")
    } else if path.hasSuffix("/samples") {
      value.removeValue(forKey: "schemaVersion")
      value["deviceTime"] = try iso(value["deviceTime"])
      guard let samples = value["samples"] as? [[String: Any]] else { throw DriverTrackingFailure.invalidContract }
      value["samples"] = try samples.map { sample -> [String: Any] in
        var next = sample; next["capturedAt"] = try iso(sample["capturedAt"]); return next
      }
      guard let id = value["batchId"] as? String else { throw DriverTrackingFailure.invalidContract }
      key = id
    } else {
      let id = String(path.split(separator: "/").dropLast().last ?? "")
      let state = value["state"] as? String == "STOPPED" ? "STOPPED" : "ACTIVE"
      value = ["sessionId": id, "generation": value["generation"] as Any, "state": state]
      if state == "STOPPED", let ref = stopReference { value["stopRequestRef"] = ref }
      let body = try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])
      let retry = path + body.base64EncodedString()
      key = state == "STOPPED" ? id : retries[retry] ?? UUID().uuidString.lowercased()
      retries[retry] = key
    }
    return (try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]), key)
  }
  func incoming(path: String, data: Data) throws -> [String: Any] {
    guard let value = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw DriverTrackingFailure.invalidContract }
    let server = try millis(value["serverTime"])
    stopReference = value["stopRequestRef"] as? String
    var result: [String: Any] = ["schemaVersion": 1, "environment": "driver-mvp", "serverTime": server]
    if path.hasSuffix("/tracking-status") {
      result["credentialExpiresAt"] = try millis(value["credentialExpiresAt"])
      if value["trackingSessionId"] is NSNull || value["sessionState"] as? String == "IDLE" { result["session"] = NSNull() }
      else {
        result["session"] = ["sessionId": value["trackingSessionId"] as Any,
          "generation": value["generation"] as Any, "state": value["sessionState"] as Any,
          "leaseExpiresAt": try millis(value["leaseExpiresAt"]), "gpsState": value["gpsState"] as Any]
      }
    } else {
      result["sessionId"] = value["trackingSessionId"] ?? value["sessionId"]
      result["generation"] = value["generation"]
      result["leaseExpiresAt"] = try millis(value["leaseExpiresAt"])
      result["gpsState"] = value["gpsState"]
      result["action"] = value["action"]
      if path.hasSuffix("/samples") {
        for key in ["accepted", "duplicate", "rejected"] { result[key] = value[key] }
      }
    }
    retries = retries.filter { !$0.key.hasPrefix(path) }
    return result
  }
}


struct DriverRunLink {
  let id: String
  let version: Int64
  private let token: String
  static func parse(_ raw: String, host: String = "") -> DriverRunLink? {
    guard !raw.contains("%") else { return nil }
    let parts = raw.split(separator: "#", omittingEmptySubsequences: false)
    guard parts.count == 2,
      parts[0] == "vanridesdriver://run/start" || !host.isEmpty && parts[0] == "https://\(host)/motorista/saida" else { return nil }
    let fields = parts[1].split(separator: ".", omittingEmptySubsequences: false)
    guard fields.count == 4, fields[0] == "v1",
      fields[1].range(of: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", options: .regularExpression) != nil,
      fields[2].range(of: "^(0|[1-9][0-9]{0,15})$", options: .regularExpression) != nil,
      let version = Int64(fields[2]), version <= 9_007_199_254_740_991,
      fields[3].range(of: "^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$", options: .regularExpression) != nil else { return nil }
    return DriverRunLink(id: String(fields[1]), version: version, token: String(fields[3]))
  }
  func matches(id: String, version: Int64, token: String) -> Bool {
    self.id == id && self.version == version && self.token == token
  }
}

// This journal contains only a native stop attestation; samples and credentials are excluded.
enum DriverTrackingStopJournal {
  static func encode(_ request: DriverTrackingStopRequest?) throws -> Data {
    guard let request = request else { return Data("null".utf8) }
    var value = request.body; value["sessionId"] = request.sessionID
    return try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])
  }
  static func decode(_ data: Data?) throws -> DriverTrackingStopRequest? {
    guard let data = data else { return nil }
    let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
    if object is NSNull { return nil }
    guard let value = object as? [String: Any],
      Set(value.keys).isSubset(of: ["schemaVersion", "sessionId", "generation", "state", "reason", "stopRequestedAt", "stoppedAt", "stopRequestRef"]),
      let id = value["sessionId"] as? String, UUID(uuidString: id) != nil, id == id.lowercased(),
      (try? DriverTrackingContract.integer(value["schemaVersion"])) == 1,
      value["state"] as? String == "STOPPED", let reason = value["reason"] as? String,
      DriverTrackingContract.identifier(reason) else { throw DriverTrackingFailure.invalidContract }
    let generation = try DriverTrackingContract.integer(value["generation"])
    let requested = try DriverTrackingContract.integer(value["stopRequestedAt"])
    let stopped = try DriverTrackingContract.integer(value["stoppedAt"])
    guard generation >= 1, stopped >= requested else { throw DriverTrackingFailure.invalidContract }
    if let ref = value["stopRequestRef"] as? String, UUID(uuidString: ref) == nil { throw DriverTrackingFailure.invalidContract }
    return DriverTrackingStopRequest(sessionID: id, generation: generation, reason: reason,
      requestedAt: requested, stoppedAt: stopped, stopRequestRef: value["stopRequestRef"] as? String)
  }
}
