package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.DriverCompanySearchResponse;
import com.vanrides.vanrides.repository.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Service
public class DriverCompanySearchService {

    private final CompanyRepository companyRepository;

    public DriverCompanySearchService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public List<DriverCompanySearchResponse> searchCompanies(String query) {
        String normalizedQuery = hasText(query) ? query.trim() : null;
        return companyRepository.searchVisibleCompanies(CompanyService.DEFAULT_COMPANY_SLUG, normalizedQuery)
                .stream()
                .map(company -> new DriverCompanySearchResponse(company.getId(), company.getNome(), company.getSlug()))
                .toList();
    }
}
