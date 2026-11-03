#if VANRIDES_DRIVER_MVP && VANRIDES_GPS_SPIKE && DEBUG && targetEnvironment(simulator)
import Foundation
import UIKit
import XCTest
@testable import Runner

// Opt-in integration smoke. The harness supplies a short-lived synthetic credential
// in the app's private tmp directory, never in arguments, source or the test output.
final class DriverTrackingIntegrationTests: XCTestCase {
  @MainActor
  func testLoopbackCollectorReceivesACKAndStops() async throws {
    let fixtureURL = FileManager.default.temporaryDirectory.appendingPathComponent("GPS_SPIKE_TEST.json")
    guard FileManager.default.fileExists(atPath: fixtureURL.path) else {
      throw XCTSkip("GPS_SPIKE_TEST.json ausente; smoke integrado não executado.")
    }
    let data: Data
    do { data = try Data(contentsOf: fixtureURL) }
    catch { XCTFail("Não foi possível ler a fixture privada."); return }
    // Remove the provisioning fixture promptly; only Keychain may retain the token.
    do { try FileManager.default.removeItem(at: fixtureURL) }
    catch { XCTFail("Não foi possível remover a fixture privada."); return }
    guard data.count <= 4_096,
      let fixture = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
      Set(fixture.keys) == ["baseUrl", "token", "startIntentId"],
      let baseURL = fixture["baseUrl"] as? String, baseURL == "http://127.0.0.1:18765",
      let token = fixture["token"] as? String,
      let intent = fixture["startIntentId"] as? String, DriverTrackingContract.identifier(intent) else {
      XCTFail("Fixture inválida para o smoke sintético de loopback."); return
    }
    let coordinator = DriverTrackingCoordinator.shared
    defer { coordinator.stop() }
    let readyDeadline = Date().addingTimeInterval(10)
    while (UIApplication.shared.applicationState != .active || coordinator.snapshot()["permission"] as? String != "GRANTED")
      && Date() < readyDeadline {
      try await Task.sleep(nanoseconds: 200_000_000)
    }
    guard UIApplication.shared.applicationState == .active else {
      XCTFail("O host do teste precisa estar em primeiro plano."); return
    }
    guard coordinator.snapshot()["permission"] as? String == "GRANTED" else {
      XCTFail("O harness precisa conceder Always e localização precisa antes do smoke."); return
    }
    let configured: [String: Any] = try await withCheckedThrowingContinuation { continuation in
      coordinator.configure(DriverTrackingCredential(baseURL: baseURL, token: token)) {
        continuation.resume(with: $0)
      }
    }
    XCTAssertEqual(configured["credentialConfigured"] as? Bool, true)
    let started: [String: Any] = try await withCheckedThrowingContinuation { continuation in
      coordinator.start(intentID: intent) { continuation.resume(with: $0) }
    }
    guard started["state"] as? String == "TRACKING" else {
      XCTFail("O início nativo não entrou em TRACKING."); return
    }
    let ackDeadline = Date().addingTimeInterval(45)
    while Date() < ackDeadline {
      let status = coordinator.snapshot()
      if (status["accepted"] as? Int ?? 0) > 0 { break }
      guard status["state"] as? String == "TRACKING" else {
        XCTFail("A sessão encerrou antes do ACK sintético."); return
      }
      try await Task.sleep(nanoseconds: 250_000_000)
    }
    XCTAssertGreaterThan(coordinator.snapshot()["accepted"] as? Int ?? 0, 0,
      "O harness precisa injetar uma localização sintética pelo simctl.")
    coordinator.stop()
    let localStop = coordinator.snapshot()
    let requestedAt = try XCTUnwrap(localStop["stopRequestedAt"] as? NSNumber).int64Value
    let stoppedAt = try XCTUnwrap(localStop["stoppedAt"] as? NSNumber).int64Value
    let acceptedAtStop = localStop["accepted"] as? Int
    // The harness may drop a STOP request/ACK. Snapshot polling must not drive its retry.
    let stopDeadline = Date().addingTimeInterval(25)
    while Date() < stopDeadline {
      let status = coordinator.snapshot()
      guard status["state"] as? String == "STOPPED", status["queued"] as? Int == 0,
        status["gpsState"] as? String == "UNKNOWN" else {
        XCTFail("A espera do ACK de parada não pode reativar a coleta."); return
      }
      XCTAssertEqual((status["stopRequestedAt"] as? NSNumber)?.int64Value, requestedAt)
      XCTAssertEqual((status["stoppedAt"] as? NSNumber)?.int64Value, stoppedAt)
      XCTAssertEqual(status["accepted"] as? Int, acceptedAtStop)
      if status["stopPending"] as? Bool == false { break }
      try await Task.sleep(nanoseconds: 200_000_000)
    }
    let stopped = coordinator.snapshot()
    XCTAssertEqual(stopped["state"] as? String, "STOPPED")
    XCTAssertEqual(stopped["queued"] as? Int, 0)
    XCTAssertNotNil(stopped["stoppedAt"] as? NSNumber)
    XCTAssertEqual(stopped["diagnosticWriteFailed"] as? Bool, false)
    XCTAssertEqual(stopped["stopPending"] as? Bool, false, "O receptor precisa confirmar STOPPED.")
  }
}
#endif
