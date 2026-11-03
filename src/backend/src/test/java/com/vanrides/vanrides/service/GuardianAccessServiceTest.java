package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuardianAccessServiceTest {

    private static final LocalDate OPERATION_DATE = LocalDate.of(2026, 7, 16);
    private static final Set<ViagemStatus> READABLE_TRIP_STATUSES = Set.of(
            ViagemStatus.PLANNED,
            ViagemStatus.IN_PROGRESS,
            ViagemStatus.COMPLETED
    );

    @Mock
    private GuardianPassageiroRepository guardianPassageiroRepository;

    @Mock
    private ViagemRepository viagemRepository;

    @Mock
    private OperationalDateProvider operationalDateProvider;

    @InjectMocks
    private GuardianAccessService guardianAccessService;

    @Test
    void shouldListLinkedPassengerIdsUsingFetchedLinks() {
        Passageiro primeiro = new Passageiro();
        primeiro.setId(10L);

        Passageiro segundo = new Passageiro();
        segundo.setId(11L);

        GuardianPassageiro linkA = new GuardianPassageiro();
        linkA.setPassageiro(primeiro);

        GuardianPassageiro linkB = new GuardianPassageiro();
        linkB.setPassageiro(segundo);

        when(guardianPassageiroRepository.findAllWithPassageiroByGuardianIdAndCompanyId(7L, 88L))
                .thenReturn(List.of(linkA, linkB));

        List<Long> result = guardianAccessService.listLinkedPassageiroIds(7L, 88L);

        assertEquals(List.of(10L, 11L), result);
        verify(guardianPassageiroRepository).findAllWithPassageiroByGuardianIdAndCompanyId(7L, 88L);
    }

    @Test
    void shouldNeverUseLegacyPassengerDriverLinkAsLocationGrant() {
        when(operationalDateProvider.today()).thenReturn(OPERATION_DATE);

        assertFalse(guardianAccessService.hasLinkedMotorista(7L, 3L, 88L));
        verifyNoInteractions(guardianPassageiroRepository);
    }

    @Test
    void shouldAllowOperationalTripMotoristaForLinkedPassengerWithoutFixedDriver() {
        when(operationalDateProvider.today()).thenReturn(OPERATION_DATE);
        when(viagemRepository.existsByGuardianLinkedPassengerAndMotoristaOnDate(
                eq(7L),
                eq(4L),
                eq(88L),
                eq(OPERATION_DATE),
                eq(READABLE_TRIP_STATUSES)
        )).thenReturn(true);

        assertTrue(guardianAccessService.hasLinkedMotorista(7L, 4L, 88L));
        verify(operationalDateProvider).today();
    }

    @Test
    void shouldDenyMotoristaWhenOperationalTripDoesNotMatch() {
        when(operationalDateProvider.today()).thenReturn(OPERATION_DATE);
        when(viagemRepository.existsByGuardianLinkedPassengerAndMotoristaOnDate(
                eq(7L),
                eq(99L),
                eq(88L),
                eq(OPERATION_DATE),
                eq(READABLE_TRIP_STATUSES)
        )).thenReturn(false);

        assertFalse(guardianAccessService.hasLinkedMotorista(7L, 99L, 88L));
        verify(operationalDateProvider).today();
    }
}
