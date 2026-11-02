package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Passageiro;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PassengerServiceEligibilityAssignmentService {

    @Autowired
    private PassengerServiceEligibilityManagementService managementService;

    public void assignServiceEligibilities(Passageiro passageiro,
                                           Company company,
                                           Long companyId,
                                           List<Long> servicoIds) {
        managementService.assignServiceEligibilities(passageiro, company, companyId, servicoIds);
    }
}
