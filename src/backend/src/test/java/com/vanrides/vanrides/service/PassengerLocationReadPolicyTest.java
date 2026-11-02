package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerLocationReadPolicyTest {

    private static final Long PASSAGEIRO_ID = 11L;
    private static final Long MOTORISTA_ID = 22L;
    private static final Long COMPANY_ID = 33L;
    private static final LocalDate OPERATION_DATE = LocalDate.of(2026, 7, 16);
    private static final Set<ViagemStatus> READABLE_TRIP_STATUSES = Set.of(
            ViagemStatus.PLANNED,
            ViagemStatus.IN_PROGRESS,
            ViagemStatus.COMPLETED
    );

    @Mock
    private ViagemRepository viagemRepository;

    @Mock
    private OperationalDateProvider operationalDateProvider;

    private PassengerLocationReadPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PassengerLocationReadPolicy(
                viagemRepository,
                operationalDateProvider
        );
    }

    @Test
    void shouldAllowOnlyTheOperationalTripUsingTheExplicitStatusAllowlist() {
        when(operationalDateProvider.today()).thenReturn(OPERATION_DATE);
        when(viagemRepository.existsByPassengerAndMotoristaOnDate(
                PASSAGEIRO_ID,
                MOTORISTA_ID,
                COMPANY_ID,
                OPERATION_DATE,
                READABLE_TRIP_STATUSES
        )).thenReturn(true);

        assertThatCode(() -> policy.requireCanReadMotorista(
                PASSAGEIRO_ID,
                MOTORISTA_ID,
                COMPANY_ID
        )).doesNotThrowAnyException();

    }

    @Test
    void shouldReturnExactGenericNotFoundWhenNoRelationshipExists() {
        when(operationalDateProvider.today()).thenReturn(OPERATION_DATE);

        assertThatThrownBy(() -> policy.requireCanReadMotorista(
                PASSAGEIRO_ID,
                MOTORISTA_ID,
                COMPANY_ID
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(exception.getReason()).isEqualTo("Motorista não encontrado na empresa atual.");
        });
    }

    @Test
    void shouldRejectInvalidIdentifiersBeforeAccessingRepositories() {
        assertGenericNotFound(null, MOTORISTA_ID, COMPANY_ID);
        assertGenericNotFound(0L, MOTORISTA_ID, COMPANY_ID);
        assertGenericNotFound(-1L, MOTORISTA_ID, COMPANY_ID);
        assertGenericNotFound(PASSAGEIRO_ID, null, COMPANY_ID);
        assertGenericNotFound(PASSAGEIRO_ID, 0L, COMPANY_ID);
        assertGenericNotFound(PASSAGEIRO_ID, -1L, COMPANY_ID);
        assertGenericNotFound(PASSAGEIRO_ID, MOTORISTA_ID, null);
        assertGenericNotFound(PASSAGEIRO_ID, MOTORISTA_ID, 0L);
        assertGenericNotFound(PASSAGEIRO_ID, MOTORISTA_ID, -1L);

        verifyNoInteractions(viagemRepository, operationalDateProvider);
    }

    private void assertGenericNotFound(Long passageiroId, Long motoristaId, Long companyId) {
        assertThatThrownBy(() -> policy.requireCanReadMotorista(passageiroId, motoristaId, companyId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Motorista não encontrado na empresa atual.");
                });
    }
}
