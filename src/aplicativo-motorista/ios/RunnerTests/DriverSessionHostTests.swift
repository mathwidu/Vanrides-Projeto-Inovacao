#if VANRIDES_DRIVER_MVP || DRIVER_SESSION_STANDALONE_TESTS
import Foundation
import CryptoKit
import Security
import XCTest
#if !DRIVER_SESSION_STANDALONE_TESTS
@testable import Runner
#endif

private let fixtureNow = Date(timeIntervalSince1970: 1_900_000_000)
private let fixtureOrigin = "https://pairing.vanrides.invalid"
private let fixturePairing = "00000000-0000-4000-8000-000000000002"
private let fixtureDevice = "00000000-0000-4000-8000-000000000003"
private let fixtureDriver = "00000000-0000-4000-8000-000000000004"
private func fixtureToken(_ byte: UInt8) -> String { DriverSessionCodec.base64(Data(repeating: byte, count: 32)) }

private final class SessionMemoryStore: DriverSessionStore {
  var bytes: Data?
  var failSave = false
  var writes = 0
  var reads = 0
  var rejectSave: ((Data) -> Bool)?
  func load() throws -> Data? { reads += 1; return bytes }
  func save(_ data: Data) throws {
    if failSave || rejectSave?(data) == true { throw DriverSessionFailure.storage }
    writes += 1; bytes = data
  }
}

// Test fixture only: neither a Keychain item nor an exportable runtime fallback.
private final class SessionFixtureKeys: DriverSessionKeyProvider {
  var values: [String: DriverSessionSigningKey] = [:]
  var creates = 0
  var randomCalls = 0
  var failNextCreation = false
  func key(tag: String, create: Bool) throws -> DriverSessionSigningKey {
    if let value = values[tag] { return value }
    guard create else { throw DriverSessionFailure.key }
    if failNextCreation { failNextCreation = false; throw DriverSessionFailure.key }
    creates += 1
    var error: Unmanaged<CFError>?
    let attrs: [String: Any] = [kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
      kSecAttrKeySizeInBits as String: 256, kSecPrivateKeyAttrs as String: [kSecAttrIsPermanent as String: false]]
    guard let key = SecKeyCreateRandomKey(attrs as CFDictionary, &error) else { throw DriverSessionFailure.key }
    let value = try DriverSessionSecKey(key: key)
    values[tag] = value
    return value
  }
  func random(count: Int) throws -> Data { randomCalls += 1; return Data(repeating: 67, count: count) }
}

private final class SessionFixtureHTTP: DriverSessionHTTP {
  var requests: [URLRequest] = []
  var responses: [(URLRequest) throws -> DriverSessionHTTPResponse] = []
  var delayNanoseconds: UInt64 = 0
  func send(_ request: URLRequest) async throws -> DriverSessionHTTPResponse {
    requests.append(request)
    if delayNanoseconds > 0 { try await Task.sleep(nanoseconds: delayNanoseconds) }
    guard !responses.isEmpty else { throw DriverSessionFailure.unavailable }
    return try responses.removeFirst()(request)
  }
  func enqueue(_ body: [String: Any], status: Int = 200, nonce: String? = fixtureToken(42)) {
    responses.append { _ in DriverSessionHTTPResponse(status: status, nonce: nonce, body: try DriverSessionCodec.json(body)) }
  }
  func loseResponse() { responses.append { _ in throw URLError(.networkConnectionLost) } }
}

final class DriverSessionHostTests: XCTestCase {
  private func controller(_ store: SessionMemoryStore, _ keys: SessionFixtureKeys, _ http: SessionFixtureHTTP,
                          now: @escaping () -> Date = { fixtureNow }) throws -> DriverSessionController {
    try DriverSessionController(configuration: DriverSessionConfiguration(origin: fixtureOrigin, appVersion: "0.1.0+060"),
      store: store, keys: keys, http: http, now: now)
  }

  private func begun(_ store: SessionMemoryStore, _ keys: SessionFixtureKeys, _ http: SessionFixtureHTTP) async throws -> DriverSessionController {
    let value = try controller(store, keys, http)
    http.enqueue(beginResponse())
    _ = try await value.perform("beginPairing")
    return value
  }

  private func approved(_ store: SessionMemoryStore, _ keys: SessionFixtureKeys, _ http: SessionFixtureHTTP,
                        readiness: String = "READY") async throws -> DriverSessionController {
    let value = try await begun(store, keys, http)
    http.enqueue(pairResponse("APPROVED")); http.enqueue(tokens()); http.enqueue(driverResponse(readiness))
    _ = try await value.perform("syncSession")
    return value
  }

  func testConcurrentPairingSyncMakesTrackingTemporarilyBusyWithoutRejectingAuthorization() async throws {
    let store = SessionMemoryStore(); let keys = SessionFixtureKeys(); let http = SessionFixtureHTTP()
    let controller = try await approved(store, keys, http)
    http.delayNanoseconds = 60_000_000
    http.enqueue(driverResponse("READY"))
    let sync = Task { try await controller.perform("syncSession") }
    try await Task.sleep(nanoseconds: 15_000_000)
    let before = http.requests.count
    do { _ = try await controller.trackingRequest(path: "/driver-app/v1/tracking-status", body: nil, idempotency: nil); XCTFail("Expected transient busy") }
    catch let failure as DriverSessionFailure { XCTAssertEqual(failure, .busy) }
    XCTAssertEqual(http.requests.count, before)
    _ = try await sync.value
    http.delayNanoseconds = 0; http.enqueue(["ok": true])
    _ = try await controller.trackingRequest(path: "/driver-app/v1/tracking-status", body: nil, idempotency: nil)
    XCTAssertEqual(http.requests.last?.url?.path, "/driver-app/v1/tracking-status")
    let snapshot = await controller.snapshot(); XCTAssertEqual(snapshot["state"] as? String, "READY")
  }

  func testTrackingRequestBindsAccessBodyAndPathWithoutChanging060Status() async throws {
    let store = SessionMemoryStore(); let keys = SessionFixtureKeys(); let http = SessionFixtureHTTP()
    let controller = try await approved(store, keys, http)
    let path = "/driver-app/v1/tracking-sessions/" + fixtureDevice + "/state"
    let body = try DriverSessionCodec.json(["sessionId": fixtureDevice, "generation": 2, "state": "ACTIVE"])
    http.enqueue(["ok": true])
    _ = try await controller.trackingRequest(path: path, body: body, idempotency: fixturePairing)
    let sent = try XCTUnwrap(http.requests.last)
    XCTAssertEqual(sent.url?.absoluteString, fixtureOrigin + path)
    XCTAssertEqual(sent.httpBody, body)
    XCTAssertEqual(sent.value(forHTTPHeaderField: "Idempotency-Key"), fixturePairing)
    XCTAssertTrue(sent.value(forHTTPHeaderField: "Authorization")?.hasPrefix("DPoP ") == true)
    let proof = try XCTUnwrap(sent.value(forHTTPHeaderField: "DPoP"))
    let encoded = String(proof.split(separator: ".")[1])
    let claimsData = try XCTUnwrap(Data(base64Encoded: encoded.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/") + String(repeating: "=", count: (4 - encoded.count % 4) % 4)))
    let claims = try XCTUnwrap(JSONSerialization.jsonObject(with: claimsData) as? [String: Any])
    XCTAssertEqual(claims["htu"] as? String, fixtureOrigin + path)
    XCTAssertEqual(claims["vr_body_sha256"] as? String, DriverSessionCodec.digest(body))
    do { _ = try await controller.trackingRequest(path: "/driver-app/v1/session/revoke", body: body, idempotency: fixturePairing); XCTFail("Expected closed tracking routes") }
    catch { }
  }

  private func beginResponse(code: String = "BCDFGHJZ") -> [String: Any] {
    ["pairingId": fixturePairing, "userCode": code, "expiresAt": DriverSessionCodec.iso(fixtureNow.addingTimeInterval(300)),
      "serverTime": DriverSessionCodec.iso(fixtureNow)]
  }

  private func pairResponse(_ state: String) -> [String: Any] {
    ["pairingId": fixturePairing, "state": state, "expiresAt": DriverSessionCodec.iso(fixtureNow.addingTimeInterval(300)),
      "serverTime": DriverSessionCodec.iso(fixtureNow)]
  }

  private func tokens(access: UInt8 = 1, refresh: UInt8 = 2) -> [String: Any] {
    ["deviceId": fixtureDevice, "accessToken": fixtureToken(access), "refreshToken": fixtureToken(refresh), "tokenType": "DPoP",
      "accessExpiresAt": DriverSessionCodec.iso(fixtureNow.addingTimeInterval(300)),
      "refreshExpiresAt": DriverSessionCodec.iso(fixtureNow.addingTimeInterval(30 * 86_400)),
      "serverTime": DriverSessionCodec.iso(fixtureNow)]
  }

  private func driverResponse(_ readiness: String) -> [String: Any] {
    ["deviceId": fixtureDevice, "driverId": fixtureDriver, "displayName": NSNull(), "profileStatus": "ACTIVE",
      "deviceState": "ACTIVE", "readiness": readiness, "runId": NSNull(), "serverTime": DriverSessionCodec.iso(fixtureNow)]
  }

  private func savedRecord(_ store: SessionMemoryStore) throws -> [String: Any] {
    try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(store.bytes)) as? [String: Any])
  }

  // The JSON layout is unchanged from the previous binary; only its version marks
  // that the real device key was provisioned under the former accessibility policy.
  private func markLegacy(_ store: SessionMemoryStore) throws -> String {
    var record = try savedRecord(store)
    record["version"] = 1
    store.bytes = try DriverSessionCodec.json(record)
    return try XCTUnwrap(record["keyTag"] as? String)
  }

  private func proof(_ request: URLRequest) throws -> (header: [String: Any], claims: [String: Any]) {
    let raw = try XCTUnwrap(request.value(forHTTPHeaderField: "DPoP"))
    let parts = raw.split(separator: ".").map(String.init)
    XCTAssertEqual(parts.count, 3)
    func decode(_ raw: String) throws -> Data {
      let value = raw.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
      return try XCTUnwrap(Data(base64Encoded: value + String(repeating: "=", count: (4 - value.count % 4) % 4)))
    }
    let header = try XCTUnwrap(JSONSerialization.jsonObject(with: decode(parts[0])) as? [String: Any])
    let claims = try XCTUnwrap(JSONSerialization.jsonObject(with: decode(parts[1])) as? [String: Any])
    let jwk = try XCTUnwrap(header["jwk"] as? [String: String])
    var publicBytes = Data([4]); publicBytes.append(try decode(XCTUnwrap(jwk["x"]))); publicBytes.append(try decode(XCTUnwrap(jwk["y"])))
    let publicKey = try P256.Signing.PublicKey(x963Representation: publicBytes)
    let signature = try P256.Signing.ECDSASignature(rawRepresentation: decode(parts[2]))
    XCTAssertTrue(publicKey.isValidSignature(signature, for: Data((parts[0] + "." + parts[1]).utf8)))
    XCTAssertEqual(header["typ"] as? String, "dpop+jwt"); XCTAssertEqual(header["alg"] as? String, "ES256")
    XCTAssertEqual(claims["htu"] as? String, request.url?.absoluteString)
    XCTAssertEqual(claims["htm"] as? String, request.httpMethod)
    return (header, claims)
  }

  func testOriginIsImmutableHTTPSBuildInput() {
    XCTAssertNotNil(DriverSessionConfiguration(origin: fixtureOrigin, appVersion: "1.0+060"))
    for invalid in [nil, "", "$(VANRIDES_PAIRING_API_ORIGIN)", "http://localhost:8080", "https://user@host",
      "https://host/", "https://host/path", "https://host?q=x", "https://host#fragment"] as [String?] {
      XCTAssertNil(DriverSessionConfiguration(origin: invalid, appVersion: "1.0"))
    }
    XCTAssertNil(DriverSessionConfiguration(origin: fixtureOrigin, appVersion: "unsafe/version"))
  }

  func testUnconfiguredAndReadMethodsNeverCreateKeyOrNetwork() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let inert = try DriverSessionController(configuration: nil, store: store, keys: keys, http: http)
    for method in ["beginPairing", "syncSession", "refreshSession", "revokeSession"] {
      let state = try await inert.perform(method)
      XCTAssertEqual(state["state"] as? String, "UNCONFIGURED")
    }
    XCTAssertEqual(store.reads, 0)
    let value = try controller(store, keys, http)
    let state = await value.snapshot(), code = await value.showPairingCode()
    XCTAssertEqual(state["state"] as? String, "UNPAIRED"); XCTAssertNil(code)
    _ = try await value.perform("syncSession")
    XCTAssertEqual(keys.creates, 0); XCTAssertEqual(store.writes, 0); XCTAssertTrue(http.requests.isEmpty)
  }

  func testPKCES256RFCVectorAndCanonicalTokenValidation() {
    let verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    XCTAssertEqual(DriverSessionCodec.digest(Data(verifier.utf8)), "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
    XCTAssertTrue(DriverSessionCodec.canonicalToken(fixtureToken(1)))
    XCTAssertFalse(DriverSessionCodec.canonicalToken(String(repeating: "A", count: 42) + "B"))
  }

  func testNonceNegotiationUsesJOSEFreshJTIAndExactJournalBytes() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try controller(store, keys, http)
    http.responses.append { request in
      let journal = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(store.bytes)) as? [String: Any])
      XCTAssertNotNil(journal["journal"])
      return DriverSessionHTTPResponse(status: 401, nonce: fixtureToken(3), body: try DriverSessionCodec.json(["code": "PROOF_NONCE_REQUIRED"]))
    }
    http.enqueue(beginResponse())
    let state = try await value.perform("beginPairing")
    XCTAssertEqual(state["state"] as? String, "PENDING"); XCTAssertEqual(Set(state.keys), ["state", "expiresAt"])
    let code = await value.showPairingCode(); XCTAssertEqual(code, "BCDFGHJZ")
    XCTAssertEqual(http.requests.count, 2)
    let first = http.requests[0], second = http.requests[1]
    XCTAssertEqual(first.httpBody, second.httpBody)
    XCTAssertEqual(first.value(forHTTPHeaderField: "Idempotency-Key"), second.value(forHTTPHeaderField: "Idempotency-Key"))
    let a = try proof(first), b = try proof(second)
    XCTAssertNil(a.claims["nonce"]); XCTAssertEqual(b.claims["nonce"] as? String, fixtureToken(3))
    XCTAssertNotEqual(a.claims["jti"] as? String, b.claims["jti"] as? String)
    XCTAssertEqual(b.claims["vr_body_sha256"] as? String, DriverSessionCodec.digest(try XCTUnwrap(second.httpBody)))
    XCTAssertNil(b.claims["ath"])
    let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(second.httpBody)) as? [String: Any])
    XCTAssertEqual(Set(body.keys), ["publicJwk", "pkceChallenge", "pkceMethod", "platform", "appVersion"])
    XCTAssertEqual(body["pkceMethod"] as? String, "S256"); XCTAssertEqual(body["platform"] as? String, "IOS")
    XCTAssertEqual(body["pkceChallenge"] as? String, DriverSessionCodec.digest(Data(fixtureToken(67).utf8)))
    XCTAssertEqual(keys.creates, 1)
  }

  func testLostBeginRestartsWithSameBytesAndIdempotency() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try controller(store, keys, http)
    http.loseResponse()
    do { _ = try await value.perform("beginPairing"); XCTFail("Expected lost response") } catch {}
    let state = await value.snapshot(); XCTAssertEqual(state["state"] as? String, "REQUESTING")
    let restarted = try controller(store, keys, http)
    http.enqueue(beginResponse()); http.enqueue(pairResponse("PENDING"))
    _ = try await restarted.perform("syncSession")
    XCTAssertEqual(http.requests[0].httpBody, http.requests[1].httpBody)
    XCTAssertEqual(http.requests[0].value(forHTTPHeaderField: "Idempotency-Key"), http.requests[1].value(forHTTPHeaderField: "Idempotency-Key"))
    XCTAssertEqual(keys.creates, 1); XCTAssertEqual(keys.randomCalls, 1)
    XCTAssertNotEqual(try proof(http.requests[0]).claims["jti"] as? String, try proof(http.requests[1]).claims["jti"] as? String)
  }

  func testCodeAlphabetAndClosedResponseRejectMalformedBegin() async throws {
    for code in ["BCDFGHJL", "BCDFGHJA", "BCDFGHJ", "bcdfghjz"] {
      let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
      let value = try controller(store, keys, http)
      http.enqueue(beginResponse(code: code))
      do { _ = try await value.perform("beginPairing"); XCTFail("Malformed code") } catch {}
      let returned = await value.showPairingCode(); XCTAssertNil(returned)
    }
  }

  func testApprovalRedeemsAndMapsSetupThenReadyWithoutLeakingIdentity() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http, readiness: "SETUP_REQUIRED")
    let setup = await value.snapshot(); XCTAssertEqual(setup["state"] as? String, "SETUP_REQUIRED")
    XCTAssertEqual(Set(setup.keys), ["state"])
    let code = await value.showPairingCode(); XCTAssertNil(code)
    let redeem = http.requests[2]
    let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(redeem.httpBody)) as? [String: Any])
    XCTAssertEqual(body["pairingId"] as? String, fixturePairing)
    XCTAssertEqual(body["pkceVerifier"] as? String, fixtureToken(67))
    let statusProof = try proof(http.requests[3])
    XCTAssertEqual(statusProof.claims["ath"] as? String, DriverSessionCodec.digest(Data(fixtureToken(1).utf8)))
    XCTAssertNil(statusProof.claims["vr_body_sha256"])
    http.enqueue(driverResponse("READY"))
    let ready = try await value.perform("syncSession")
    XCTAssertEqual(ready["state"] as? String, "READY"); XCTAssertEqual(Set(ready.keys), ["state"])
  }

  func testLostRedeemRecoversBeforeConsultingConsumedPairing() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await begun(store, keys, http)
    http.enqueue(pairResponse("APPROVED")); http.loseResponse()
    do { _ = try await value.perform("syncSession"); XCTFail("Expected lost redeem") } catch {}
    let restarted = try controller(store, keys, http)
    http.enqueue(tokens()); http.enqueue(driverResponse("READY"))
    let ready = try await restarted.perform("syncSession")
    XCTAssertEqual(ready["state"] as? String, "READY")
    XCTAssertEqual(http.requests[2].httpBody, http.requests[3].httpBody)
    XCTAssertEqual(http.requests[2].value(forHTTPHeaderField: "Idempotency-Key"), http.requests[3].value(forHTTPHeaderField: "Idempotency-Key"))
    XCTAssertTrue(http.requests[3].url!.path.hasSuffix("/redeem"))
  }

  func testLostRefreshPersistsOldTokenAndJournalUntilAtomicReplacement() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.loseResponse()
    do { _ = try await value.perform("refreshSession"); XCTFail("Expected lost refresh") } catch {}
    let restarted = try controller(store, keys, http)
    http.enqueue(tokens(access: 3, refresh: 4)); http.enqueue(driverResponse("READY"))
    _ = try await restarted.perform("refreshSession")
    let first = http.requests[4], retried = http.requests[5]
    XCTAssertEqual(first.httpBody, retried.httpBody)
    XCTAssertEqual(first.value(forHTTPHeaderField: "Idempotency-Key"), retried.value(forHTTPHeaderField: "Idempotency-Key"))
    XCTAssertNil(retried.value(forHTTPHeaderField: "Authorization"))
    XCTAssertNotEqual(try proof(first).claims["jti"] as? String, try proof(retried).claims["jti"] as? String)
    http.enqueue(tokens(access: 5, refresh: 6)); http.enqueue(driverResponse("READY"))
    _ = try await restarted.perform("refreshSession")
    let nextBody = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(http.requests[7].httpBody)) as? [String: Any])
    XCTAssertEqual(nextBody["refreshToken"] as? String, fixtureToken(4))
    XCTAssertNotEqual(retried.value(forHTTPHeaderField: "Idempotency-Key"), http.requests[7].value(forHTTPHeaderField: "Idempotency-Key"))
  }

  func testConcurrentRefreshCannotCreateAnotherRotation() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.delayNanoseconds = 30_000_000
    http.enqueue(tokens(access: 3, refresh: 4)); http.enqueue(driverResponse("READY"))
    async let first = value.perform("refreshSession")
    try await Task.sleep(nanoseconds: 5_000_000)
    _ = try await value.perform("refreshSession")
    _ = try await first
    XCTAssertEqual(http.requests.filter { $0.url!.path.hasSuffix("/refresh") }.count, 1)
  }

  func testRevokeRemainsPendingAcrossLostACKAndOnlyThenClearsTokens() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.loseResponse()
    do { _ = try await value.perform("revokeSession"); XCTFail("Expected lost revoke") } catch {}
    let pending = await value.snapshot(); XCTAssertEqual(pending["state"] as? String, "REVOKING")
    let restarted = try controller(store, keys, http)
    http.enqueue(["deviceId": fixtureDevice, "state": "REVOKED", "serverTime": DriverSessionCodec.iso(fixtureNow)])
    let revoked = try await restarted.perform("syncSession")
    XCTAssertEqual(revoked["state"] as? String, "REVOKED")
    XCTAssertEqual(http.requests[4].httpBody, Data("{}".utf8)); XCTAssertEqual(http.requests[4].httpBody, http.requests[5].httpBody)
    XCTAssertEqual(http.requests[4].value(forHTTPHeaderField: "Idempotency-Key"), http.requests[5].value(forHTTPHeaderField: "Idempotency-Key"))
    let claims = try proof(http.requests[5]).claims
    XCTAssertEqual(claims["ath"] as? String, DriverSessionCodec.digest(Data(fixtureToken(1).utf8)))
    XCTAssertEqual(claims["vr_body_sha256"] as? String, DriverSessionCodec.digest(Data("{}".utf8)))
    let saved = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(store.bytes)) as? [String: Any])
    XCTAssertNil(saved["tokens"]); XCTAssertNil(saved["journal"])
  }

  func testNonceChallengesAreBoundedAndDoNotReplaceJournal() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try controller(store, keys, http)
    for _ in 0..<4 { http.enqueue(["code": "PROOF_NONCE_REQUIRED"], status: 401, nonce: fixtureToken(8)) }
    do { _ = try await value.perform("beginPairing"); XCTFail("Expected bounded failure") } catch {}
    XCTAssertEqual(http.requests.count, 3)
    XCTAssertEqual(Set(http.requests.compactMap { $0.value(forHTTPHeaderField: "Idempotency-Key") }).count, 1)
    let claims = try http.requests.map { try proof($0).claims["jti"] as! String }
    XCTAssertEqual(Set(claims).count, 3)
    let state = await value.snapshot(); XCTAssertEqual(state["state"] as? String, "REQUESTING")
  }

  func testMalformedNonceAndOversizedResponseNeverExposeCode() async throws {
    for invalidNonce in ["bad", String(repeating: "A", count: 42) + "B"] {
      let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
      let value = try controller(store, keys, http)
      http.enqueue(beginResponse(), nonce: invalidNonce)
      do { _ = try await value.perform("beginPairing"); XCTFail("Malformed nonce") } catch {}
      let code = await value.showPairingCode(); XCTAssertNil(code)
      XCTAssertEqual(http.requests.count, 1)
    }
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try controller(store, keys, http)
    http.responses.append { _ in DriverSessionHTTPResponse(status: 200, nonce: nil, body: Data(repeating: 65, count: 32_769)) }
    do { _ = try await value.perform("beginPairing"); XCTFail("Oversized response") } catch {}
  }

  func testExpiredCodeDisappearsAndExplicitBeginStartsNewRequest() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    _ = try await begun(store, keys, http)
    let restarted = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(301) })
    let state = await restarted.snapshot(), code = await restarted.showPairingCode()
    XCTAssertEqual(state["state"] as? String, "EXPIRED"); XCTAssertEqual(Set(state.keys), ["state"]); XCTAssertNil(code)
    http.enqueue(beginResponse())
    _ = try await restarted.perform("beginPairing")
    XCTAssertNotEqual(http.requests[0].value(forHTTPHeaderField: "Idempotency-Key"), http.requests[1].value(forHTTPHeaderField: "Idempotency-Key"))
    XCTAssertEqual(keys.creates, 1)
  }

  func testStorageFailurePreventsPostAndCorruptOrOtherOriginFailClosed() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try controller(store, keys, http)
    store.failSave = true
    do { _ = try await value.perform("beginPairing"); XCTFail("Expected storage failure") } catch {}
    XCTAssertTrue(http.requests.isEmpty); XCTAssertEqual(keys.creates, 0)
    store.failSave = false; store.bytes = Data("invalid".utf8)
    XCTAssertThrowsError(try controller(store, keys, http))
    store.bytes = nil
    _ = try await begun(store, keys, http)
    XCTAssertThrowsError(try DriverSessionController(configuration: DriverSessionConfiguration(origin: "https://other.invalid", appVersion: "1"),
      store: store, keys: keys, http: http))
    var corrupted = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(store.bytes)) as? [String: Any])
    corrupted["userCode"] = "ATTACK"
    store.bytes = try DriverSessionCodec.json(corrupted)
    XCTAssertThrowsError(try controller(store, keys, http))
  }

  func testRefreshInvalidCredentialCannotReissueWithNewIdempotency() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.enqueue(["code": "INVALID_CREDENTIAL"], status: 401)
    do { _ = try await value.perform("refreshSession"); XCTFail("Expected invalid token") } catch {}
    let state = await value.snapshot(); XCTAssertEqual(state["state"] as? String, "EXPIRED")
    let terminal = try await value.perform("refreshSession")
    XCTAssertEqual(terminal["state"] as? String, "EXPIRED")
    XCTAssertEqual(http.requests.count, 5)
    http.enqueue(beginResponse())
    let newPairing = try await value.perform("beginPairing")
    XCTAssertEqual(newPairing["state"] as? String, "PENDING")
    XCTAssertEqual(http.requests.last?.url?.path, "/driver-app/v1/pairings/begin")
  }

  func testNetworkAndRateLimitAreTypedAndKeepExactJournal() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try controller(store, keys, http)
    http.loseResponse()
    do {
      _ = try await value.perform("beginPairing"); XCTFail("Expected network failure")
    } catch { XCTAssertEqual((error as? DriverSessionFailure)?.rawValue, "NETWORK_ERROR") }
    let networkState = await value.snapshot(); XCTAssertEqual(networkState["state"] as? String, "REQUESTING")
    http.enqueue(["code": "RATE_LIMITED", "message": "Discard this remote text"], status: 429)
    do {
      _ = try await value.perform("syncSession"); XCTFail("Expected rate limit")
    } catch { XCTAssertEqual((error as? DriverSessionFailure)?.rawValue, "RATE_LIMITED") }
    let limitedState = await value.snapshot(); XCTAssertEqual(limitedState["state"] as? String, "REQUESTING")
    http.enqueue(beginResponse()); http.enqueue(pairResponse("PENDING"))
    let pending = try await value.perform("syncSession")
    XCTAssertEqual(pending["state"] as? String, "PENDING")
    XCTAssertEqual(http.requests[0].httpBody, http.requests[1].httpBody)
    XCTAssertEqual(http.requests[1].httpBody, http.requests[2].httpBody)
    XCTAssertEqual(Set(http.requests.prefix(3).compactMap { $0.value(forHTTPHeaderField: "Idempotency-Key") }).count, 1)
  }

  func testColdRestartReconcilesExpiredAccessWithSameKeyAndLiveRefresh() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    _ = try await approved(store, keys, http)
    let restarted = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(301) })
    let initial = await restarted.snapshot()
    XCTAssertEqual(initial["state"] as? String, "SETUP_REQUIRED")
    var rotated = tokens(access: 3, refresh: 4)
    rotated["serverTime"] = DriverSessionCodec.iso(fixtureNow.addingTimeInterval(301))
    rotated["accessExpiresAt"] = DriverSessionCodec.iso(fixtureNow.addingTimeInterval(601))
    http.enqueue(rotated); http.enqueue(driverResponse("READY"))
    let ready = try await restarted.perform("syncSession")
    XCTAssertEqual(ready["state"] as? String, "READY")
    XCTAssertEqual(http.requests[4].url?.path, "/driver-app/v1/session/refresh")
    XCTAssertEqual(http.requests[5].url?.path, "/driver-app/v1/status")
    let refreshBody = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(http.requests[4].httpBody)) as? [String: Any])
    XCTAssertEqual(refreshBody["refreshToken"] as? String, fixtureToken(2))
    XCTAssertEqual(try proof(http.requests[0]).header["jwk"] as? [String: String],
      try proof(http.requests[4]).header["jwk"] as? [String: String])
    XCTAssertEqual(keys.creates, 1)
    let anotherRestart = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(302) })
    let notYetVerified = await anotherRestart.snapshot()
    XCTAssertEqual(notYetVerified["state"] as? String, "SETUP_REQUIRED")
    http.enqueue(driverResponse("READY"))
    let verified = try await anotherRestart.perform("syncSession")
    XCTAssertEqual(verified["state"] as? String, "READY")
    XCTAssertEqual(http.requests.filter { $0.url?.path == "/driver-app/v1/session/refresh" }.count, 1)
  }

  func testColdRestartWithExpiredRefreshCannotClaimReadyOrRotate() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    _ = try await approved(store, keys, http)
    let restarted = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(30 * 86_400 + 1) })
    let initial = await restarted.snapshot()
    XCTAssertEqual(initial["state"] as? String, "EXPIRED")
    let before = http.requests.count
    do { _ = try await restarted.perform("syncSession"); XCTFail("Expired refresh") } catch {}
    XCTAssertEqual(http.requests.count, before)
    let expired = await restarted.snapshot()
    XCTAssertEqual(expired["state"] as? String, "EXPIRED")
    XCTAssertEqual(keys.creates, 1)
    var freshBegin = beginResponse()
    freshBegin["serverTime"] = DriverSessionCodec.iso(fixtureNow.addingTimeInterval(30 * 86_400 + 1))
    freshBegin["expiresAt"] = DriverSessionCodec.iso(fixtureNow.addingTimeInterval(30 * 86_400 + 301))
    http.enqueue(freshBegin)
    let pairedAgain = try await restarted.perform("beginPairing")
    XCTAssertEqual(pairedAgain["state"] as? String, "PENDING")
    XCTAssertEqual(http.requests.count, before + 1)
    XCTAssertEqual(http.requests.last?.url?.path, "/driver-app/v1/pairings/begin")
    XCTAssertEqual(keys.creates, 1)
  }

  func testIngressRejectedAccessRotatesOnceBeforeDeclaringSessionExpired() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.enqueue(["code": "INVALID_CREDENTIAL"], status: 401)
    http.enqueue(tokens(access: 3, refresh: 4)); http.enqueue(driverResponse("READY"))
    let ready = try await value.perform("syncSession")
    XCTAssertEqual(ready["state"] as? String, "READY")
    XCTAssertEqual(http.requests[4].url?.path, "/driver-app/v1/status")
    XCTAssertEqual(http.requests[5].url?.path, "/driver-app/v1/session/refresh")
    XCTAssertEqual(http.requests[6].url?.path, "/driver-app/v1/status")
    XCTAssertEqual(try proof(http.requests[6]).claims["ath"] as? String,
      DriverSessionCodec.digest(Data(fixtureToken(3).utf8)))
  }

  private func revokeACK() -> [String: Any] {
    ["deviceId": fixtureDevice, "state": "REVOKED", "serverTime": DriverSessionCodec.iso(fixtureNow.addingTimeInterval(301))]
  }

  private func lateRotation() -> [String: Any] {
    var result = tokens(access: 3, refresh: 4)
    result["serverTime"] = DriverSessionCodec.iso(fixtureNow.addingTimeInterval(301))
    result["accessExpiresAt"] = DriverSessionCodec.iso(fixtureNow.addingTimeInterval(601))
    return result
  }

  func testExpiredRevokeNeverReachedServerRotatesThenRevokesAfterRestart() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.loseResponse()
    do { _ = try await value.perform("revokeSession"); XCTFail("Lost revoke") } catch {}
    let restarted = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(301) })
    http.enqueue(lateRotation()); http.enqueue(revokeACK())
    let revoked = try await restarted.perform("syncSession")
    XCTAssertEqual(revoked["state"] as? String, "REVOKED")
    XCTAssertEqual(http.requests[5].url?.path, "/driver-app/v1/session/refresh")
    XCTAssertEqual(http.requests[6].url?.path, "/driver-app/v1/session/revoke")
    XCTAssertNotEqual(http.requests[4].value(forHTTPHeaderField: "Idempotency-Key"), http.requests[6].value(forHTTPHeaderField: "Idempotency-Key"))
    XCTAssertEqual(try proof(http.requests[6]).claims["ath"] as? String,
      DriverSessionCodec.digest(Data(fixtureToken(3).utf8)))
    XCTAssertEqual(try proof(http.requests[0]).header["jwk"] as? [String: String],
      try proof(http.requests[6]).header["jwk"] as? [String: String])
  }

  func testAppliedRevokeWithLostACKAndExpiredAccessEndsExpiredIfRefreshDenied() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.loseResponse()
    do { _ = try await value.perform("revokeSession"); XCTFail("Lost ACK") } catch {}
    let restarted = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(301) })
    // Server applied the first revoke: both old access and refresh are now unusable.
    http.enqueue(["code": "INVALID_CREDENTIAL"], status: 401)
    let expired = try await restarted.perform("syncSession")
    XCTAssertEqual(expired["state"] as? String, "EXPIRED")
    XCTAssertEqual(http.requests.last?.url?.path, "/driver-app/v1/session/refresh")
    let count = http.requests.count
    _ = try await restarted.perform("syncSession")
    _ = try await restarted.perform("revokeSession")
    XCTAssertEqual(http.requests.count, count)
    let state = await restarted.snapshot(); XCTAssertNotEqual(state["state"] as? String, "REVOKED")
  }

  func testRevokeIntentSurvivesRestartBetweenRefreshCommitAndNextPost() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.loseResponse()
    do { _ = try await value.perform("revokeSession"); XCTFail("Lost revoke") } catch {}
    let recovering = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(301) })
    store.rejectSave = { data in
      guard let record = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
        let journal = record["journal"] as? [String: Any], let tokens = record["tokens"] as? [String: Any] else { return false }
      return journal["kind"] as? String == "revoke" && tokens["access"] as? String == fixtureToken(3)
    }
    http.enqueue(lateRotation())
    do { _ = try await recovering.perform("syncSession"); XCTFail("Crash boundary") } catch {
      XCTAssertEqual(error as? DriverSessionFailure, .storage)
    }
    let pending = await recovering.snapshot(); XCTAssertEqual(pending["state"] as? String, "REVOKING")
    store.rejectSave = nil
    let restarted = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(302) })
    let cold = await restarted.snapshot(); XCTAssertEqual(cold["state"] as? String, "REVOKING")
    http.enqueue(revokeACK())
    let revoked = try await restarted.perform("syncSession")
    XCTAssertEqual(revoked["state"] as? String, "REVOKED")
    XCTAssertEqual(http.requests.filter { $0.url?.path == "/driver-app/v1/session/refresh" }.count, 1)
    XCTAssertEqual(http.requests.last?.value(forHTTPHeaderField: "Authorization"), "DPoP " + fixtureToken(3))
  }

  func testLostRefreshAfterReplayRetentionExpiresEndsExpiredAndAllowsExplicitPairing() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.loseResponse()
    do { _ = try await value.perform("refreshSession"); XCTFail("Lost rotating response") } catch {}
    let restarted = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(601) })
    http.enqueue(["code": "INTENT_EXPIRED"], status: 410)
    let expired = try await restarted.perform("syncSession")
    XCTAssertEqual(expired["state"] as? String, "EXPIRED")
    XCTAssertEqual(http.requests[4].httpBody, http.requests[5].httpBody)
    XCTAssertEqual(http.requests[4].value(forHTTPHeaderField: "Idempotency-Key"), http.requests[5].value(forHTTPHeaderField: "Idempotency-Key"))
    let before = http.requests.count
    _ = try await restarted.perform("syncSession")
    XCTAssertEqual(http.requests.count, before)
    var fresh = beginResponse()
    fresh["serverTime"] = DriverSessionCodec.iso(fixtureNow.addingTimeInterval(601))
    fresh["expiresAt"] = DriverSessionCodec.iso(fixtureNow.addingTimeInterval(901))
    http.enqueue(fresh)
    let paired = try await restarted.perform("beginPairing")
    XCTAssertEqual(paired["state"] as? String, "PENDING")
    XCTAssertEqual(http.requests.last?.url?.path, "/driver-app/v1/pairings/begin")
  }

  func testRevocationRefreshNetworkAndRateLimitKeepIntentAndSameOperation() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    _ = try await approved(store, keys, http)
    let value = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(301) })
    http.loseResponse()
    do { _ = try await value.perform("revokeSession"); XCTFail("Lost refresh response") } catch {
      XCTAssertEqual(error as? DriverSessionFailure, .network)
    }
    let offline = await value.snapshot(); XCTAssertEqual(offline["state"] as? String, "REVOKING")
    http.enqueue(["code": "RATE_LIMITED"], status: 429)
    do { _ = try await value.perform("syncSession"); XCTFail("Rate limited recovery") } catch {
      XCTAssertEqual(error as? DriverSessionFailure, .rateLimited)
    }
    let limited = await value.snapshot(); XCTAssertEqual(limited["state"] as? String, "REVOKING")
    let restarted = try controller(store, keys, http, now: { fixtureNow.addingTimeInterval(302) })
    http.enqueue(lateRotation()); http.enqueue(revokeACK())
    let revoked = try await restarted.perform("syncSession")
    XCTAssertEqual(revoked["state"] as? String, "REVOKED")
    let refreshes = http.requests.filter { $0.url?.path == "/driver-app/v1/session/refresh" }
    XCTAssertEqual(refreshes.count, 3)
    XCTAssertEqual(Set(refreshes.compactMap { $0.value(forHTTPHeaderField: "Idempotency-Key") }).count, 1)
    XCTAssertEqual(refreshes[0].httpBody, refreshes[1].httpBody)
    XCTAssertEqual(refreshes[1].httpBody, refreshes[2].httpBody)
  }

  func testLegacyLiveBindingAndRefreshReplayKeepVersionAndPublicKey() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.loseResponse()
    do { _ = try await value.perform("refreshSession"); XCTFail("Lost refresh") } catch {}
    let tag = try markLegacy(store)
    let restarted = try controller(store, keys, http)
    http.enqueue(tokens(access: 3, refresh: 4)); http.enqueue(driverResponse("READY"))
    let ready = try await restarted.perform("syncSession")
    XCTAssertEqual(ready["state"] as? String, "READY")
    XCTAssertEqual(http.requests[4].httpBody, http.requests[5].httpBody)
    XCTAssertEqual(http.requests[4].value(forHTTPHeaderField: "Idempotency-Key"), http.requests[5].value(forHTTPHeaderField: "Idempotency-Key"))
    XCTAssertEqual(try proof(http.requests[0]).header["jwk"] as? [String: String],
      try proof(http.requests[5]).header["jwk"] as? [String: String])
    let count = http.requests.count
    _ = try await restarted.perform("beginPairing")
    XCTAssertEqual(http.requests.count, count)
    XCTAssertEqual(keys.creates, 1)
    let saved = try savedRecord(store)
    XCTAssertEqual(saved["version"] as? Int, 1)
    XCTAssertEqual(saved["keyTag"] as? String, tag)
  }

  func testLegacyPendingBeginReplayAndRedeemPreserveKeyAndPKCE() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try controller(store, keys, http)
    http.loseResponse()
    do { _ = try await value.perform("beginPairing"); XCTFail("Lost begin") } catch {}
    let tag = try markLegacy(store)
    let restarted = try controller(store, keys, http)
    http.enqueue(beginResponse()); http.enqueue(pairResponse("PENDING"))
    _ = try await restarted.perform("syncSession")
    XCTAssertEqual(http.requests[0].httpBody, http.requests[1].httpBody)
    XCTAssertEqual(http.requests[0].value(forHTTPHeaderField: "Idempotency-Key"), http.requests[1].value(forHTTPHeaderField: "Idempotency-Key"))
    let count = http.requests.count
    _ = try await restarted.perform("beginPairing")
    XCTAssertEqual(http.requests.count, count)
    http.enqueue(pairResponse("APPROVED")); http.enqueue(tokens()); http.enqueue(driverResponse("READY"))
    _ = try await restarted.perform("syncSession")
    XCTAssertEqual(try proof(http.requests[0]).header["jwk"] as? [String: String],
      try proof(http.requests[4]).header["jwk"] as? [String: String])
    let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(http.requests[4].httpBody)) as? [String: Any])
    XCTAssertEqual(body["pkceVerifier"] as? String, fixtureToken(67))
    let saved = try savedRecord(store)
    XCTAssertEqual(saved["version"] as? Int, 1)
    XCTAssertEqual(saved["keyTag"] as? String, tag)
    XCTAssertEqual(keys.creates, 1)
  }

  func testLegacyRevokeReplayThenExplicitNewBindingRotatesKeyOnlyOnce() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.loseResponse()
    do { _ = try await value.perform("revokeSession"); XCTFail("Lost revoke") } catch {}
    let oldTag = try markLegacy(store)
    let restarted = try controller(store, keys, http)
    http.enqueue(revokeACK())
    let revoked = try await restarted.perform("syncSession")
    XCTAssertEqual(revoked["state"] as? String, "REVOKED")
    XCTAssertEqual(http.requests[4].httpBody, http.requests[5].httpBody)
    XCTAssertEqual(http.requests[4].value(forHTTPHeaderField: "Idempotency-Key"), http.requests[5].value(forHTTPHeaderField: "Idempotency-Key"))
    XCTAssertEqual(try proof(http.requests[0]).header["jwk"] as? [String: String],
      try proof(http.requests[5]).header["jwk"] as? [String: String])
    XCTAssertEqual(try savedRecord(store)["version"] as? Int, 1)
    XCTAssertEqual(keys.creates, 1)
    http.loseResponse()
    do { _ = try await restarted.perform("beginPairing"); XCTFail("Lost new begin") } catch {}
    let migrated = try savedRecord(store)
    let newTag = try XCTUnwrap(migrated["keyTag"] as? String)
    XCTAssertEqual(migrated["version"] as? Int, 2)
    XCTAssertNotEqual(newTag, oldTag)
    XCTAssertNotEqual(try proof(http.requests[0]).header["jwk"] as? [String: String],
      try proof(http.requests[6]).header["jwk"] as? [String: String])
    let afterCrash = try controller(store, keys, http)
    http.enqueue(beginResponse()); http.enqueue(pairResponse("PENDING"))
    _ = try await afterCrash.perform("syncSession")
    XCTAssertEqual(http.requests[6].httpBody, http.requests[7].httpBody)
    XCTAssertEqual(http.requests[6].value(forHTTPHeaderField: "Idempotency-Key"), http.requests[7].value(forHTTPHeaderField: "Idempotency-Key"))
    XCTAssertEqual(try savedRecord(store)["keyTag"] as? String, newTag)
    XCTAssertEqual(keys.creates, 2)
    http.enqueue(pairResponse("APPROVED")); http.enqueue(tokens()); http.enqueue(driverResponse("READY"))
    _ = try await afterCrash.perform("syncSession")
    http.enqueue(revokeACK())
    _ = try await afterCrash.perform("revokeSession")
    http.enqueue(beginResponse())
    _ = try await afterCrash.perform("beginPairing")
    XCTAssertEqual(try savedRecord(store)["version"] as? Int, 2)
    XCTAssertEqual(try savedRecord(store)["keyTag"] as? String, newTag)
    XCTAssertEqual(keys.creates, 2)
    XCTAssertEqual(try proof(http.requests[6]).header["jwk"] as? [String: String],
      try proof(XCTUnwrap(http.requests.last)).header["jwk"] as? [String: String])
  }

  func testLegacyMigrationInterruptedBeforeKeyCreationKeepsNewTagForRetry() async throws {
    let store = SessionMemoryStore(), keys = SessionFixtureKeys(), http = SessionFixtureHTTP()
    let value = try await approved(store, keys, http)
    http.enqueue(revokeACK())
    _ = try await value.perform("revokeSession")
    let oldTag = try markLegacy(store)
    let restarted = try controller(store, keys, http)
    keys.failNextCreation = true
    let count = http.requests.count
    do { _ = try await restarted.perform("beginPairing"); XCTFail("Interrupted key creation") } catch {
      XCTAssertEqual(error as? DriverSessionFailure, .key)
    }
    let committed = try savedRecord(store)
    let newTag = try XCTUnwrap(committed["keyTag"] as? String)
    XCTAssertEqual(committed["version"] as? Int, 2)
    XCTAssertNotEqual(newTag, oldTag)
    XCTAssertEqual(committed["state"] as? String, "UNPAIRED")
    XCTAssertEqual(http.requests.count, count)
    XCTAssertEqual(keys.creates, 1)
    let afterCrash = try controller(store, keys, http)
    http.enqueue(beginResponse())
    let pending = try await afterCrash.perform("beginPairing")
    XCTAssertEqual(pending["state"] as? String, "PENDING")
    XCTAssertEqual(try savedRecord(store)["keyTag"] as? String, newTag)
    XCTAssertEqual(keys.creates, 2)
    var unsupported = try savedRecord(store)
    unsupported["version"] = 3
    store.bytes = try DriverSessionCodec.json(unsupported)
    XCTAssertThrowsError(try controller(store, keys, http))
  }
}

#if DRIVER_SESSION_STANDALONE_TESTS
@main
struct DriverSessionHostTestMain {
  static func main() {
    let suite = DriverSessionHostTests.defaultTestSuite
    suite.run()
    guard let run = suite.testRun, run.executionCount == 31, run.totalFailureCount == 0 else { exit(1) }
  }
}
#endif
#endif
