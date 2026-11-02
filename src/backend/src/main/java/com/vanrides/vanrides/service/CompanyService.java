package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyService {

    static final String DEFAULT_COMPANY_SLUG = "default";
    private static final String DEFAULT_COMPANY_NAME = "VanRides Default";

    @Autowired
    private CompanyRepository companyRepository;

    public Company getDefaultCompany() {
        return companyRepository.findBySlug(DEFAULT_COMPANY_SLUG)
                .orElseGet(() -> companyRepository.save(new Company(DEFAULT_COMPANY_NAME, DEFAULT_COMPANY_SLUG)));
    }

    public Company getOrCreateBySlug(String rawSlug) {
        String slug = normalizeSlug(rawSlug);
        if (slug.isBlank()) {
            return getDefaultCompany();
        }

        return companyRepository.findBySlug(slug)
                .orElseGet(() -> companyRepository.save(new Company(buildCompanyName(slug), slug)));
    }

    public Company getOrCreateByName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return getDefaultCompany();
        }

        String slug = normalizeSlug(companyName);
        if (slug.isBlank()) {
            return getDefaultCompany();
        }

        return companyRepository.findBySlug(slug)
                .orElseGet(() -> companyRepository.save(new Company(companyName.trim(), slug)));
    }

    @Transactional
    public Company createNewForPublicBootstrap(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome da empresa é obrigatório para o primeiro cadastro."
            );
        }

        String normalizedName = companyName.trim();
        String slug = normalizeSlug(normalizedName);
        if (slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome da empresa inválido.");
        }
        if (companyRepository.findBySlug(slug).isPresent()) {
            throw companyAlreadyExists();
        }

        try {
            return companyRepository.saveAndFlush(new Company(normalizedName, slug));
        } catch (DataIntegrityViolationException exception) {
            throw companyAlreadyExists();
        }
    }

    private ResponseStatusException companyAlreadyExists() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Esta empresa já está cadastrada e requer ativação administrada."
        );
    }

    private String normalizeSlug(String rawSlug) {
        if (rawSlug == null) {
            return "";
        }

        return rawSlug
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    private String buildCompanyName(String slug) {
        if (slug.isBlank()) {
            return DEFAULT_COMPANY_NAME;
        }

        String[] parts = slug.split("-");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            if (!name.isEmpty()) {
                name.append(' ');
            }

            name.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                name.append(part.substring(1));
            }
        }

        return name.isEmpty() ? DEFAULT_COMPANY_NAME : name.append(" Transportes").toString();
    }
}
