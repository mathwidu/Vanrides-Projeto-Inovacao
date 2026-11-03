package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.CompanyInviteCode;
import com.vanrides.vanrides.repository.CompanyInviteCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Year;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyInviteCodeGeneratorTest {

    @Test
    void springContextCreatesGeneratorWithRepositoryDependency() {
        CompanyInviteCodeRepository repository = mock(CompanyInviteCodeRepository.class);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CompanyInviteCodeRepository.class, () -> repository);
            context.register(CompanyInviteCodeGenerator.class);

            context.refresh();

            assertThat(context.getBean(CompanyInviteCodeGenerator.class)).isNotNull();
        }
    }

    @Test
    void generatesInviteCodeWithExpectedFormat() {
        CompanyInviteCodeRepository repository = mock(CompanyInviteCodeRepository.class);
        when(repository.findByCode(anyString())).thenReturn(Optional.empty());
        CompanyInviteCodeGenerator generator = new CompanyInviteCodeGenerator(repository, new Random(0));

        String code = generator.generateUniqueCode();

        assertThat(code).matches("INV-" + Year.now().getValue() + "-[A-Z2-9]{4}");
        verify(repository).findByCode(code);
    }

    @Test
    void retriesWhenGeneratedCodeAlreadyExists() {
        CompanyInviteCodeRepository repository = mock(CompanyInviteCodeRepository.class);
        when(repository.findByCode(anyString()))
                .thenReturn(Optional.of(new CompanyInviteCode()))
                .thenReturn(Optional.empty());
        CompanyInviteCodeGenerator generator = new CompanyInviteCodeGenerator(repository, new Random(0));

        String code = generator.generateUniqueCode();

        assertThat(code).matches("INV-" + Year.now().getValue() + "-[A-Z2-9]{4}");
        verify(repository, times(2)).findByCode(anyString());
    }
}
