import Foundation
import XCTest

// Pure label guard, also exercised offline with synthetic labels.
enum MVPPairingCodeLabel {
  static let pattern = "^[BCDFGHJKMNPQRSTVWXYZ]{8}$"

  static func matches(_ label: String) -> Bool {
    let whole = NSRange(label.startIndex..<label.endIndex, in: label)
    guard let expression = try? NSRegularExpression(pattern: pattern),
      let match = expression.firstMatch(in: label, range: whole) else { return false }
    return match.range == whole
  }
}

// Separate runner, operating only the normal installed MVP. No credentials,
// simulated positions, permission resets or test commands enter the driver.
final class DriverMVPUITests: XCTestCase {
  private let driverID = "dev.matheusduarte.vanrides.driver"
  private struct Fixture: Decodable {
    let schemaVersion: Int
    let command: String
    let sourceDigest: String
  }
  private struct Failure: Error { let code: String }
  private var documents: URL {
    FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
  }

  @MainActor
  func testCommand() throws {
    continueAfterFailure = false
    let commandFile = documents.appendingPathComponent("command.json")
    let resultFile = documents.appendingPathComponent("result.json")
    let codeFile = documents.appendingPathComponent("pairing-code.json")
    try? FileManager.default.removeItem(at: resultFile)
    defer { try? FileManager.default.removeItem(at: commandFile) }
    var result: [String: Any] = ["schemaVersion": 1, "success": false, "pairingCodeExported": false]
    defer {
      if result["success"] as? Bool != true {
        try? FileManager.default.removeItem(at: codeFile)
      }
      if let data = try? JSONSerialization.data(withJSONObject: result, options: [.sortedKeys]) {
        try? data.write(to: resultFile, options: [.atomic, .completeFileProtection])
      }
    }
    do {
      // Every command invalidates the previous lab export, even invalid commands.
      try clearPairingCodeExport(codeFile)
      guard let data = try? Data(contentsOf: commandFile), data.count <= 1024,
        let fixture = try? JSONDecoder().decode(Fixture.self, from: data),
        fixture.schemaVersion == 1, fixture.sourceDigest == mvpUISourceDigest,
        ["inspect", "pair", "copy-code", "export-pairing-code", "open-whatsapp", "refresh", "confirm-start", "stop"].contains(fixture.command) else {
        throw Failure(code: "INVALID_PRIVATE_COMMAND")
      }
      result["command"] = fixture.command
      let app = XCUIApplication(bundleIdentifier: driverID)
      app.activate() // Never terminate or reinstall the app under test.
      guard app.wait(for: .runningForeground, timeout: 15) else {
        throw Failure(code: "APP_NOT_FOREGROUND")
      }
      switch fixture.command {
      case "pair":
        try tap(app, "Vincular este celular")
        try waitLabel(app, "Confirme pelo WhatsApp")
      case "copy-code":
        if button(app, "Mostrar código").exists { try tap(app, "Mostrar código") }
        // The app copies its transient code. The runner never reads it or the
        // clipboard, and never returns a code in the result or XCTest logs.
        try tap(app, "Copiar código")
      case "export-pairing-code":
        // Authorized lab bridge only. The app's normal five-minute window applies.
        result["snapshot"] = snapshot(app)
        try exportPairingCode(app, to: codeFile)
        result["pairingCodeExported"] = true
      case "open-whatsapp":
        try tap(app, "Abrir conversa no WhatsApp")
        guard app.wait(for: .runningBackground, timeout: 15) || app.state == .runningBackgroundSuspended else {
          throw Failure(code: "WHATSAPP_HANDOFF_UNCONFIRMED")
        }
      case "refresh":
        try tap(app, "Atualizar vínculo")
      case "confirm-start":
        // A real private deep link must already have reached the normal app.
        // This explicit tap is the only action here that can request a start.
        try tap(app, "Iniciar localização desta saída")
        try waitLabel(app, "Parar localização")
      case "stop":
        if button(app, "Parar localização").exists { try tap(app, "Parar localização") }
        try waitForStop(app)
      default: break
      }
      if fixture.command != "export-pairing-code" { result["snapshot"] = snapshot(app) }
      result["success"] = true
    } catch let failure as Failure {
      result["code"] = failure.code
      XCTFail("MVP_UI_" + failure.code)
    } catch {
      result["code"] = "COMMAND_FAILED"
      XCTFail("MVP_UI_COMMAND_FAILED")
    }
  }

  private func clearPairingCodeExport(_ file: URL) throws {
    do {
      if FileManager.default.fileExists(atPath: file.path) {
        try FileManager.default.removeItem(at: file)
      }
    } catch {
      throw Failure(code: "PAIRING_EXPORT_CLEANUP_FAILED")
    }
  }

  @MainActor
  private func exportPairingCode(_ app: XCUIApplication, to file: URL) throws {
    guard app.staticTexts["Confirme pelo WhatsApp"].exists else {
      throw Failure(code: "PAIRING_PENDING_REQUIRED")
    }
    let show = button(app, "Mostrar código")
    let copy = button(app, "Copiar código")
    guard show.exists || copy.exists else { throw Failure(code: "PAIRING_CONTROL_REQUIRED") }
    if show.exists { try tap(app, "Mostrar código") }
    guard copy.waitForExistence(timeout: 12), copy.isEnabled,
      app.staticTexts["Confirme pelo WhatsApp"].exists else {
      throw Failure(code: "PAIRING_PENDING_REQUIRED")
    }
    let candidates = app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", MVPPairingCodeLabel.pattern))
    guard candidates.count == 1 else { throw Failure(code: "PAIRING_CODE_NOT_UNIQUE") }
    let candidate = candidates.element(boundBy: 0)
    guard candidate.isHittable else { throw Failure(code: "PAIRING_CODE_NOT_VISIBLE") }
    // Read only this single matched label. Never log it or inspect the clipboard.
    let code = candidate.label
    guard MVPPairingCodeLabel.matches(code) else { throw Failure(code: "PAIRING_CODE_INVALID") }
    do {
      let data = try JSONSerialization.data(withJSONObject: ["code": code], options: [.sortedKeys])
      try data.write(to: file, options: [.atomic, .completeFileProtection])
      try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: file.path)
    } catch {
      try? FileManager.default.removeItem(at: file)
      throw Failure(code: "PAIRING_EXPORT_WRITE_FAILED")
    }
  }

  @MainActor
  private func button(_ app: XCUIApplication, _ label: String) -> XCUIElement {
    app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
  }

  @MainActor
  private func has(_ app: XCUIApplication, _ label: String) -> Bool {
    app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch.exists
  }

  @MainActor
  private func tap(_ app: XCUIApplication, _ label: String) throws {
    let target = button(app, label)
    guard target.waitForExistence(timeout: 12) else { throw Failure(code: "CONTROL_UNAVAILABLE") }
    for _ in 0..<5 where !target.isHittable { app.swipeUp() }
    guard target.isHittable, target.isEnabled else { throw Failure(code: "CONTROL_NOT_READY") }
    target.tap()
  }

  @MainActor
  private func waitLabel(_ app: XCUIApplication, _ label: String) throws {
    let target = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    guard target.waitForExistence(timeout: 45) else { throw Failure(code: "EXPECTED_STATE_UNCONFIRMED") }
  }

  @MainActor
  private func waitForStop(_ app: XCUIApplication) throws {
    let deadline = Date().addingTimeInterval(45)
    while Date() < deadline {
      let value = snapshot(app)
      if value["stopConfirmed"] == true { return }
      RunLoop.current.run(until: Date().addingTimeInterval(0.25))
    }
    throw Failure(code: "STOP_ACK_UNCONFIRMED")
  }

  @MainActor
  private func snapshot(_ app: XCUIApplication) -> [String: Bool] {
    let pending = has(app, "Parada solicitada. Aguardando confirmação.")
    let stopped = ["Localização interrompida", "Trajeto concluído", "Saída cancelada"].contains { has(app, $0) }
    return [
      "paired": has(app, "Aparelho vinculado"),
      "pairingPending": has(app, "Confirme pelo WhatsApp"),
      "startAvailable": button(app, "Iniciar localização desta saída").exists,
      "canStop": button(app, "Parar localização").exists,
      "stopPending": pending,
      "stopConfirmed": stopped && !pending && !button(app, "Parar localização").exists,
      "runCompleted": has(app, "Trajeto concluído"),
      "runCanceled": has(app, "Saída cancelada"),
      "gpsFresh": has(app, "Posição recente recebida"),
    ]
  }
}
