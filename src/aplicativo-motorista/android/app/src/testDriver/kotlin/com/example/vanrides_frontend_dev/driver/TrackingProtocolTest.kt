package com.example.vanrides_frontend_dev.driver

import org.junit.Test

class TrackingProtocolTest {
    @Test fun endpointIsolation() = TrackingProtocolChecks.endpointIsolation()
    @Test fun leaseConservativeAndReplay() = TrackingProtocolChecks.leaseConservativeAndReplay()
    @Test fun leaseBoundsAndCredential() = TrackingProtocolChecks.leaseBoundsAndCredential()
    @Test fun boundedQueueAndAge() = TrackingProtocolChecks.boundedQueueAndAge()
    @Test fun explicitAcknowledgmentsOnly() = TrackingProtocolChecks.explicitAcknowledgmentsOnly()
    @Test fun ownerAndFiniteNumbers() = TrackingProtocolChecks.ownerAndFiniteNumbers()
    @Test fun stopFailureClassification() = TrackingProtocolChecks.stopFailureClassification()
    @Test fun acknowledgedIdleStopSurvivesLaterCredentialFailure() = TrackingProtocolChecks.acknowledgedIdleStopSurvivesLaterCredentialFailure()
    @Test fun unknownOrUnacknowledgedStopNeverBecomesConfirmed() = TrackingProtocolChecks.unknownOrUnacknowledgedStopNeverBecomesConfirmed()
    @Test fun collectingOwnedProviderOrPendingStopCannotUseAnOldAcknowledgment() = TrackingProtocolChecks.collectingOwnedProviderOrPendingStopCannotUseAnOldAcknowledgment()
    @Test fun failedCheckpointStoreNeverStartsHttp() = TrackingProtocolChecks.failedCheckpointStoreNeverStartsHttp()
    @Test fun persistedAckInvalidationMustBeVisibleBeforeTheFirstRequest() = TrackingProtocolChecks.persistedAckInvalidationMustBeVisibleBeforeTheFirstRequest()
    @Test fun strictControlsAndStatus() = TrackingWireChecks.strictControlsAndStatus()
    @Test fun ackAndMissingSequences() = TrackingWireChecks.ackAndMissingSequences()
    @Test fun byteBoundsAndExactRetryBody() = TrackingWireChecks.byteBoundsAndExactRetryBody()
}
