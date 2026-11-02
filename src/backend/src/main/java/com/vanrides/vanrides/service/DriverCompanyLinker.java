package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.DriverCompanyMembership;
import com.vanrides.vanrides.model.DriverCompanyMembershipStatus;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.DriverCompanyMembershipRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DriverCompanyLinker {

    private final DriverCompanyMembershipRepository membershipRepository;
    private final MotoristaRepository motoristaRepository;

    public DriverCompanyLinker(DriverCompanyMembershipRepository membershipRepository,
                               MotoristaRepository motoristaRepository) {
        this.membershipRepository = membershipRepository;
        this.motoristaRepository = motoristaRepository;
    }

    public DriverCompanyMembership link(Motorista motorista, Company company, String source, String reviewNotes) {
        if (motorista.getCompany() != null && !motorista.getCompany().getId().equals(company.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Motorista já está vinculado a outra empresa.");
        }

        return membershipRepository
                .findFirstByMotoristaIdAndStatusOrderByCreatedAtDesc(motorista.getId(), DriverCompanyMembershipStatus.ACTIVE)
                .map(existing -> {
                    if (!existing.getCompany().getId().equals(company.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Motorista já está vinculado a outra empresa.");
                    }
                    if (motorista.getCompany() == null) {
                        motorista.setCompany(company);
                        motoristaRepository.save(motorista);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    motorista.setCompany(company);
                    motoristaRepository.save(motorista);

                    DriverCompanyMembership membership = new DriverCompanyMembership();
                    membership.setMotorista(motorista);
                    membership.setCompany(company);
                    membership.setStatus(DriverCompanyMembershipStatus.ACTIVE);
                    membership.setSource(source);
                    membership.setReviewNotes(reviewNotes);
                    return membershipRepository.save(membership);
                });
    }
}
