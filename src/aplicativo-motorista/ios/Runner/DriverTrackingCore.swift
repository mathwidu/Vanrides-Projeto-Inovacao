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
  case finishReviewRequired = "FINISH_REVIEW_REQUIRED"
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

enum DriverTrackingStopContinuity {
  static func reusesAttestation(state: String, stoppedAt: Int64?, providerPresent: Bool, forceNewRecord: Bool = false) -> Bool {
    (state == "STOPPED" || state == "ERROR") && stoppedAt != nil && !providerPresent && !forceNewRecord
  }

  static func preservesConfirmation(state: String, stoppedAt: Int64?, providerPresent: Bool,
                                    pending: Bool, sessionID: String?, generation: Int64) -> Bool {
    state == "STOPPED" && reusesAttestation(state: state, stoppedAt: stoppedAt, providerPresent: providerPresent)
      && !pending && sessionID != nil && generation >= 1
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
  private(set) var runState: String?
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
      let packetStopReference = value["stopRequestRef"] as? String
      value = ["sessionId": id, "generation": value["generation"] as Any, "state": state]
      if state == "STOPPED", let ref = packetStopReference ?? stopReference { value["stopRequestRef"] = ref }
      let body = try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])
      let retry = path + body.base64EncodedString()
      key = state == "STOPPED" ? id : retries[retry] ?? UUID().uuidString.lowercased()
      retries[retry] = key
    }
    return (try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]), key)
  }
  func incoming(path: String, data: Data) throws -> [String: Any] {
    guard let value = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw DriverTrackingFailure.invalidContract }
    let stateKey = path.hasSuffix("/tracking-status") ? "runState" : value["runId"] != nil ? "state" : nil
    var nextRunState = runState
    if let key = stateKey, let raw = value[key] {
      if raw is NSNull { nextRunState = nil }
      else {
        guard let state = raw as? String,
          ["PLANNED", "STARTING", "START_FAILED", "TRACKING", "DEGRADED", "COMPLETED", "CANCELED"].contains(state) else {
          throw DriverTrackingFailure.invalidContract
        }
        nextRunState = state
      }
    }
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
    runState = nextRunState
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
  static func parseFinish(_ raw: String, host: String = "") -> DriverRunLink? {
    let base = raw.split(separator: "#", omittingEmptySubsequences: false).first.map(String.init) ?? ""
    guard base == "vanridesdriver://run/finish" || !host.isEmpty && base == "https://\(host)/motorista/fim" else { return nil }
    return parse("vanridesdriver://run/start" + String(raw.dropFirst(base.count)), host: host)
  }
}

// A proof-only receipt confirms an existing stop; it never grants collection.
struct DriverTrackingStopReceipt {
  let attestation: DriverTrackingStopRequest
  let stoppedAt: Int64
  let runState: String

  init(_ data: Data, expected: DriverTrackingStopRequest) throws {
    guard data.count <= 32_768, let value = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
      Set(value.keys) == ["schemaVersion", "sessionId", "generation", "stopRequestRef", "stopConfirmed", "stoppedAt", "serverTime", "runState"],
      try DriverTrackingContract.integer(value["schemaVersion"]) == 1,
      value["sessionId"] as? String == expected.sessionID,
      try DriverTrackingContract.integer(value["generation"]) == expected.generation,
      let confirmed = value["stopConfirmed"] as? NSNumber,
      CFGetTypeID(confirmed) == CFBooleanGetTypeID(), confirmed.boolValue,
      let runState = value["runState"] as? String,
      ["PLANNED", "STARTING", "START_FAILED", "TRACKING", "DEGRADED", "COMPLETED", "CANCELED"].contains(runState)
    else { throw DriverTrackingFailure.invalidContract }
    if let ref = expected.body["stopRequestRef"] as? String {
      guard value["stopRequestRef"] as? String == ref else { throw DriverTrackingFailure.invalidContract }
    } else if !(value["stopRequestRef"] is NSNull) { throw DriverTrackingFailure.invalidContract }
    func milliseconds(_ raw: Any?) throws -> Int64 {
      guard let text = raw as? String, text.count <= 40, text.hasSuffix("Z") else { throw DriverTrackingFailure.invalidContract }
      let formatter = ISO8601DateFormatter()
      formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
      guard let date = formatter.date(from: text) ?? ISO8601DateFormatter().date(from: text) else { throw DriverTrackingFailure.invalidContract }
      let time = date.timeIntervalSince1970 * 1000
      guard time.isFinite, time >= 0, time < Double(Int64.max) else { throw DriverTrackingFailure.invalidContract }
      return Int64(time.rounded())
    }
    let stoppedAt = try milliseconds(value["stoppedAt"])
    guard stoppedAt <= (try milliseconds(value["serverTime"])) else { throw DriverTrackingFailure.invalidContract }
    self.attestation = expected
    self.stoppedAt = stoppedAt
    self.runState = runState
  }
}

// This journal contains only a native stop attestation; samples and credentials are excluded.
enum DriverTrackingStopJournal {
  static func clearConfirmed(_ expected: DriverTrackingStopRequest, current: DriverTrackingStopRequest?,
                             persist: (Data) throws -> Void) throws {
    guard current != nil, try encode(current) == encode(expected) else { throw DriverTrackingFailure.invalidContract }
    try persist(encode(nil))
  }

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

enum DriverFinishContract {
  static func exact(_ value: [String: Any], _ fields: Set<String>) throws {
    guard Set(value.keys) == fields else { throw DriverTrackingFailure.invalidContract }
  }
  static func uuid(_ raw: Any?) throws -> String {
    guard let value = raw as? String, value.count == 36, UUID(uuidString: value) != nil,
      value == value.lowercased() else { throw DriverTrackingFailure.invalidContract }
    return value
  }
  static func instant(_ raw: Any?) throws {
    guard let value = raw as? String, value.hasSuffix("Z") else { throw DriverTrackingFailure.invalidContract }
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if formatter.date(from: value) != nil { return }
    formatter.formatOptions = [.withInternetDateTime]
    guard formatter.date(from: value) != nil else { throw DriverTrackingFailure.invalidContract }
  }
  static func authorization(_ value: [String: Any]) throws {
    try exact(value,["intentId","intentToken","expectedVersion"])
    _ = try uuid(value["intentId"])
    guard let token = value["intentToken"] as? String,
      token.range(of: "^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$", options: .regularExpression) != nil,
      try DriverTrackingContract.integer(value["expectedVersion"]) <= 9_007_199_254_740_991 else { throw DriverTrackingFailure.invalidContract }
  }
  static func same(_ first: [String: Any]?, _ second: [String: Any]) -> Bool {
    guard let first = first else { return false }
    return NSDictionary(dictionary: first).isEqual(to: second)
  }
  static func read(_ args: [String: Any]) -> [String: Any] { ["intentToken": args["intentToken"]!, "expectedVersion": args["expectedVersion"]!] }
  static func owner(_ value: [String: Any]) throws -> String {
    let id = try uuid(value["trackingSessionId"]), generation = try DriverTrackingContract.integer(value["sessionGeneration"])
    guard generation >= 1 else { throw DriverTrackingFailure.invalidContract }
    return "\(id):\(generation)"
  }
  static func preview(_ value: [String: Any], args: [String: Any]) throws {
    try exact(value,["intentId","runId","trackingSessionId","sessionGeneration","expectedVersion","expiresAt","state","trip","stopAcknowledged"])
    guard value["intentId"] as? String == args["intentId"] as? String,
      try DriverTrackingContract.integer(value["expectedVersion"]) == DriverTrackingContract.integer(args["expectedVersion"]),
      ["READY","COMPLETED"].contains(value["state"] as? String ?? ""), value["stopAcknowledged"] is Bool,
      let trip = value["trip"] as? [String: Any] else { throw DriverTrackingFailure.invalidContract }
    _ = try uuid(value["runId"]); _ = try owner(value); try instant(value["expiresAt"])
    try exact(trip,["name","direction","localDate","localTime","timezone"])
    guard let name = trip["name"] as? String, (1...80).contains(name.count),
      ["OUTBOUND","RETURN"].contains(trip["direction"] as? String ?? ""),
      let date = trip["localDate"] as? String, date.range(of: "^\\d{4}-\\d{2}-\\d{2}$", options: .regularExpression) != nil,
      let time = trip["localTime"] as? String, time.range(of: "^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$", options: .regularExpression) != nil,
      let timezone = trip["timezone"] as? String, TimeZone(identifier: timezone) != nil else { throw DriverTrackingFailure.invalidContract }
  }
  static func receipt(_ value: [String: Any], expected: [String: Any]) throws {
    try exact(value,["intentId","runId","trackingSessionId","sessionGeneration","expectedVersion","state","finishConsumedAt","stopAcknowledged","stopRequestRef","stoppedAt"])
    for key in ["intentId","runId","trackingSessionId"] {
      guard value[key] as? String == expected[key] as? String else { throw DriverTrackingFailure.invalidContract }
    }
    for key in ["sessionGeneration","expectedVersion"] {
      guard try DriverTrackingContract.integer(value[key]) == DriverTrackingContract.integer(expected[key]) else { throw DriverTrackingFailure.invalidContract }
    }
    guard value["state"] as? String == "COMPLETED", value["stopAcknowledged"] is Bool else { throw DriverTrackingFailure.invalidContract }
    try instant(value["finishConsumedAt"])
    if !(value["stopRequestRef"] is NSNull) { _ = try uuid(value["stopRequestRef"]) }
    if !(value["stoppedAt"] is NSNull) { try instant(value["stoppedAt"]) }
    if value["stopAcknowledged"] as? Bool == true { try instant(value["stoppedAt"]) }
  }
  static func stoppedPacket(_ preview: [String: Any], ref: String?, currentOwner: String?,
                            providerStopped: Bool, now: Int64) throws -> DriverTrackingStopRequest {
    let expected = try owner(preview)
    guard providerStopped, currentOwner == nil || currentOwner == expected, now > 0 else { throw DriverTrackingFailure.invalidContract }
    if let ref = ref { _ = try uuid(ref) }
    return DriverTrackingStopRequest(sessionID: try uuid(preview["trackingSessionId"]),
      generation: try DriverTrackingContract.integer(preview["sessionGeneration"]),reason: "USER_FINISH",
      requestedAt: now,stoppedAt: now,stopRequestRef: ref)
  }
}

/// Restoring the private journal never restores a navigation callback or starts GPS.
final class DriverFinishFlow {
  typealias Reply = (Result<[String: Any], DriverTrackingFailure>) -> Void
  typealias NetworkReply = (Result<[String: Any], DriverTrackingFailure>, Int?) -> Void
  typealias Request = (String, [String: Any], String?, @escaping NetworkReply) -> Void
  private let request: Request
  private let save: (Data?) throws -> Void
  private let localOwner: () -> String?
  private let localOff: () -> Bool
  private let available: () -> Bool
  private let stopLocal: () -> Void
  private let prepareStop: ([String: Any]) throws -> Void
  private let bindStop: ([String: Any], String?) throws -> Void
  private let clearStop: ([String: Any], String?) throws -> Void
  private let wake: (Double) -> Void
  private let now: () -> Date
  private var journal: [String: Any]?
  private var storageAvailable = true
  private var offered: [String: Any]?, offeredArgs: [String: Any]?
  private var reply: Reply?
  private var first = false, recovering = false, prepared = false
  private var nextAt = Date.distantPast
  private(set) var networkBusy = false
  private(set) var holdStop = false
  init(request: @escaping Request, load: () throws -> Data?, save: @escaping (Data?) throws -> Void,
       localOwner: @escaping () -> String?, localOff: @escaping () -> Bool, available: @escaping () -> Bool,
       stopLocal: @escaping () -> Void, prepareStop: @escaping ([String: Any]) throws -> Void,
       bindStop: @escaping ([String: Any], String?) throws -> Void,
       clearStop: @escaping ([String: Any], String?) throws -> Void, wake: @escaping (Double) -> Void,
       now: @escaping () -> Date = Date.init) {
    self.request = request; self.save = save; self.localOwner = localOwner; self.localOff = localOff
    self.available = available; self.stopLocal = stopLocal; self.bindStop = bindStop; self.clearStop = clearStop; self.wake = wake
    self.prepareStop = prepareStop
    self.now = now
    do {
      if let data = try load() {
        guard data.count <= 16_384, let value = try JSONSerialization.jsonObject(with: data) as? [String: Any],
          Set(value.keys).isSubset(of: ["authorization","preview","key","receipt"]),
          let args = value["authorization"] as? [String: Any], let preview = value["preview"] as? [String: Any] else { throw DriverTrackingFailure.invalidContract }
        try DriverFinishContract.authorization(args); try DriverFinishContract.preview(preview,args: args)
        _ = try DriverFinishContract.uuid(value["key"]); journal = value
      }
    } catch { storageAvailable = false }
  }
  private func persist(_ value: [String: Any]?) throws { try save(try value.map { try JSONSerialization.data(withJSONObject: $0,options: [.sortedKeys]) }) }
  func cancelAction() { let callback = reply; reply = nil; holdStop = false; callback?(.failure(.cancelled)) }
  func pending() -> [String: Any]? { journal?["preview"] as? [String: Any] }
  func retry(_ args: [String: Any], previewOnly: Bool, completion: @escaping Reply) {
    do {
      try DriverFinishContract.exact(args,["intentId","expectedVersion"])
      guard let record = journal, let auth = record["authorization"] as? [String: Any],
        auth["intentId"] as? String == args["intentId"] as? String,
        try DriverTrackingContract.integer(auth["expectedVersion"]) == DriverTrackingContract.integer(args["expectedVersion"]),
        let preview = record["preview"] as? [String: Any] else { throw DriverTrackingFailure.invalidContract }
      offered = preview; offeredArgs = auth
      if previewOnly { completion(.success(preview)) } else { finish(auth,completion: completion) }
    } catch { completion(.failure(.invalidContract)) }
  }
  func preview(_ args: [String: Any], completion: @escaping Reply) {
    guard !networkBusy, available() else { completion(.failure(.busy)); return }
    do { try DriverFinishContract.authorization(args) } catch { completion(.failure(.invalidContract)); return }
    networkBusy = true
    request("/driver-app/v1/finish-intents/\(args["intentId"]!)/preview",DriverFinishContract.read(args),nil) { [weak self] result, status in
      guard let self = self else { return }; self.networkBusy = false
      do {
        let value = try result.get(); try DriverFinishContract.preview(value,args: args)
        self.offered = value; self.offeredArgs = args; completion(.success(value))
      } catch { completion(.failure(status == 409 || status == 410 ? .finishReviewRequired : .invalidContract)) }
      self.wake(0)
    }
  }
  func finish(_ args: [String: Any], completion: @escaping Reply) {
    guard reply == nil, !networkBusy else { completion(.failure(.busy)); return }
    var mayStop = false
    do {
      try DriverFinishContract.authorization(args)
      guard let preview = offered, DriverFinishContract.same(offeredArgs,args) else { throw DriverTrackingFailure.invalidContract }
      let expected = try DriverFinishContract.owner(preview)
      guard localOwner() == nil || localOwner() == expected else { throw DriverTrackingFailure.invalidContract }
      mayStop = true
      guard storageAvailable else { throw DriverTrackingFailure.credentialStorage }
      var existing = journal
      if let prior = existing?["authorization"] as? [String: Any], !DriverFinishContract.same(prior,args) { existing = nil }
      let next = existing ?? ["authorization": args,"preview": preview,"key": UUID().uuidString.lowercased()]
      try persist(next); journal = next
      recovering = existing != nil && next["receipt"] == nil
      reply = completion; holdStop = true; first = true; prepared = false; nextAt = .distantPast
      stopLocal(); tick(); wake(0.1)
    } catch { if mayStop { stopLocal() }; completion(.failure(.invalidContract)) }
  }
  func tick() {
    guard let record = journal, reply != nil, localOff(),
      let preview = record["preview"] as? [String: Any], let args = record["authorization"] as? [String: Any],
      let key = record["key"] as? String else { return }
    guard localOwner() == nil || localOwner() == (try? DriverFinishContract.owner(preview)) else { fail(.invalidContract); return }
    if !prepared {
      do { try prepareStop(preview); prepared = true }
      catch { fail(.credentialStorage); return }
    }
    guard !networkBusy, now() >= nextAt else { return }
    guard available() else { wake(0.1); return }
    let consume = first && !recovering && record["receipt"] == nil
    first = false; networkBusy = true
    let path = consume ? "/driver-app/v1/runs/\(preview["runId"]!)/finish" : "/driver-app/v1/finish-intents/\(args["intentId"]!)/receipt"
    var body = consume ? args : DriverFinishContract.read(args)
    if consume { body["foregroundConfirmed"] = "CONFIRMED" }
    request(path,body,consume ? key : nil) { [weak self] result, status in
      guard let self = self else { return }; self.networkBusy = false
      guard self.journal?["key"] as? String == key, self.reply != nil else { self.wake(0); return }
      // Cancelling the collector's wrapper does not synchronously drain its
      // actor-owned URLSession request. BUSY means no FINISH was submitted.
      if case .failure(.busy) = result {
        if consume { self.first = true }
        self.nextAt = self.now().addingTimeInterval(0.1)
        self.wake(0.1)
        return
      }
      self.holdStop = false
      if self.recovering && status == 404 { self.recovering = false; self.first = true; self.wake(0); return }
      self.recovering = false
      do {
        let value = try result.get()
        let receipt: [String: Any]
        if consume {
          guard let nested = value["receipt"] as? [String: Any] else { throw DriverTrackingFailure.invalidContract }
          receipt = nested
        } else { receipt = value }
        try DriverFinishContract.receipt(receipt,expected: preview)
        if consume {
          try DriverFinishContract.exact(value,["receipt","control"])
          if let control = value["control"] as? [String: Any] {
            try DriverFinishContract.exact(control,["runId","state","entityVersion","trackingSessionId","generation","serverTime","action","gpsState","leaseExpiresAt","stopRequestRef"])
          }
          guard let control = value["control"] as? [String: Any], control["action"] as? String == "STOP",
            control["trackingSessionId"] as? String == preview["trackingSessionId"] as? String,
            control["runId"] as? String == preview["runId"] as? String, control["state"] as? String == "COMPLETED",
            NSDictionary(dictionary: ["ref": control["stopRequestRef"]!]).isEqual(to: ["ref": receipt["stopRequestRef"]!]),
            try DriverTrackingContract.integer(control["generation"]) == DriverTrackingContract.integer(preview["sessionGeneration"]) else { throw DriverTrackingFailure.invalidContract }
        }
        if let prior = record["receipt"] as? [String: Any] {
          guard NSDictionary(dictionary: ["ref": prior["stopRequestRef"]!,"at": prior["finishConsumedAt"]!]).isEqual(to: ["ref": receipt["stopRequestRef"]!,"at": receipt["finishConsumedAt"]!]) else { throw DriverTrackingFailure.invalidContract }
        }
        var updated = record; updated["receipt"] = receipt; try self.persist(updated); self.journal = updated
        let ref = receipt["stopRequestRef"] as? String
        if receipt["stopAcknowledged"] as? Bool != true { try self.bindStop(preview,ref) }
        if receipt["stopAcknowledged"] as? Bool == true && self.localOff() {
          try self.clearStop(preview,ref); try self.persist(nil); self.journal = nil
          let callback = self.reply; self.reply = nil
          callback?(.success(["receipt": receipt,"providerStopped": true]))
        } else { self.nextAt = self.now().addingTimeInterval(2); self.wake(2) }
      } catch { self.fail(status == 409 || status == 410 ? .finishReviewRequired : .serverRejected) }
      self.wake(0)
    }
  }
  private func fail(_ error: DriverTrackingFailure) { holdStop = false; let callback = reply; reply = nil; callback?(.failure(error)); wake(0) }
}
