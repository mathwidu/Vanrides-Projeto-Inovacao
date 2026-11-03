package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.PassengerCompanySearchResponse;
import com.vanrides.vanrides.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Service
public class PassengerCompanySearchService {

    @Autowired
    private CompanyRepository companyRepository;

    public List<PassengerCompanySearchResponse> searchCompanies(String query) {
        String normalizedQuery = hasText(query) ? query.trim() : null;
        return companyRepository.searchVisibleCompanies(CompanyService.DEFAULT_COMPANY_SLUG, normalizedQuery)
                .stream()
                .map(company -> new PassengerCompanySearchResponse(company.getId(), company.getNome(), company.getSlug()))
                .toList();
    }
}
