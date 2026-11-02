import Foundation
import XCTest
#if !DRIVER_TRACKING_STANDALONE_TESTS
@testable import Runner
#endif

final class DriverTrackingCoreTests: XCTestCase {
  private func control(expiry: Int64 = 310_000, server: Int64 = 10_000) throws -> DriverTrackingControl {
    try DriverTrackingControl(["schemaVersion": 1, "environment": "gps-spike", "sessionId": "synthetic",
      "generation": 1, "serverTime": server, "leaseExpiresAt": expiry, "action": "CONTINUE", "gpsState": "UNKNOWN"])
  }

  private func sample(_ sequence: Int64, expires: Double = 120) -> DriverTrackingSample {
    DriverTrackingSample(sequence: sequence, capturedAt: 1_000, latitude: 0, longitude: 0,
      accuracyM: 5, speedMps: nil, headingDeg: nil, expires: expires)
  }

  func testProductionClocksAndPartialAckPreserveMissingSequence() throws {
    let id = "12345678-1234-1234-1234-123456789012"
    let path = "/driver-app/v1/tracking-sessions/" + id + "/samples"
    let wire = DriverProductionTrackingWire()
    let batch = try DriverTrackingBatch(samples: [sample(1), sample(2), sample(3)], sessionID: id, generation: 2, deviceTime: 1_000)
    let outgoing = try wire.outgoing(path: path, data: batch.body)
    let body = try XCTUnwrap(JSONSerialization.jsonObject(with: outgoing.0!) as? [String: Any])
    XCTAssertEqual(body["deviceTime"] as? String, "1970-01-01T00:00:01.000Z")
    XCTAssertNil(body["schemaVersion"])
    let batchID = try XCTUnwrap(body["batchId"] as? String)
    XCTAssertEqual(batchID, batchID.lowercased()); XCTAssertNotNil(UUID(uuidString: batchID))
    XCTAssertEqual(outgoing.1, batchID)
    let raw: [String: Any] = ["sessionId": id, "generation": 2, "serverTime": "2026-10-31T12:00:00Z",
      "leaseExpiresAt": "2026-10-31T12:05:00Z", "action": "CONTINUE", "gpsState": "CLOCK_SKEW",
      "stopRequestRef": NSNull(), "accepted": [1], "duplicate": [3], "rejected": []]
    let normalized = try wire.incoming(path: path, data: JSONSerialization.data(withJSONObject: raw))
    let control = try DriverTrackingControl(normalized, additionalKeys: ["accepted", "duplicate", "rejected"])
    XCTAssertEqual(control.sessionID, id); XCTAssertEqual(control.gpsState, "CLOCK_SKEW")
    let (a, d, r) = try batch.acknowledgements(normalized)
    XCTAssertEqual(batch.sequences.subtracting(a.union(d).union(r)), [2])
  }

  func testProductionStopJournalRoundTripsAndRejectsGPSOrActiveClaims() throws {
    let id = "12345678-1234-1234-1234-123456789012"
    let stop = DriverTrackingStopRequest(sessionID: id, generation: 2, reason: "USER_STOP", requestedAt: 100, stoppedAt: 101, stopRequestRef: id)
    let data = try DriverTrackingStopJournal.encode(stop)
    let restored = try XCTUnwrap(DriverTrackingStopJournal.decode(data))
    XCTAssertEqual(restored.sessionID, id)
    XCTAssertEqual(try DriverTrackingStopJournal.encode(restored), data)
    var invalid = stop.body; invalid["sessionId"] = id; invalid["latitude"] = 0
    XCTAssertThrowsError(try DriverTrackingStopJournal.decode(JSONSerialization.data(withJSONObject: invalid)))
    invalid.removeValue(forKey: "latitude"); invalid["state"] = "ACTIVE"
    XCTAssertThrowsError(try DriverTrackingStopJournal.decode(JSONSerialization.data(withJSONObject: invalid)))
    XCTAssertNil(try DriverTrackingStopJournal.decode(DriverTrackingStopJournal.encode(nil)))
    let wire = DriverProductionTrackingWire()
    let path = "/driver-app/v1/tracking-sessions/" + id + "/state"
    let first = try wire.outgoing(path: path, data: JSONSerialization.data(withJSONObject: restored.body))
    let second = try DriverProductionTrackingWire().outgoing(path: path, data: JSONSerialization.data(withJSONObject: restored.body))
    XCTAssertEqual(first.1, id); XCTAssertEqual(first.1, second.1)
  }

  func testRawStartLinksRejectEncodingHostsAndAmbiguousVersions() {
    let id = "12345678-1234-1234-1234-123456789012"
    let raw = "vanridesdriver://run/start#v1." + id + ".7." + String(repeating: "A", count: 43)
    XCTAssertNotNil(DriverRunLink.parse(raw))
    XCTAssertNotNil(DriverRunLink.parse(raw.replacingOccurrences(of: "vanridesdriver://run/start", with: "https://links.example/motorista/saida"), host: "links.example"))
    for value in [raw + ".extra", raw.replacingOccurrences(of: ".7.", with: ".07."),
      raw.replacingOccurrences(of: "12345678", with: "%312345678"), raw.replacingOccurrences(of: ".7.", with: ".9007199254740992."),
      raw.replacingOccurrences(of: "/start#", with: "/start?next=x#"), String(raw.dropLast()) + "B"] {
      XCTAssertNil(DriverRunLink.parse(value))
    }
  }

  #if VANRIDES_GPS_SPIKE || DRIVER_TRACKING_STANDALONE_TESTS
  func testEndpointCannotLeakCredentialToOtherHostsOrPhysicalHTTP() throws {
    XCTAssertNoThrow(try DriverTrackingContract.endpoint("https://gps-spike.vanrides.matheusduarte.dev.br", allowSimulatorHTTP: false))
    XCTAssertNoThrow(try DriverTrackingContract.endpoint("http://127.0.0.1:18765", allowSimulatorHTTP: true))
    for endpoint in ["http://127.0.0.1:18765", "https://evil.example", "https://gps-spike.vanrides.matheusduarte.dev.br/other",
      "https://user@gps-spike.vanrides.matheusduarte.dev.br", "https://gps-spike.vanrides.matheusduarte.dev.br?token=x",
      "https://gps-spike.vanrides.matheusduarte.dev.br#x", "https://gps-spike.vanrides.matheusduarte.dev.br:444"] {
      XCTAssertThrowsError(try DriverTrackingContract.endpoint(endpoint, allowSimulatorHTTP: false))
    }
  }

  #endif

  func testLeaseCountsRequestLatencyAndSameExpiryCannotExtend() throws {
    var lease = DriverTrackingLease()
    try lease.update(control(), requestStarted: 100, now: 108)
    XCTAssertEqual(lease.deadline, 400)
    try lease.update(control(), requestStarted: 120, now: 128)
    XCTAssertEqual(lease.deadline, 400)
    try lease.update(control(expiry: 300_000, server: 20_000), requestStarted: 130, now: 138)
    XCTAssertEqual(lease.deadline, 400)
    XCTAssertEqual(lease.expiresAt, 310_000)
  }

  func testLateResponseCannotResurrectAnExpiredLease() throws {
    var lease = DriverTrackingLease()
    try lease.update(control(), requestStarted: 100, now: 108)
    XCTAssertThrowsError(try lease.update(control(expiry: 330_000, server: 30_000), requestStarted: 399, now: 401))
    var initial = DriverTrackingLease()
    XCTAssertThrowsError(try initial.update(control(expiry: 11_000), requestStarted: 100, now: 102))
  }

  func testStopRetryBackoffIsBoundedAndCountsFromEachFailure() {
    var retry = DriverTrackingStopRetry()
    var now: Double = 100
    XCTAssertEqual(retry.remainingDelay(now: now), 0)
    for delay: Double in [2, 4, 8, 16, 30, 30, 30] {
      XCTAssertEqual(retry.failed(now: now), delay)
      XCTAssertEqual(retry.nextAttemptAt, now + delay)
      XCTAssertEqual(retry.remainingDelay(now: now + delay - 0.5), 0.5)
      XCTAssertEqual(retry.remainingDelay(now: now + delay + 60), 0)
      // Request latency belongs before the next failure, not inside its retry delay.
      now += delay + 8
    }
  }

  func testStopRetryResetRemovesWaitAndRestartsBackoff() {
    var retry = DriverTrackingStopRetry()
    XCTAssertEqual(retry.failed(now: 100), 2)
    XCTAssertEqual(retry.failed(now: 110), 4)
    retry.reset()
    XCTAssertNil(retry.nextAttemptAt)
    XCTAssertEqual(retry.remainingDelay(now: 111), 0)
    XCTAssertEqual(retry.failed(now: 111), 2)
  }

  func testStoppedRequestKeepsOriginalNativeRecordDuringRetries() throws {
    let request = DriverTrackingStopRequest(sessionID: "synthetic_stop", generation: 3,
      reason: "LEASE_EXPIRED", requestedAt: 1_000, stoppedAt: 1_001)
    let original = try JSONSerialization.data(withJSONObject: request.body, options: [.sortedKeys])
    var retry = DriverTrackingStopRetry()
    for now: Double in [100, 1_000, 10_000] {
      _ = retry.failed(now: now)
      XCTAssertEqual(try JSONSerialization.data(withJSONObject: request.body, options: [.sortedKeys]), original)
    }
    XCTAssertEqual(request.sessionID, "synthetic_stop")
    XCTAssertEqual(request.generation, 3)
    XCTAssertEqual(request.body["state"] as? String, "STOPPED")
    XCTAssertEqual(request.body["stopRequestedAt"] as? Int64, 1_000)
    XCTAssertEqual(request.body["stoppedAt"] as? Int64, 1_001)
    XCTAssertEqual(request.body["reason"] as? String, "LEASE_EXPIRED")
  }

  func testLeaseAndIntegerContractRejectsMalformedControl() throws {
    XCTAssertThrowsError(try control(expiry: 310_001))
    XCTAssertThrowsError(try DriverTrackingContract.integer(true))
    XCTAssertThrowsError(try DriverTrackingContract.integer(1.5))
    XCTAssertThrowsError(try DriverTrackingContract.integer(-1))
    XCTAssertFalse(DriverTrackingContract.identifier("private/path"))
    XCTAssertEqual(try DriverTrackingContract.integer(7), 7)
    XCTAssertFalse(try control().isWithinCredential(expiry: nil))
    XCTAssertFalse(try control().isWithinCredential(expiry: 309_999))
    XCTAssertTrue(try control().isWithinCredential(expiry: 310_000))
  }

  func testQueueIsBoundedAndExpiresWithoutNetwork() {
    var queue = DriverTrackingQueue()
    for index in 1...125 { queue.append(sample(Int64(index)), now: 0) }
    XCTAssertEqual(queue.samples.count, 120)
    XCTAssertEqual(queue.dropped, 5)
    queue.purge(now: 120)
    XCTAssertTrue(queue.samples.isEmpty)
    XCTAssertEqual(queue.dropped, 125)
  }

  func testPartialACKPreservesGapsAndRetryBodyExactly() throws {
    var queue = DriverTrackingQueue()
    (1...3).forEach { queue.append(sample(Int64($0)), now: 0) }
    let batch = try DriverTrackingBatch(samples: queue.samples, sessionID: "synthetic", generation: 1, deviceTime: 1_000)
    let bytes = batch.body
    let (accepted, duplicates, rejected) = try batch.acknowledgements(["accepted": [1], "duplicate": [3], "rejected": []])
    XCTAssertEqual(queue.remove(accepted.union(duplicates).union(rejected)), [1, 3])
    XCTAssertEqual(queue.samples.map(\.sequence), [2])
    XCTAssertEqual(batch.body, bytes)
    XCTAssertEqual(queue.remove(accepted), [])
  }

  func testACKCannotRemoveUnknownOrConflictingSequences() throws {
    let batch = try DriverTrackingBatch(samples: [sample(1), sample(2)], sessionID: "synthetic", generation: 1, deviceTime: 1_000)
    XCTAssertThrowsError(try batch.acknowledgements(["accepted": [3], "duplicate": [], "rejected": []]))
    XCTAssertThrowsError(try batch.acknowledgements(["accepted": [1], "duplicate": [1], "rejected": []]))
    XCTAssertThrowsError(try batch.acknowledgements(["accepted": [], "duplicate": [], "rejected": [["sequence": 1, "code": "bad secret message"]]]))
  }

  func testBatchNeverExceedsThirtySamplesOrBodyLimit() throws {
    let batch = try DriverTrackingBatch(samples: (1...120).map { sample(Int64($0)) },
      sessionID: "synthetic", generation: 1, deviceTime: 1_000)
    XCTAssertEqual(batch.sequences.count, 30)
    XCTAssertLessThanOrEqual(batch.body.count, 32_768)
    let json = try XCTUnwrap(JSONSerialization.jsonObject(with: batch.body) as? [String: Any])
    XCTAssertEqual((json["samples"] as? [Any])?.count, 30)
  }
}

#if DRIVER_TRACKING_STANDALONE_TESTS
@main
struct DriverTrackingCoreTestMain {
  static func main() {
    let suite = DriverTrackingCoreTests.defaultTestSuite
    suite.run()
    guard let run = suite.testRun, run.executionCount == 14, run.totalFailureCount == 0 else { exit(1) }
  }
}
#endif
