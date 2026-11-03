#if VANRIDES_DRIVER_MVP
import CoreLocation
import Foundation
import UIKit

// Owned by the process. Flutter engines, scenes and widgets only observe/control it.
final class DriverTrackingCoordinator: NSObject, CLLocationManagerDelegate {
  typealias Completion = (Result<[String: Any], DriverTrackingFailure>) -> Void
  static let shared = DriverTrackingCoordinator()
  private let permissionManager = CLLocationManager()
  private let transport = DriverTrackingTransport()
  private var credential: DriverTrackingCredential?
  private var credentialExpiresAt: Int64?
  private var state = "UNCONFIGURED"
  private var reason: String?
  private var gpsState = "UNKNOWN"
  private var sessionID: String?
  private var sessionGeneration: Int64 = 0
  private var lifecycle: UInt64 = 0
  private var lease = DriverTrackingLease()
  private var queue = DriverTrackingQueue()
  private var batch: DriverTrackingBatch?
  private var sequence: Int64 = 0
  private var accepted = 0
  private var duplicates = 0
  private var rejected = 0
  private var discardedCallbacks = 0
  private var lastCapture = -Double.infinity
  private var lastSend = -Double.infinity
  private var lastHeartbeat = -Double.infinity
  private var stopRequestedAt: Int64?
  private var stoppedAt: Int64?
  private var nativeStopCalledAt: Int64?
  private var nativeStopMonotonicMs: Int64?
  private var stopLeaseDeadlineMonotonicMs: Int64?
  private var pendingStop: DriverTrackingStopRequest?
  private let stopJournal = DriverSessionKeychainStore(service: "dev.matheusduarte.vanrides.driver.tracking-stop.v1")
  private var stopRetry = DriverTrackingStopRetry()
  private var stopRetryWork: DispatchWorkItem?
  private var stopRetryID: UUID?
  private var provider: CLLocationManager?
  private var providerDelegate: DriverTrackingLocationDelegate?
  private var timer: DispatchSourceTimer?
  private var task: DriverTrackingRequest?
  private var requestID: UUID?
  private var operation: Completion?
  private var permissionReply: Completion?
  private var permissionTimeout: DispatchWorkItem?
  private var permissionRequestedAlways = false
  private var permissionRequestedPrecise = false
  private var observers: [NSObjectProtocol] = []
  private var diagnosticURL: URL?
  private var diagnosticHistory: [[String: Any]] = []
  private var diagnosticWriteFailed = false

  private override init() {
    super.init()
    credential = DriverTrackingCredential.load()
    state = credential == nil ? "UNCONFIGURED" : "READY"
    #if !VANRIDES_GPS_SPIKE
    do {
      pendingStop = try DriverTrackingStopJournal.decode(stopJournal.load())
      if pendingStop != nil { state = "STOPPED"; reason = "STOP_RECONCILIATION_REQUIRED" }
    } catch { state = "ERROR"; reason = "STOP_STORAGE_ERROR" }
    #endif
    permissionManager.delegate = self
    do {
      let support = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask,
        appropriateFor: nil, create: true)
      var directory = support.appendingPathComponent("DriverTrackingDiagnostics", isDirectory: true)
      try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
        attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
      var values = URLResourceValues()
      values.isExcludedFromBackup = true
      try directory.setResourceValues(values)
      let url = directory.appendingPathComponent("sanitized-v1.json")
      diagnosticURL = url
      if let data = try? Data(contentsOf: url), data.count <= 32_768,
        let records = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
        diagnosticHistory = Array(records.suffix(8))
      }
    } catch { diagnosticWriteFailed = true }
    // Diagnostics exclude identity and GPS; the separate protected journal holds only a stop attestation.
    if let previous = diagnosticHistory.last {
      stopRequestedAt = (previous["stopRequestedAt"] as? NSNumber)?.int64Value
      stoppedAt = (previous["stoppedAt"] as? NSNumber)?.int64Value
      nativeStopCalledAt = (previous["nativeStopCalledAt"] as? NSNumber)?.int64Value
      nativeStopMonotonicMs = (previous["nativeStopMonotonicMs"] as? NSNumber)?.int64Value
      stopLeaseDeadlineMonotonicMs = (previous["leaseDeadlineMonotonicMs"] as? NSNumber)?.int64Value
      if previous["state"] as? String == "TRACKING" || previous["state"] as? String == "STARTING" {
        reason = "PROCESS_RECREATED"
      } else { reason = previous["reason"] as? String }
    }
    observers.append(NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification,
      object: nil, queue: .main) { [weak self] _ in self?.becameActive() })
    observers.append(NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification,
      object: nil, queue: .main) { [weak self] _ in
        guard let self = self else { return }
        if self.state == "STARTING" { self.stopLocally(reason: "FOREGROUND_REQUIRED") }
      })
    observers.append(NotificationCenter.default.addObserver(forName: UIApplication.willTerminateNotification,
      object: nil, queue: .main) { [weak self] _ in
        guard let self = self, self.state == "TRACKING" || self.state == "STARTING" else { return }
        self.stopLocally(reason: "PROCESS_TERMINATING")
      })
  }

  private var foreground: Bool { UIApplication.shared.applicationState == .active }
  private var stopPending: Bool { pendingStop != nil }

  private var permission: String {
    switch permissionManager.authorizationStatus {
    case .denied, .restricted: return "DENIED"
    case .notDetermined: return "UNKNOWN"
    case .authorizedAlways, .authorizedWhenInUse:
      if permissionManager.accuracyAuthorization != .fullAccuracy { return "APPROXIMATE" }
      return permissionManager.authorizationStatus == .authorizedAlways ? "GRANTED" : "UNKNOWN"
    @unknown default: return "DENIED"
    }
  }

  func snapshot() -> [String: Any] {
    _ = checkLease()
    purgeQueue()
    return ["state": state, "credentialConfigured": credential != nil, "permission": permission,
      "queued": queue.samples.count, "accepted": accepted, "duplicates": duplicates,
      "rejected": rejected, "dropped": queue.dropped, "discardedCallbacks": discardedCallbacks,
      "gpsState": gpsState, "diagnosticWriteFailed": diagnosticWriteFailed, "stopPending": stopPending,
      "leaseExpiresAt": lease.expiresAt == 0 ? NSNull() : NSNumber(value: lease.expiresAt),
      "reason": reason as Any? ?? NSNull(),
      "stopRequestedAt": stopRequestedAt.map { NSNumber(value: $0) } as Any? ?? NSNull(),
      "stoppedAt": stoppedAt.map { NSNumber(value: $0) } as Any? ?? NSNull()]
  }

  func configure(_ value: DriverTrackingCredential, completion: @escaping Completion) {
    guard state != "TRACKING", state != "STARTING", !stopPending, operation == nil, task == nil else {
      completion(.failure(.busy)); return
    }
    do { try value.save() }
    catch let failure as DriverTrackingFailure { completion(.failure(failure)); return }
    catch { completion(.failure(.credentialStorage)); return }
    lifecycle &+= 1
    credential = value
    credentialExpiresAt = nil
    sessionID = nil
    sessionGeneration = 0
    pendingStop = nil
    cancelStopRetry()
    lease = DriverTrackingLease()
    state = "READY"
    reason = nil
    syncStatus(completion: completion)
  }

  func requestPermission(completion: @escaping Completion) {
    guard foreground else { completion(.failure(.foregroundRequired)); return }
    guard permissionReply == nil, operation == nil else { completion(.failure(.busy)); return }
    permissionReply = completion
    permissionRequestedAlways = false
    permissionRequestedPrecise = false
    let timeout = DispatchWorkItem { [weak self] in self?.finishPermission() }
    permissionTimeout = timeout
    DispatchQueue.main.asyncAfter(deadline: .now() + 45, execute: timeout)
    advancePermission()
  }

  private func advancePermission() {
    guard permissionReply != nil, foreground else { return }
    switch permissionManager.authorizationStatus {
    case .notDetermined: permissionManager.requestWhenInUseAuthorization()
    case .authorizedWhenInUse:
      if !permissionRequestedAlways {
        permissionRequestedAlways = true
        permissionManager.requestAlwaysAuthorization()
      } else { finishPermission() }
    case .authorizedAlways:
      if permissionManager.accuracyAuthorization != .fullAccuracy && !permissionRequestedPrecise {
        permissionRequestedPrecise = true
        let epoch = lifecycle
        permissionManager.requestTemporaryFullAccuracyAuthorization(withPurposeKey: "DriverRunTracking") {
          [weak self] _ in
          DispatchQueue.main.async {
            guard let self = self, self.lifecycle == epoch else { return }
            self.finishPermission()
          }
        }
      } else { finishPermission() }
    default: finishPermission()
    }
  }

  private func finishPermission() {
    guard let reply = permissionReply else { return }
    permissionReply = nil
    permissionTimeout?.cancel()
    permissionTimeout = nil
    if permissionManager.authorizationStatus == .authorizedWhenInUse { reason = "BACKGROUND_PERMISSION_REQUIRED" }
    else if permission == "APPROXIMATE" { reason = "PRECISE_PERMISSION_REQUIRED" }
    else if permission == "GRANTED" && state != "TRACKING" { reason = nil }
    reply(.success(snapshot()))
  }

  func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    guard checkLease() else { return }
    if state == "TRACKING" && permission != "GRANTED" {
      stopLocally(reason: "PERMISSION_REVOKED"); pump(); return
    }
    // Authorization callbacks may arrive while the system prompt has made the app inactive.
    if permissionReply != nil && foreground { advancePermission() }
  }

  func start(intentID: String, activation: [String: Any]? = nil, completion: @escaping Completion) {
    #if !VANRIDES_GPS_SPIKE
    guard activation != nil else { completion(.failure(.invalidArguments)); return }
    #endif
    guard foreground else { completion(.failure(.foregroundRequired)); return }
    guard permission == "GRANTED" else { completion(.failure(.permissionRequired)); return }
    guard credential != nil else { completion(.failure(.credentialRequired)); return }
    guard !stopPending, operation == nil, permissionReply == nil, task == nil,
      state != "TRACKING", state != "STARTING" else { completion(.failure(.busy)); return }
    lifecycle &+= 1
    operation = completion
    state = "STARTING"
    reason = nil
    saveDiagnostic()
    let epoch = lifecycle
    reconcile { [weak self] success, orphan in
      guard let self = self else { return }
      guard success else { return }
      #if VANRIDES_GPS_SPIKE
      guard !orphan else { self.finish(.failure(.reconciliationRequired)); return }
      #endif
      guard self.lifecycle == epoch else { return }
      guard self.foreground, self.permission == "GRANTED" else {
        self.stopLocally(reason: "FOREGROUND_REQUIRED"); return
      }
      self.state = "STARTING"
      self.send(path: "/driver-app/v1/start-intents/\(intentID)/activate", body: activation ?? ["schemaVersion": 1]) {
        [weak self] json, started in
        guard let self = self else { return }
        do {
          let control = try DriverTrackingControl(json)
          guard control.isWithinCredential(expiry: self.credentialExpiresAt) else {
            throw DriverTrackingFailure.invalidContract
          }
          self.sessionID = control.sessionID
          self.sessionGeneration = control.generation
          self.lease = DriverTrackingLease()
          guard self.foreground, self.permission == "GRANTED", control.action == "CONTINUE" else {
            self.stopLocally(reason: control.action == "STOP" ? "REMOTE_STOP" : "FOREGROUND_REQUIRED")
            self.pump(); return
          }
          try self.lease.update(control, requestStarted: started, now: DriverTrackingClock.now())
          self.queue = DriverTrackingQueue()
          self.batch = nil
          self.sequence = 0
          self.accepted = 0; self.duplicates = 0; self.rejected = 0
          self.lastCapture = -Double.infinity
          self.lastSend = -Double.infinity
          self.lastHeartbeat = DriverTrackingClock.now()
          self.stopRequestedAt = nil; self.stoppedAt = nil; self.pendingStop = nil
          self.cancelStopRetry()
          self.gpsState = control.gpsState
          self.state = "TRACKING"
          self.beginLocation()
          self.beginTimer()
          self.saveDiagnostic()
          self.finish(.success(self.snapshot()))
        } catch { self.failClosed(.invalidContract) }
      }
    }
  }

  func syncStatus(completion: @escaping Completion) {
    _ = checkLease()
    guard credential != nil else { completion(.failure(.credentialRequired)); return }
    guard operation == nil, permissionReply == nil, task == nil, state != "STARTING" else {
      completion(.failure(.busy)); return
    }
    operation = completion
    if stopPending {
      // The original identity/stop record must be acknowledged before reconciliation can replace it.
      sendStopped { [weak self] in
        guard let self = self else { return }
        self.finish(.success(self.snapshot()))
      }
      return
    }
    reconcile { [weak self] success, _ in
      guard let self = self, success else { return }
      self.finish(.success(self.snapshot()))
    }
  }

  private func reconcile(completion: @escaping (Bool, Bool) -> Void) {
    send(path: "/driver-app/v1/status", body: nil) { [weak self] json, started in
      guard let self = self else { return }
      do {
        guard Set(json.keys) == ["schemaVersion", "environment", "serverTime", "credentialExpiresAt", "session"],
          try DriverTrackingContract.integer(json["schemaVersion"]) == 1,
          ["gps-spike", "driver-mvp"].contains(json["environment"] as? String ?? "") else { throw DriverTrackingFailure.invalidContract }
        let serverTime = try DriverTrackingContract.integer(json["serverTime"])
        let credentialExpiry = try DriverTrackingContract.integer(json["credentialExpiresAt"])
        guard credentialExpiry > serverTime else { throw DriverTrackingFailure.invalidContract }
        self.credentialExpiresAt = credentialExpiry
        if json["session"] is NSNull {
          if self.state == "TRACKING" { self.stopLocally(reason: "REMOTE_STOP") }
          else if self.state != "STARTING" && self.state != "STOPPED" { self.state = "READY"; self.reason = nil }
          completion(true, false); return
        }
        guard let remote = json["session"] as? [String: Any],
          Set(remote.keys) == ["sessionId", "generation", "state", "leaseExpiresAt", "gpsState"],
          let remoteState = remote["state"] as? String,
          ["RUNNING", "STOP_PENDING", "STOPPED", "EXPIRED", "REVOKED"].contains(remoteState) else {
          throw DriverTrackingFailure.invalidContract
        }
        var controlJSON = remote
        controlJSON.removeValue(forKey: "state")
        controlJSON["schemaVersion"] = 1
        controlJSON["environment"] = "gps-spike"
        controlJSON["serverTime"] = serverTime
        controlJSON["action"] = remoteState == "RUNNING" ? "CONTINUE" : "STOP"
        let control = try DriverTrackingControl(controlJSON)
        guard control.leaseExpiresAt <= credentialExpiry else { throw DriverTrackingFailure.invalidContract }
        if self.state == "TRACKING" {
          try self.apply(control, started: started)
          completion(true, false)
        } else if remoteState == "RUNNING" || remoteState == "STOP_PENDING" {
          #if !VANRIDES_GPS_SPIKE
          if self.state == "STARTING" && remoteState == "RUNNING" { completion(true, false); return }
          #endif
          // A new process must close the orphan before accepting another explicit intent.
          self.sessionID = control.sessionID
          self.sessionGeneration = control.generation
          self.stopLocally(reason: "PROCESS_RECREATED", cancelOperation: false, forceNewRecord: true)
          self.sendStopped { completion(true, true) }
        } else {
          if self.state != "STARTING" && self.state != "STOPPED" { self.state = "STOPPED"; self.reason = "REMOTE_STOP" }
          completion(true, false)
        }
      } catch { self.failClosed(.invalidContract) }
    }
  }

  func stop() {
    if state == "STOPPED", provider == nil { pump(); return }
    stopLocally(reason: "USER_STOP")
    pump()
  }

  private func stopLocally(reason newReason: String, cancelOperation: Bool = true, forceNewRecord: Bool = false) {
    let wasStopped = state == "STOPPED" && stoppedAt != nil && !forceNewRecord
    lifecycle &+= 1
    requestID = nil
    task?.cancel(); task = nil
    timer?.cancel(); timer = nil
    cancelStopRetry(resetBackoff: !wasStopped)
    if !wasStopped { stopRequestedAt = DriverTrackingClock.wallMilliseconds() }
    // Remove the old delegate as well as invalidating its captured lifecycle generation.
    provider?.delegate = nil
    provider?.stopUpdatingLocation()
    provider = nil; providerDelegate = nil
    // Also record an explicit native stop when closing an orphan (there may be no local provider).
    permissionManager.stopUpdatingLocation()
    if !wasStopped {
      nativeStopCalledAt = DriverTrackingClock.wallMilliseconds()
      nativeStopMonotonicMs = Int64(DriverTrackingClock.now() * 1000)
      stopLeaseDeadlineMonotonicMs = lease.deadline > 0 ? Int64(lease.deadline * 1000) : nil
      stoppedAt = max(stopRequestedAt ?? 0, DriverTrackingClock.wallMilliseconds())
    }
    queue.clear(); batch = nil
    gpsState = "UNKNOWN"
    state = credential == nil ? "UNCONFIGURED" : "STOPPED"
    if !wasStopped {
      reason = newReason
      if let sessionID = sessionID, let requested = stopRequestedAt, let stopped = stoppedAt {
        pendingStop = DriverTrackingStopRequest(sessionID: sessionID, generation: sessionGeneration,
          reason: newReason, requestedAt: requested, stoppedAt: stopped, stopRequestRef: transport.stopReference)
      } else { pendingStop = nil }
    }
    saveStopJournal()
    saveDiagnostic()
    if cancelOperation { finish(.failure(.cancelled)) }
    if let reply = permissionReply {
      permissionReply = nil; permissionTimeout?.cancel(); permissionTimeout = nil
      reply(.failure(.cancelled))
    }
    // Even a lease/permission callback without a subsequent pump must attempt the native STOP ACK.
    if stopPending { armStopRetry(after: stopRetry.remainingDelay(now: DriverTrackingClock.now())) }
  }

  @discardableResult private func checkLease() -> Bool {
    guard state == "TRACKING" else { return true }
    if DriverTrackingClock.now() >= lease.deadline {
      stopLocally(reason: "LEASE_EXPIRED")
      return false
    }
    return true
  }

  private func beginLocation() {
    let manager = CLLocationManager()
    let delegate = DriverTrackingLocationDelegate(owner: self, generation: lifecycle)
    provider = manager; providerDelegate = delegate
    manager.delegate = delegate
    manager.desiredAccuracy = kCLLocationAccuracyBest
    manager.distanceFilter = kCLDistanceFilterNone
    manager.activityType = .automotiveNavigation
    manager.pausesLocationUpdatesAutomatically = false
    manager.allowsBackgroundLocationUpdates = true
    manager.showsBackgroundLocationIndicator = true
    manager.startUpdatingLocation()
  }

  fileprivate func locations(_ locations: [CLLocation], generation: UInt64) {
    guard generation == lifecycle else { discardedCallback(); return }
    guard checkLease(), state == "TRACKING" else { return }
    guard permission == "GRANTED" else { stopLocally(reason: "PERMISSION_REVOKED"); pump(); return }
    let now = DriverTrackingClock.now()
    guard now - lastCapture >= 5, let location = locations.last else { pump(); return }
    let coordinate = location.coordinate
    guard coordinate.latitude.isFinite, (-90...90).contains(coordinate.latitude),
      coordinate.longitude.isFinite, (-180...180).contains(coordinate.longitude),
      location.horizontalAccuracy.isFinite, (0...10_000).contains(location.horizontalAccuracy) else { return }
    let age = max(0, Date().timeIntervalSince(location.timestamp))
    guard location.timestamp.timeIntervalSince1970.isFinite,
      location.timestamp.timeIntervalSince1970 >= 0, age < 120,
      location.timestamp.timeIntervalSinceNow <= 10 else { return }
    sequence += 1
    let speed = location.speed.isFinite && (0...150).contains(location.speed) ? location.speed : nil
    let heading = location.course.isFinite && (0..<360).contains(location.course) ? location.course : nil
    queue.append(DriverTrackingSample(sequence: sequence,
      capturedAt: Int64(location.timestamp.timeIntervalSince1970 * 1000),
      latitude: coordinate.latitude, longitude: coordinate.longitude, accuracyM: location.horizontalAccuracy,
      speedMps: speed, headingDeg: heading, expires: now + 120 - age), now: now)
    lastCapture = now
    pump()
  }

  fileprivate func locationFailed(_ error: Error, generation: UInt64) {
    guard generation == lifecycle else { discardedCallback(); return }
    guard checkLease(), state == "TRACKING" else { return }
    if (error as? CLError)?.code == .denied { stopLocally(reason: "PERMISSION_REVOKED") }
    // Transient location errors never fabricate a sample or fresh GPS state.
    pump()
  }

  private func becameActive() {
    _ = checkLease()
    if state == "TRACKING" && permission != "GRANTED" { stopLocally(reason: "PERMISSION_REVOKED") }
    if permissionReply != nil { advancePermission() }
    pump()
  }

  private func beginTimer() {
    timer?.cancel()
    let epoch = lifecycle
    let source = DispatchSource.makeTimerSource(queue: .main)
    source.schedule(deadline: .now() + 1, repeating: 1, leeway: .milliseconds(100))
    source.setEventHandler { [weak self] in
      guard let self = self else { return }
      guard self.lifecycle == epoch else { self.discardedCallback(); return }
      _ = self.checkLease()
      self.pump()
    }
    timer = source
    source.resume()
  }

  private func cancelStopRetry(resetBackoff: Bool = true) {
    stopRetryID = nil
    stopRetryWork?.cancel()
    stopRetryWork = nil
    if resetBackoff { stopRetry.reset() }
  }

  private func armStopRetry(after delay: Double) {
    cancelStopRetry(resetBackoff: false)
    guard stopPending, credential != nil else { return }
    let id = UUID()
    let epoch = lifecycle
    let work = DispatchWorkItem { [weak self] in
      guard let self = self, self.stopRetryID == id, self.lifecycle == epoch, self.stopPending else { return }
      self.cancelStopRetry(resetBackoff: false)
      self.pump()
    }
    stopRetryID = id
    stopRetryWork = work
    // This is best-effort process execution; iOS suspension can defer it until foreground resumes.
    DispatchQueue.main.asyncAfter(deadline: .now() + max(0, delay), execute: work)
  }

  private func purgeQueue() {
    queue.purge(now: DriverTrackingClock.now())
    if let current = batch {
      let remaining = current.sequences.subtracting(current.acknowledged)
      if !remaining.isSubset(of: Set(queue.samples.map(\.sequence))) { batch = nil }
    }
  }

  private func pump() {
    _ = checkLease()
    purgeQueue()
    guard task == nil, operation == nil, credential != nil else { return }
    if stopPending {
      let delay = stopRetry.remainingDelay(now: DriverTrackingClock.now())
      if delay > 0 {
        if stopRetryWork == nil { armStopRetry(after: delay) }
      } else { sendStopped() }
      return
    }
    guard state == "TRACKING", let sessionID = sessionID else { return }
    let now = DriverTrackingClock.now()
    if now - lastHeartbeat >= 30 {
      lastHeartbeat = now
      send(path: "/driver-app/v1/tracking-sessions/\(sessionID)/state",
        body: ["schemaVersion": 1, "generation": sessionGeneration, "state": "RUNNING"]) {
        [weak self] json, started in
        guard let self = self else { return }
        do { try self.apply(DriverTrackingControl(json), started: started) }
        catch { self.failClosed(.invalidContract) }
      }
      return
    }
    guard now - lastSend >= 5, !queue.samples.isEmpty else { return }
    do {
      if batch == nil { batch = try DriverTrackingBatch(samples: queue.samples, sessionID: sessionID,
        generation: sessionGeneration, deviceTime: DriverTrackingClock.wallMilliseconds()) }
      guard let current = batch else { return }
      lastSend = now
      send(path: "/driver-app/v1/tracking-sessions/\(sessionID)/samples", encodedBody: current.body) {
        [weak self] json, started in
        guard let self = self else { return }
        do {
          let control = try DriverTrackingControl(json, additionalKeys: ["accepted", "duplicate", "rejected"])
          let (a, d, r) = try current.acknowledgements(json)
          try self.apply(control, started: started)
          guard self.state == "TRACKING" else { return }
          self.accepted += self.queue.remove(a).count
          self.duplicates += self.queue.remove(d).count
          self.rejected += self.queue.remove(r).count
          self.batch?.acknowledged.formUnion(a.union(d).union(r))
          if self.batch?.acknowledged == current.sequences { self.batch = nil }
        } catch { self.failClosed(.invalidContract) }
      }
    } catch { failClosed(.invalidContract) }
  }

  private func apply(_ control: DriverTrackingControl, started: Double) throws {
    guard control.sessionID == sessionID, control.generation == sessionGeneration else {
      throw DriverTrackingFailure.invalidContract
    }
    guard control.isWithinCredential(expiry: credentialExpiresAt) else {
      throw DriverTrackingFailure.invalidContract
    }
    guard state == "TRACKING" else { throw DriverTrackingFailure.invalidContract }
    if control.action == "STOP" { stopLocally(reason: "REMOTE_STOP"); return }
    let previousExpiry = lease.expiresAt
    try lease.update(control, requestStarted: started, now: DriverTrackingClock.now())
    gpsState = control.gpsState
    if lease.expiresAt != previousExpiry { saveDiagnostic() }
  }

  private func saveStopJournal() {
    #if !VANRIDES_GPS_SPIKE
    do { try stopJournal.save(DriverTrackingStopJournal.encode(pendingStop)) }
    catch { reason = "STOP_STORAGE_ERROR" }
    #endif
  }

  private func sendStopped(completion: (() -> Void)? = nil) {
    #if VANRIDES_GPS_SPIKE
    sendStopPacket(completion: completion)
    #else
    guard let pending = pendingStop else { completion?(); return }
    send(path: "/driver-app/v1/status", body: nil) { [weak self] json, _ in
      guard let self = self else { return }
      guard let session = json["session"] as? [String: Any],
        session["sessionId"] as? String == pending.sessionID,
        (try? DriverTrackingContract.integer(session["generation"])) == pending.generation else {
        self.failClosed(.invalidContract); return
      }
      if session["state"] as? String == "STOPPED" {
        self.pendingStop = nil; self.cancelStopRetry(); self.saveStopJournal(); self.saveDiagnostic(); completion?()
      } else { self.sendStopPacket(completion: completion) }
    }
    #endif
  }

  private func sendStopPacket(completion: (() -> Void)? = nil) {
    guard let stopped = pendingStop else {
      completion?(); return
    }
    cancelStopRetry(resetBackoff: false)
    send(path: "/driver-app/v1/tracking-sessions/\(stopped.sessionID)/state", body: stopped.body) { [weak self] json, _ in
      guard let self = self else { return }
      do {
        let control = try DriverTrackingControl(json)
        guard control.sessionID == stopped.sessionID, control.generation == stopped.generation,
          control.action == "STOP" else { throw DriverTrackingFailure.invalidContract }
        self.pendingStop = nil
        self.saveStopJournal()
        self.cancelStopRetry()
        self.saveDiagnostic()
        completion?()
      } catch { self.failClosed(.invalidContract) }
    }
  }

  private func send(path: String, body: [String: Any]?,
    completion: @escaping ([String: Any], Double) -> Void
  ) {
    do {
      let encoded = try body.map { try JSONSerialization.data(withJSONObject: $0, options: [.sortedKeys]) }
      send(path: path, encodedBody: encoded, completion: completion)
    } catch { failClosed(.invalidContract) }
  }

  private func send(path: String, encodedBody: Data?,
    completion: @escaping ([String: Any], Double) -> Void
  ) {
    guard checkLease(), task == nil, let credential = credential else {
      finish(.failure(.busy)); return
    }
    let id = UUID()
    let epoch = lifecycle
    let started = DriverTrackingClock.now()
    requestID = id
    do {
      let outgoing = try transport.request(credential: credential, path: path, body: encodedBody) {
        [weak self] result, status in
        guard let self = self else { return }
        guard self.requestID == id, self.lifecycle == epoch else { self.discardedCallback(); return }
        self.task = nil; self.requestID = nil
        guard self.checkLease() else { self.pump(); return }
        switch result {
        case .success(let json): completion(json, started)
        case .failure(let failure):
          let retry = status == nil || status == 429 || (status.map { $0 >= 500 } ?? false)
          if self.stopPending && status == 409 {
            self.reason = "STOP_RECONCILIATION_REQUIRED"
            self.armStopRetry(after: self.stopRetry.failed(now: DriverTrackingClock.now()))
            self.saveDiagnostic(); self.finish(.failure(failure))
          } else if failure == .invalidContract || !retry { self.failClosed(failure) }
          else {
            if self.state == "STARTING" { self.state = "ERROR"; self.reason = failure.rawValue }
            self.gpsState = "UNKNOWN"
            self.saveDiagnostic()
            if self.stopPending {
              let delay = self.stopRetry.failed(now: DriverTrackingClock.now())
              self.armStopRetry(after: delay)
            }
            self.finish(.failure(failure))
          }
        }
      }
      task = outgoing
      outgoing.resume()
    } catch { failClosed(.invalidContract) }
  }

  private func failClosed(_ failure: DriverTrackingFailure) {
    let previousStop = pendingStop
    let reply = operation
    operation = nil
    stopLocally(reason: failure.rawValue)
    // Fatal responses pause retry, but never erase an unconfirmed native attestation.
    if let previousStop = previousStop { pendingStop = previousStop; saveStopJournal() }
    cancelStopRetry()
    if credential == nil { state = "UNCONFIGURED" }
    else { state = "ERROR" }
    saveDiagnostic()
    reply?(.failure(failure))
  }

  private func finish(_ result: Result<[String: Any], DriverTrackingFailure>) {
    let reply = operation
    operation = nil
    reply?(result)
  }

  private func discardedCallback() {
    discardedCallbacks += 1
    saveDiagnostic()
  }

  private func saveDiagnostic() {
    var value: [String: Any] = ["state": state, "recordedAt": DriverTrackingClock.wallMilliseconds(),
      "accepted": accepted, "duplicates": duplicates, "rejected": rejected, "dropped": queue.dropped,
      "discardedCallbacks": discardedCallbacks]
    if let reason = reason { value["reason"] = reason }
    if let time = stopRequestedAt { value["stopRequestedAt"] = time }
    if let time = stoppedAt { value["stoppedAt"] = time }
    if let time = nativeStopCalledAt { value["nativeStopCalledAt"] = time }
    if let time = nativeStopMonotonicMs { value["nativeStopMonotonicMs"] = time }
    if let time = stopLeaseDeadlineMonotonicMs { value["leaseDeadlineMonotonicMs"] = time }
    if state == "TRACKING", lease.deadline > 0 {
      value["activeLeaseDeadlineMonotonicMs"] = Int64(lease.deadline * 1000)
    }
    diagnosticHistory.append(value)
    diagnosticHistory = Array(diagnosticHistory.suffix(8))
    guard let url = diagnosticURL else { diagnosticWriteFailed = true; return }
    do {
      let data = try JSONSerialization.data(withJSONObject: diagnosticHistory, options: [.sortedKeys])
      try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
      if state == "STOPPED" || state == "ERROR" {
        let handle = try FileHandle(forWritingTo: url)
        defer { try? handle.close() }
        try handle.synchronize()
      }
      diagnosticWriteFailed = false
    } catch { diagnosticWriteFailed = true }
  }
}

private final class DriverTrackingLocationDelegate: NSObject, CLLocationManagerDelegate {
  private weak var owner: DriverTrackingCoordinator?
  private let generation: UInt64
  init(owner: DriverTrackingCoordinator, generation: UInt64) {
    self.owner = owner; self.generation = generation
  }
  func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    owner?.locations(locations, generation: generation)
  }
  func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    owner?.locationFailed(error, generation: generation)
  }
  func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    // Recheck the permission and lease even if no location fix is delivered.
    owner?.locations([], generation: generation)
  }
}
#endif
