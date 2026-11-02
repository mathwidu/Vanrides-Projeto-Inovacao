import Foundation
import UIKit
import XCTest

private struct LabFixture: Decodable {
    let schemaVersion: Int
    let command: String
    let runID: String
    let sourceDigest: String
    let privacyApproved: Bool
    let baseURL: String?
    let credential: String?
    let intent: String?
    let backgroundSeconds: Int?
}

private struct LabFailure: Error {
    let code: String
    init(_ code: String) { self.code = code }
}

final class DriverGPSProofUITests: XCTestCase {
    private let driverID = "dev.matheusduarte.vanrides.driver"
    private let sentinel = "VRUILAB_SENTINEL_030_NON_SECRET_7F8C"
    private var result: [String: Any] = [:]

    private var documents: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    private func readFixture() throws -> LabFixture {
        let path = documents.appendingPathComponent("command.json")
        // A fixture is one-shot. Never echo a path, decoder error, or its contents.
        defer { try? FileManager.default.removeItem(at: path) }
        guard let data = try? Data(contentsOf: path),
              let fixture = try? JSONDecoder().decode(LabFixture.self, from: data),
              fixture.schemaVersion == 1,
              UUID(uuidString: fixture.runID) != nil,
              ["inspect", "sentinel-pass", "sentinel-fail-intent", "sentinel-fail-credential",
               "configure", "start", "stop", "background"].contains(fixture.command),
              fixture.sourceDigest == labSourceDigest else {
            throw LabFailure("FIXTURE_MISSING_OR_INVALID")
        }
        return fixture
    }

    private func saveResult() {
        guard let data = try? JSONSerialization.data(withJSONObject: result, options: [.prettyPrinted, .sortedKeys]) else { return }
        try? data.write(to: documents.appendingPathComponent("result.json"), options: [.atomic, .completeFileProtection])
    }

    // XCTest invokes this lifecycle hook even when an XCTest failure interrupts
    // a synchronous test using an Objective-C exception and bypasses Swift defer.
    // A runner/process crash remains outside that guarantee; pasteboard expiry is
    // the bounded fallback, and a missing/incomplete result never passes audit.
    override func tearDownWithError() throws {
        // Public synchronous XCTest lifecycle hook. Avoid the former async
        // MainActor.run wrapper; whether this reduces runner-idle latency must
        // be measured on device, not inferred from the choice of hook.
        result["teardownEntryMainThread"] = Thread.isMainThread
        let cleanupVerified: Bool
        if Thread.isMainThread {
            cleanupVerified = MainActor.assumeIsolated { completeSynchronousTeardown() }
        } else {
            cleanupVerified = DispatchQueue.main.sync {
                MainActor.assumeIsolated { completeSynchronousTeardown() }
            }
        }
        try super.tearDownWithError()
        if !cleanupVerified { XCTFail("UI_LAB_PASTEBOARD_TEARDOWN_CLEANUP_UNVERIFIED") }
    }

    @MainActor
    private func completeSynchronousTeardown() -> Bool {
        result["teardownHook"] = "SYNCHRONOUS_WITH_ERROR"
        let cleanup = cleanupPasteboardPreservingAUT(
            XCUIApplication(bundleIdentifier: driverID), resultKey: "teardownPasteboardCleanup"
        )
        let verified = verifiedCleanup(cleanup)
        result["pasteboardEmptyAtTeardown"] = verified
        if !verified {
            result["success"] = false
            result["code"] = "PASTEBOARD_TEARDOWN_CLEANUP_UNVERIFIED"
        }
        try? FileManager.default.removeItem(at: documents.appendingPathComponent("command.json"))
        result["fixtureRemoved"] = !FileManager.default.fileExists(atPath: documents.appendingPathComponent("command.json").path)
        result["teardownCompleted"] = true
        if let runID = result["runID"] as? String, let command = result["command"] as? String {
            let marker = "UI_LAB_TEARDOWN_" + runID + "_SOURCE_" + labSourceDigest + "_COMMAND_" + command
            XCTContext.runActivity(named: marker) { _ in }
            print(marker)
        }
        saveResult()
        return verified
    }

    @MainActor
    func testCommand() throws {
        continueAfterFailure = false
        try? FileManager.default.removeItem(at: documents.appendingPathComponent("result.json"))
        result = ["schemaVersion": 1, "sourceDigest": labSourceDigest,
                  "success": false, "code": "COMMAND_INCOMPLETE"]
        let app = XCUIApplication(bundleIdentifier: driverID)
        // No launchEnvironment, launchArguments, location simulation, or permission reset.
        defer { saveResult() }
        do {
            let fixture = try readFixture()
            result["command"] = fixture.command
            result["runID"] = fixture.runID
            // Only these validated, non-secret identity fields enter logs. They
            // bind the result bundle and terminal log to this one-shot fixture.
            let identity = "UI_LAB_RUN_" + fixture.runID + "_SOURCE_" + labSourceDigest + "_COMMAND_" + fixture.command
            XCTContext.runActivity(named: identity) { _ in }
            print(identity)
            app.activate() // Does not deliberately terminate an existing driver process.
            guard app.wait(for: .runningForeground, timeout: 15) else {
                throw LabFailure("APP_NOT_FOREGROUND")
            }
            switch fixture.command {
            case "inspect":
                result["snapshot"] = try snapshot(app)
            case "sentinel-pass", "sentinel-fail-intent", "sentinel-fail-credential":
                try sentinelAudit(app, mode: fixture.command)
            case "configure":
                guard fixture.privacyApproved else { throw LabFailure("PRIVACY_AUDIT_REQUIRED") }
                try requireIdleTitle(app)
                guard let baseURL = fixture.baseURL, let credential = fixture.credential else {
                    throw LabFailure("CONFIGURE_FIXTURE_REQUIRED")
                }
                try openCredentialEditor(app)
                try replaceThroughPaste(app, field: field(app, label: "Endereço do receptor de teste", secure: false), text: baseURL)
                try replaceThroughPaste(app, field: field(app, label: "Credencial de teste", secure: true), text: credential)
                try tap(app, "Configurar aparelho")
                try waitLabel(app, "Código de início do teste", timeout: 30)
                result["snapshot"] = try snapshot(app)
            case "start":
                guard fixture.privacyApproved else { throw LabFailure("PRIVACY_AUDIT_REQUIRED") }
                try requireIdle(snapshot(app))
                guard let intent = fixture.intent else { throw LabFailure("START_FIXTURE_REQUIRED") }
                try replaceThroughPaste(app, field: field(app, label: "Código de início do teste", secure: false), text: intent)
                try tap(app, "Iniciar teste")
                try waitLabel(app, "Localização em andamento", timeout: 45)
                result["snapshot"] = try snapshot(app)
                result["postRunnerContinuityVerified"] = false
            case "stop":
                let initial = try snapshot(app)
                if initial["state"] as? String != "STOPPED" {
                    try tap(app, "Parar localização")
                    try waitLabel(app, "O coletor confirmou o encerramento.", timeout: 45)
                }
                let stopped = try snapshot(app)
                guard stopped["state"] as? String == "STOPPED", stopped["stopPending"] as? Bool == false else {
                    throw LabFailure("STOP_NOT_CONFIRMED")
                }
                result["snapshot"] = stopped
            case "background":
                let seconds = fixture.backgroundSeconds ?? 30
                guard (1...180).contains(seconds) else { throw LabFailure("INVALID_BACKGROUND_DURATION") }
                XCUIDevice.shared.press(.home)
                guard app.wait(for: .runningBackground, timeout: 10) || app.state == .runningBackgroundSuspended else {
                    throw LabFailure("BACKGROUND_NOT_CONFIRMED")
                }
                // Explicit experiment; does not simulate a screen lock or a battery test.
                try awaitBackground(seconds: seconds)
                app.activate()
                result["snapshot"] = try snapshot(app)
                result["screenLockVerified"] = false
                result["postRunnerContinuityVerified"] = false
            default:
                throw LabFailure("UNKNOWN_COMMAND")
            }
            result["success"] = true
            result.removeValue(forKey: "code")
        } catch let error as LabFailure {
            result["code"] = error.code
            XCTFail("UI_LAB_" + error.code)
        } catch {
            result["code"] = "UNEXPECTED_ERROR"
            XCTFail("UI_LAB_UNEXPECTED_ERROR")
        }
    }

    @MainActor
    private func awaitBackground(seconds: Int) throws {
        let deadline = Date().addingTimeInterval(TimeInterval(seconds))
        while Date() < deadline { RunLoop.current.run(until: min(deadline, Date().addingTimeInterval(0.25))) }
    }

    @MainActor
    private func requireIdle(_ snapshot: [String: Any]) throws {
        guard let state = snapshot["state"] as? String,
              ["READY", "STOPPED", "ERROR", "UNCONFIGURED"].contains(state),
              snapshot["starting"] as? Bool == false,
              snapshot["stopPending"] as? Bool == false else { throw LabFailure("IDLE_APP_REQUIRED") }
    }

    @MainActor
    private func requireIdleTitle(_ app: XCUIApplication) throws {
        try requireIdle(snapshot(app))
    }

    @MainActor
    private func element(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        let button = app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
        if button.exists { return button }
        return app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    @MainActor
    private func reveal(_ app: XCUIApplication, _ label: String) throws -> XCUIElement {
        for _ in 0..<7 {
            let candidate = element(app, label)
            if candidate.exists && candidate.isHittable { return candidate }
            app.swipeUp()
        }
        for _ in 0..<7 {
            let candidate = element(app, label)
            if candidate.exists && candidate.isHittable { return candidate }
            app.swipeDown()
        }
        throw LabFailure("UI_CONTROL_UNAVAILABLE")
    }

    @MainActor
    private func tap(_ app: XCUIApplication, _ label: String) throws {
        let control = try reveal(app, label)
        guard control.isEnabled else { throw LabFailure("UI_CONTROL_DISABLED") }
        control.tap()
    }

    @MainActor
    private func waitLabel(_ app: XCUIApplication, _ label: String, timeout: TimeInterval) throws {
        guard element(app, label).waitForExistence(timeout: timeout) else { throw LabFailure("EXPECTED_UI_STATE_TIMEOUT") }
    }

    @MainActor
    private func field(_ app: XCUIApplication, label: String, secure: Bool, phase: String = "INITIAL") throws -> XCUIElement {
        let anchor = try reveal(app, label)
        if secure {
            guard label == "Credencial de teste", anchor.exists else { throw LabFailure("KNOWN_CREDENTIAL_LABEL_REQUIRED") }
            let predicate = NSPredicate(format: "label == %@", label)
            let exactSecure = app.secureTextFields.matching(predicate)
            let exactText = app.textFields.matching(predicate)
            let secureCount = app.secureTextFields.count
            var diagnostic: [String: Any] = [
                "phase": phase, "knownLabelPresent": true, "secureFieldCount": secureCount,
                "textFieldCount": app.textFields.count,
                "exactKnownSecureCount": exactSecure.count, "exactKnownTextCount": exactText.count
            ]
            result["credentialFieldResolution"] = diagnostic
            let candidate: XCUIElement
            if exactSecure.count == 1 {
                candidate = exactSecure.firstMatch
                diagnostic["method"] = "EXACT_KNOWN_SECURE_LABEL"
                diagnostic["selectedType"] = "SECURE_TEXT_FIELD"
            } else if exactSecure.count == 0 && secureCount == 1 {
                candidate = app.secureTextFields.element(boundBy: 0)
                diagnostic["method"] = "UNIQUE_SECURE_WITH_KNOWN_LABEL"
                diagnostic["selectedType"] = "SECURE_TEXT_FIELD"
            } else if secureCount == 0 && exactText.count == 1 {
                // A plain AX type is acceptable only for this exact known label.
                // The existing dummy mask proof remains mandatory before secrets.
                candidate = exactText.firstMatch
                diagnostic["method"] = "EXACT_KNOWN_TEXT_LABEL"
                diagnostic["selectedType"] = "TEXT_FIELD"
            } else {
                recordCredentialResolution(diagnostic)
                throw LabFailure("CREDENTIAL_FIELD_UNAVAILABLE_OR_AMBIGUOUS")
            }
            diagnostic["selectedEnabled"] = candidate.exists && candidate.isEnabled
            recordCredentialResolution(diagnostic)
            guard candidate.exists && candidate.isEnabled else { throw LabFailure("EXPECTED_TEXT_FIELD_UNAVAILABLE") }
            return candidate
        }
        let candidate = app.textFields.matching(NSPredicate(format: "label == %@", label)).firstMatch
        guard candidate.exists && candidate.isEnabled else { throw LabFailure("EXPECTED_TEXT_FIELD_UNAVAILABLE") }
        return candidate
    }

    @MainActor
    private func recordCredentialResolution(_ diagnostic: [String: Any]) {
        var history = result["credentialFieldResolutions"] as? [[String: Any]] ?? []
        history.append(diagnostic)
        result["credentialFieldResolutions"] = history
        result["credentialFieldResolution"] = diagnostic
    }

    @MainActor
    private func resolveCurrentField(_ app: XCUIApplication, original: XCUIElement,
                                     credential: Bool, phase: String) throws -> XCUIElement {
        let current: XCUIElement
        if credential {
            current = try field(app, label: "Credencial de teste", secure: true, phase: phase)
        } else {
            current = original
        }
        guard current.exists else { throw LabFailure("FIELD_REFERENCE_UNAVAILABLE") }
        return current
    }

    @MainActor
    private func clearField(_ app: XCUIApplication, _ original: XCUIElement, credential: Bool = false) throws -> [String: Any] {
        // Only emptiness is derived transiently from a real field value. Never
        // retain/log the value or its length. Dummy equality checks are separate.
        let current = try resolveCurrentField(app, original: original, credential: credential, phase: "BEFORE_FOCUS")
        current.tap()
        var observation: [String: Any] = [
            "method": "FOCUS_LONG_PRESS_SELECT_ALL_DELETE",
            "selectAllTapped": false, "deleteTapped": false, "emptyVerified": false
        ]
        if try fieldIsEmpty(app, original: current, credential: credential, phase: "AFTER_FOCUS") {
            observation["emptyVerified"] = true
            return observation
        }
        // Flutter's iOS long-press on an already focused field collapses the
        // selection, making the real Select All action available for nonempty text.
        let focused = try resolveCurrentField(app, original: current, credential: credential, phase: "BEFORE_SELECT_ALL")
        focused.press(forDuration: 1.0)
        var selectedAll = false
        let allLabels = ["Selecionar tudo", "Select all", "Select All", "Selecionar Tudo"]
        let selectAllPredicate = NSPredicate(format: "label IN %@", allLabels as NSArray)
        let controls = [app.buttons.matching(selectAllPredicate).firstMatch,
                        app.menuItems.matching(selectAllPredicate).firstMatch,
                        app.staticTexts.matching(selectAllPredicate).firstMatch]
        for control in controls where control.exists && control.isHittable && control.isEnabled {
            control.tap()
            selectedAll = true
            break
        }
        if !selectedAll {
            result["lastClearMenuProbe"] = standardMenuProbe(app)
            throw LabFailure("SELECT_ALL_CONTROL_UNAVAILABLE")
        }
        observation["selectAllTapped"] = true
        let labels = ["delete", "Delete", "Apagar", "Excluir", "Backspace", "backspace"]
        let key = app.keyboards.keys.matching(NSPredicate(
            format: "identifier IN %@ OR label IN %@", labels as NSArray, labels as NSArray
        )).firstMatch
        guard key.waitForExistence(timeout: 1), key.isHittable, key.isEnabled else {
            throw LabFailure("DELETE_CONTROL_UNAVAILABLE")
        }
        key.tap()
        observation["deleteTapped"] = true
        guard try fieldIsEmpty(app, original: focused, credential: credential, phase: "AFTER_DELETE") else {
            throw LabFailure("FIELD_CLEAR_NOT_EMPTY")
        }
        observation["emptyVerified"] = true
        return observation
    }

    @MainActor
    private func fieldIsEmpty(_ app: XCUIApplication, original: XCUIElement,
                              credential: Bool, phase: String) throws -> Bool {
        let current = try resolveCurrentField(app, original: original, credential: credential, phase: phase)
        guard current.exists else { throw LabFailure("FIELD_REFERENCE_UNAVAILABLE") }
        guard let value = current.value as? String else { throw LabFailure("FIELD_EMPTY_STATE_UNREADABLE") }
        return value.isEmpty
    }

    @MainActor
    private func standardMenuProbe(_ app: XCUIApplication) -> [String: Any] {
        let names: [(String, [String])] = [
            ("selectAll", ["Selecionar tudo", "Selecionar Tudo", "Select all", "Select All"]),
            ("select", ["Selecionar", "Select"]), ("cut", ["Cortar", "Recortar", "Cut"]),
            ("copy", ["Copiar", "Copy"]), ("paste", ["Colar", "Paste"]), ("more", ["Mais", "More"])
        ]
        var probe: [String: Any] = [:]
        for (name, labels) in names {
            let predicate = NSPredicate(format: "label IN %@", labels as NSArray)
            let controls = [app.buttons.matching(predicate).firstMatch,
                            app.menuItems.matching(predicate).firstMatch,
                            app.staticTexts.matching(predicate).firstMatch]
            var exists = false
            var hittable = false
            var enabled = false
            for control in controls where control.exists {
                exists = true
                hittable = hittable || control.isHittable
                enabled = enabled || control.isEnabled
            }
            probe[name] = ["exists": exists, "hittable": hittable, "enabled": enabled]
        }
        return probe
    }

    @MainActor
    private func replaceThroughPaste(_ app: XCUIApplication, field: XCUIElement, text: String) throws {
        // Select All and Delete use positively identified UI controls.
        // The marker and real secrets use exactly the same paste path.
        try requireIdle(snapshot(app))
        guard field.exists else { throw LabFailure("FIELD_REFERENCE_UNAVAILABLE") }
        let credential = field.elementType == .secureTextField || field.label == "Credencial de teste"
        var event: [String: Any] = [
            "method": "EDIT_MENU_PASTE",
            "pasteboardWriteContext": "RUNNER_FOREGROUND",
            "pasteboardWriteAttempted": false,
            "runnerForegroundConfirmed": false,
            "runnerUIApplicationActive": false,
            "autForegroundRestored": false,
            "keyboardPasteSent": false,
            "longPressSent": false,
            "menuPasteControlFound": false,
            "menuPasteTapped": false,
            "consentAlertSeen": false,
            "consentNameMatched": false,
            "consentAccepted": false
        ]
        let eventIndex = (result["pasteEvents"] as? [[String: Any]])?.count ?? 0
        recordPasteEvent(event, at: eventIndex)
        var cleanupAttempted = false
        defer {
            if !cleanupAttempted {
                event["cleanup"] = cleanupPasteboardPreservingAUT(app, resultKey: "lastPasteboardCleanup")
            }
            recordPasteEvent(event, at: eventIndex)
        }
        try stagePasteboardInOwnRunner(app, text: text, event: &event, eventIndex: eventIndex)
        guard event["hasStringsAfterSet"] as? Bool == true else { throw LabFailure("PASTEBOARD_EMPTY_AFTER_SET") }
        event["clear"] = try clearField(app, field, credential: credential)
        recordPasteEvent(event, at: eventIndex)
        let emptied = try resolveCurrentField(app, original: field, credential: credential, phase: "AFTER_CLEAR_BEFORE_PASTE")
        try tapActualPasteMenu(app, field: emptied, event: &event, eventIndex: eventIndex)
        try allowExpectedPasteConsent(app, event: &event, eventIndex: eventIndex)
        let cleanup = cleanupPasteboardPreservingAUT(app, resultKey: "lastPasteboardCleanup")
        cleanupAttempted = true
        event["cleanup"] = cleanup
        recordPasteEvent(event, at: eventIndex)
        guard verifiedCleanup(cleanup) else { throw LabFailure("PASTEBOARD_CLEANUP_UNVERIFIED") }
        // Real values are read only transiently to verify empty before paste;
        // no real content or length is persisted, compared to a secret, or logged.
    }

    @MainActor
    private func tapActualPasteMenu(_ app: XCUIApplication, field: XCUIElement,
                                     event: inout [String: Any], eventIndex: Int) throws {
        field.press(forDuration: 1.0)
        event["longPressSent"] = true
        recordPasteEvent(event, at: eventIndex)
        for label in ["Colar", "Paste"] {
            let controls: [(XCUIElement, String)] = [
                (app.menuItems.matching(NSPredicate(format: "label == %@", label)).firstMatch, "MENU_ITEM"),
                (app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch, "BUTTON"),
                (app.staticTexts.matching(NSPredicate(format: "label == %@", label)).firstMatch, "STATIC_TEXT")
            ]
            for (control, kind) in controls {
                guard control.waitForExistence(timeout: 0.5), control.isHittable else { continue }
                event["menuPasteControlFound"] = true
                event["menuControlKind"] = kind
                recordPasteEvent(event, at: eventIndex)
                control.tap()
                event["menuPasteTapped"] = true
                recordPasteEvent(event, at: eventIndex)
                return
            }
        }
        throw LabFailure("PASTE_MENU_CONTROL_UNAVAILABLE")
    }

    @MainActor
    private func stagePasteboardInOwnRunner(_ app: XCUIApplication, text: String,
                                            event: inout [String: Any], eventIndex: Int) throws {
        guard let runnerID = Bundle.main.bundleIdentifier,
              runnerID.hasSuffix(".xctrunner"), runnerID != driverID else {
            throw LabFailure("OWN_TEST_RUNNER_ID_REQUIRED")
        }
        let runner = XCUIApplication(bundleIdentifier: runnerID)
        var restoreNeeded = false
        defer {
            if restoreNeeded && processIsRunning(app) {
                app.activate()
                event["autForegroundRestored"] = app.wait(for: .runningForeground, timeout: 10)
                recordPasteEvent(event, at: eventIndex)
            }
        }
        // Use activate(), with no explicit launch()/terminate() request. AUT
        // state observations are asynchronous; a concurrent crash can still
        // make a later activate() relaunch it. Identity is verified externally.
        runner.activate()
        restoreNeeded = true
        event["runnerForegroundConfirmed"] = runner.wait(for: .runningForeground, timeout: 10)
        let deadline = Date().addingTimeInterval(2)
        while UIApplication.shared.applicationState != .active && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
        event["runnerUIApplicationActive"] = UIApplication.shared.applicationState == .active
        recordPasteEvent(event, at: eventIndex)
        guard event["runnerForegroundConfirmed"] as? Bool == true,
              event["runnerUIApplicationActive"] as? Bool == true else {
            throw LabFailure("TEST_RUNNER_FOREGROUND_REQUIRED")
        }
        event["pasteboardWriteAttempted"] = true
        UIPasteboard.general.setObjects([text as NSString], localOnly: true,
                                       expirationDate: Date().addingTimeInterval(30))
        event["hasStringsAfterSet"] = UIPasteboard.general.hasStrings
        event["changeCountAfterSet"] = UIPasteboard.general.changeCount
        recordPasteEvent(event, at: eventIndex)
        guard processIsRunning(app) else { throw LabFailure("AUT_PROCESS_NOT_RUNNING") }
        app.activate()
        let restored = app.wait(for: .runningForeground, timeout: 10)
        event["autForegroundRestored"] = restored
        recordPasteEvent(event, at: eventIndex)
        restoreNeeded = !restored
        guard restored else { throw LabFailure("AUT_FOREGROUND_RESTORE_REQUIRED") }
    }

    private func verifiedCleanup(_ cleanup: [String: Any]) -> Bool {
        ["autWasRunning", "runnerForegroundConfirmed", "runnerUIApplicationActive",
         "readabilityConfirmed", "probeChangeCountAdvanced", "clearChangeCountAdvanced",
         "emptyAfterClear", "runnerActiveDuringClear", "cleared", "autForegroundRestored"]
            .allSatisfy { cleanup[$0] as? Bool == true }
    }

    @MainActor
    private func processIsRunning(_ app: XCUIApplication) -> Bool {
        switch app.state {
        case .runningForeground, .runningBackground, .runningBackgroundSuspended: return true
        default: return false
        }
    }

    @MainActor
    private func cleanupPasteboardPreservingAUT(_ app: XCUIApplication, resultKey: String) -> [String: Any] {
        var cleanup: [String: Any] = [
            "autWasRunning": processIsRunning(app),
            "runnerForegroundConfirmed": false, "runnerUIApplicationActive": false,
            "readabilityConfirmed": false, "probeChangeCountAdvanced": false,
            "clearChangeCountAdvanced": false, "emptyAfterClear": false,
            "runnerActiveDuringClear": false, "cleared": false,
            "autForegroundRestored": false, "processIdentityVerified": false
        ]
        result[resultKey] = cleanup
        guard let runnerID = Bundle.main.bundleIdentifier,
              runnerID.hasSuffix(".xctrunner"), runnerID != driverID else { return cleanup }
        let runner = XCUIApplication(bundleIdentifier: runnerID)
        runner.activate()
        cleanup["runnerForegroundConfirmed"] = runner.wait(for: .runningForeground, timeout: 10)
        let activeDeadline = Date().addingTimeInterval(2)
        while UIApplication.shared.applicationState != .active && Date() < activeDeadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
        cleanup["runnerUIApplicationActive"] = UIApplication.shared.applicationState == .active
        result[resultKey] = cleanup
        if cleanup["runnerForegroundConfirmed"] as? Bool == true,
           cleanup["runnerUIApplicationActive"] as? Bool == true {
            let pasteboard = UIPasteboard.general
            let before = pasteboard.changeCount
            // An explicit non-secret local probe distinguishes working access
            // from Code 11 returning a false empty/zero. It is never pasted.
            let probe = "VRUILAB_CLEANUP_READABILITY_PROBE_" + UUID().uuidString
            pasteboard.setObjects([probe as NSString], localOnly: true,
                                  expirationDate: Date().addingTimeInterval(30))
            let probeDeadline = Date().addingTimeInterval(0.5)
            while (!pasteboard.hasStrings || pasteboard.changeCount <= before) && Date() < probeDeadline {
                RunLoop.current.run(until: Date().addingTimeInterval(0.05))
            }
            let probeCount = pasteboard.changeCount
            let probeAdvanced = probeCount > before
            let readable = pasteboard.hasStrings && probeAdvanced
            cleanup["probeChangeCountAdvanced"] = probeAdvanced
            cleanup["readabilityConfirmed"] = readable
            pasteboard.items = []
            let clearDeadline = Date().addingTimeInterval(0.5)
            while readable && (pasteboard.numberOfItems != 0 || pasteboard.hasStrings
                || pasteboard.changeCount <= probeCount) && Date() < clearDeadline {
                RunLoop.current.run(until: Date().addingTimeInterval(0.05))
            }
            let clearAdvanced = pasteboard.changeCount > probeCount
            let empty = readable && pasteboard.numberOfItems == 0 && !pasteboard.hasStrings
            let active = UIApplication.shared.applicationState == .active
            cleanup["clearChangeCountAdvanced"] = clearAdvanced
            cleanup["emptyAfterClear"] = empty
            cleanup["runnerActiveDuringClear"] = active
            cleanup["cleared"] = readable && probeAdvanced && clearAdvanced && empty && active
        }
        result[resultKey] = cleanup
        // No explicit launch()/terminate() during cleanup. state is asynchronous:
        // an AUT crash between this check and activate() can cause a relaunch.
        // These flags prove UI restoration only, never process identity/continuity.
        if cleanup["autWasRunning"] as? Bool == true && processIsRunning(app) {
            app.activate()
            cleanup["autForegroundRestored"] = app.wait(for: .runningForeground, timeout: 10)
        }
        result[resultKey] = cleanup
        return cleanup
    }

    @MainActor
    private func recordPasteEvent(_ event: [String: Any], at index: Int) {
        var events = result["pasteEvents"] as? [[String: Any]] ?? []
        if index < events.count { events[index] = event } else { events.append(event) }
        result["pasteEvents"] = events
    }

    @MainActor
    private func allowExpectedPasteConsent(_ app: XCUIApplication, event: inout [String: Any], eventIndex: Int) throws {
        var expectedNames = ["VanRides Motorista"]
        if Bundle.main.bundleIdentifier?.hasSuffix(".xctrunner") == true {
            for key in ["CFBundleDisplayName", "CFBundleName"] {
                if let name = Bundle.main.object(forInfoDictionaryKey: key) as? String,
                   name.hasPrefix("DriverGPSProof"), !expectedNames.contains(name) {
                    expectedNames.append(name)
                }
            }
        }
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for alert in [app.alerts.firstMatch, springboard.alerts.firstMatch] {
            guard alert.waitForExistence(timeout: 1.0) else { continue }
            event["consentAlertSeen"] = true
            let named = expectedNames.contains { name in
                alert.label.contains(name)
                    || alert.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", name)).firstMatch.exists
            }
            event["consentNameMatched"] = named
            recordPasteEvent(event, at: eventIndex)
            guard named else { throw LabFailure("UNEXPECTED_PASTE_CONSENT_ALERT") }
            let buttons = ["Permitir Colar", "Permitir colar", "Allow Paste", "Allow paste"]
            let allow = alert.buttons.matching(NSPredicate(format: "label IN %@", buttons as NSArray)).firstMatch
            guard allow.exists && allow.isHittable else { throw LabFailure("PASTE_CONSENT_REQUIRES_REVIEW") }
            allow.tap()
            event["consentAccepted"] = true
            recordPasteEvent(event, at: eventIndex)
            return
        }
    }

    @MainActor
    private func openCredentialEditor(_ app: XCUIApplication) throws {
        if element(app, "Credencial de teste").exists {
            _ = try field(app, label: "Credencial de teste", secure: true)
            return
        }
        _ = try snapshot(app)
        if !element(app, "Usar nova credencial de teste").exists
            && !element(app, "Copiar diagnóstico sanitizado").exists {
            try tap(app, "Detalhes do teste")
        }
        try tap(app, "Usar nova credencial de teste")
    }

    @MainActor
    private func sentinelAudit(_ app: XCUIApplication, mode: String) throws {
        // UI preflight only: an idle heading and no starting/pending-stop marker.
        // The principal must independently confirm native/receiver NO_SESSION
        // before running a sentinel-only idle relaunch.
        try requireIdle(snapshot(app))
        // No Cancel exists in the current editor. A sentinel-only idle relaunch
        // resets that transient widget state without configuring or starting GPS.
        app.terminate()
        app.activate()
        try requireIdle(snapshot(app))
        let intent = try field(app, label: "Código de início do teste", secure: false)
        _ = observePublicDummy(intent, key: "intentBeforeFirst", expected: "")
        try replaceThroughPaste(app, field: intent, text: sentinel)
        // Values are inspected only for a public sentinel; never persisted or logged.
        guard observePublicDummy(intent, key: "intentFirst", expected: sentinel) else {
            throw LabFailure("SENTINEL_INTENT_NOT_REPLACED")
        }
        result["intentSentinelEntered"] = true
        XCTContext.runActivity(named: "UI_LAB_INTENT_SENTINEL_INSERTED") { _ in }
        if mode == "sentinel-pass" {
            // The URL field is prefilled in real configure. Prove that this
            // same paste path replaces a non-empty dummy rather than appending
            // or leaving the previous value unchanged.
            let replacementSentinel = sentinel + "_REPLACED"
            try replaceThroughPaste(app, field: intent, text: replacementSentinel)
            guard observePublicDummy(intent, key: "intentReplacement", expected: replacementSentinel, previous: sentinel) else {
                throw LabFailure("SENTINEL_NONEMPTY_REPLACEMENT_FAILED")
            }
            result["intentReplacementVerified"] = true
            XCTContext.runActivity(named: "UI_LAB_NONEMPTY_REPLACEMENT_VERIFIED") { _ in }
        }
        if mode == "sentinel-fail-intent" {
            result["intentionalFailure"] = true
            result["code"] = "INTENTIONAL_INTENT_FAILURE"
            // Keep continueAfterFailure=false. The XCTest lifecycle teardown,
            // rather than successful control flow, must complete the cleanup.
            XCTFail("UI_LAB_INTENTIONAL_INTENT_FAILURE")
            throw LabFailure("EXPECTED_ABORT_DID_NOT_OCCUR")
        }
        _ = try clearField(app, intent)
        _ = observePublicDummy(intent, key: "intentAfterClear", expected: "")
        try openCredentialEditor(app)
        let credential = try field(app, label: "Credencial de teste", secure: true)
        try replaceThroughPaste(app, field: credential, text: sentinel)
        let populatedCredential = try field(app, label: "Credencial de teste", secure: true, phase: "AFTER_PASTE_MASK_PROOF")
        guard populatedCredential.exists, let masked = populatedCredential.value as? String,
              masked.count == sentinel.count,
              masked.allSatisfy({ "•●∙*".contains($0) }) else {
            _ = try? clearField(app, populatedCredential, credential: true)
            throw LabFailure("SENTINEL_CREDENTIAL_NOT_PROTECTED")
        }
        result["credentialSentinelEntered"] = true
        result["credentialFieldProtected"] = true
        XCTContext.runActivity(named: "UI_LAB_CREDENTIAL_SENTINEL_INSERTED") { _ in }
        if mode == "sentinel-fail-credential" {
            result["intentionalFailure"] = true
            result["code"] = "INTENTIONAL_CREDENTIAL_FAILURE"
            XCTFail("UI_LAB_INTENTIONAL_CREDENTIAL_FAILURE")
            throw LabFailure("EXPECTED_ABORT_DID_NOT_OCCUR")
        }
        _ = try clearField(app, populatedCredential, credential: true)
        let emptiedCredential = try field(app, label: "Credencial de teste", secure: true, phase: "AFTER_FINAL_CLEAR")
        _ = observePublicDummy(emptiedCredential, key: "credentialAfterClear", expected: "")
        result["intentionalFailure"] = false
        // Both commands are explicitly sentinel-only and required idle above.
        // Reset the transient editor so a later command sees the normal UI.
        app.terminate()
        app.activate()
        result["editorLeftOpen"] = false
        result["idleRelaunchPerformed"] = true
    }

    @MainActor
    private func observePublicDummy(_ field: XCUIElement, key: String, expected: String, previous: String? = nil) -> Bool {
        // Called only by sentinelAudit after an idle relaunch and public dummy
        // entry. No real configure/start fixture uses this value read.
        let observed = field.exists ? field.value as? String : nil
        var observation: [String: Any] = [
            "valueReadable": observed != nil,
            "expectedLength": expected.count,
            "equalsExpected": observed == expected
        ]
        if let observed = observed { observation["length"] = observed.count }
        if let previous = previous {
            observation["previousLength"] = previous.count
            observation["equalsPrevious"] = observed == previous
            observation["equalsConcatenation"] = observed == previous + expected
        }
        var observations = result["dummyFieldObservations"] as? [String: Any] ?? [:]
        observations[key] = observation
        result["dummyFieldObservations"] = observations
        return observed == expected
    }

    @MainActor
    private func snapshot(_ app: XCUIApplication) throws -> [String: Any] {
        // This is an observation of fixed UI labels, not a native diagnostic
        // snapshot. The principal verifies native sidecar/receiver separately.
        let titles: [(String, String)] = [
            ("Encerrando localização", "STOPPING"),
            ("Iniciando localização", "STARTING"),
            ("Localização em andamento", "TRACKING"),
            ("Localização encerrada", "STOPPED"),
            ("Aparelho configurado", "READY"),
            ("Teste precisa de atenção", "ERROR"),
            ("Aplicativo instalado", "UNCONFIGURED")
        ]
        var state: String?
        // A previous command can leave the scroll view at the Details section.
        // Bring the known heading into view without reading any field value.
        for attempt in 0..<7 {
            for (label, candidate) in titles where visibleLabel(app, label) {
                state = candidate
                break
            }
            if state != nil { break }
            if attempt < 6 { app.swipeDown() }
        }
        guard let state = state else {
            result["snapshotUIReadFailure"] = ["knownHeaderVisible": false]
            throw LabFailure("VISIBLE_UI_HEADER_UNAVAILABLE")
        }
        let starting = state == "STARTING" || element(app, "Iniciando localização").exists
        let stopPending = state == "STOPPING"
            || element(app, "Encerrando localização").exists
            || element(app, "Encerramento solicitado").exists
            || element(app, "Aguardando confirmação do encerramento.").exists
        var permission = "UNKNOWN"
        for (label, candidate) in [
            ("Localização autorizada", "GRANTED"),
            ("Ative a localização precisa nos ajustes", "APPROXIMATE"),
            ("Localização não autorizada", "DENIED"),
            ("Permissão ainda não verificada", "UNKNOWN")
        ] where visibleLabel(app, label) {
            permission = candidate
            break
        }
        var gpsState = "UNKNOWN"
        for (label, candidate) in [
            ("Posição recente recebida", "FRESH"),
            ("Sem posição recente no receptor", "STALE"),
            ("Aguardando posição válida", "UNKNOWN")
        ] where visibleLabel(app, label) {
            gpsState = candidate
            break
        }
        // The UI does not show credential state directly. ERROR is ambiguous;
        // expose null there, and explicitly label the other cases as inference.
        let credentialConfigured: Any = state == "ERROR" ? NSNull() : (state != "UNCONFIGURED")
        return [
            "observationSource": "VISIBLE_UI",
            "state": state,
            "starting": starting,
            "stopPending": stopPending,
            "permission": permission,
            "gpsState": gpsState,
            "credentialConfigured": credentialConfigured,
            "credentialConfiguredInferred": state != "ERROR",
            "stopConfirmationVisible": visibleLabel(app, "O coletor confirmou o encerramento.")
        ]
    }

    @MainActor
    private func visibleLabel(_ app: XCUIApplication, _ label: String) -> Bool {
        let candidate = element(app, label)
        return candidate.exists && candidate.isHittable
    }
}
