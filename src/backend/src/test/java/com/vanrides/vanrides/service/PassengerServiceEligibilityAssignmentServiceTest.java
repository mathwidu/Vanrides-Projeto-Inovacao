package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Passageiro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PassengerServiceEligibilityAssignmentServiceTest {

    @Mock
    private PassengerServiceEligibilityManagementService managementService;

    @InjectMocks
    private PassengerServiceEligibilityAssignmentService service;

    @Test
    void shouldDelegateEligibilityAssignmentToManagementService() {
        Company company = new Company("Empresa QA", "empresa-qa");
        company.setId(66L);
        Passageiro passageiro = new Passageiro();
        passageiro.setId(21L);
        List<Long> servicoIds = List.of(10L, 11L);

        service.assignServiceEligibilities(passageiro, company, 66L, servicoIds);

        verify(managementService).assignServiceEligibilities(passageiro, company, 66L, servicoIds);
    }
}
