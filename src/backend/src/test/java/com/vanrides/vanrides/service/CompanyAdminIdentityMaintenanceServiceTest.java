package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.model.CompanyAdmin;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminIdentityMaintenanceServiceTest {

    @Mock private CompanyAdminRepository companyAdminRepository;
    @Mock private LoginIdentityService loginIdentityService;

    @InjectMocks
    private CompanyAdminService companyAdminService;

    @BeforeEach
    void configureIdentitySeam() {
        lenient().when(loginIdentityService.requireAvailableFor(any(), any(), any()))
                .thenAnswer(invocation -> normalize(invocation.getArgument(0, String.class)));
        lenient().when(loginIdentityService.requireOwnedBy(any(), any(), any()))
                .thenAnswer(invocation -> normalize(invocation.getArgument(0, String.class)));
        lenient().when(loginIdentityService.executeIdentityWrite(any()))
                .thenAnswer(invocation -> {
                    Supplier<?> write = invocation.getArgument(0);
                    return write.get();
                });
    }

    @Test
    void shouldValidateOldOwnerAndDestinationBeforeUpdatingPendingAdminEmail() {
        CompanyAdmin admin = new CompanyAdmin();
        ReflectionTestUtils.setField(admin, "id", 31L);
        admin.setEmail("old.admin@vanrides.dev");
        admin.setEmailVerified(true);
        admin.setEmailVerifiedAt(OffsetDateTime.now());
        when(companyAdminRepository.findById(31L)).thenReturn(Optional.of(admin));
        when(companyAdminRepository.saveAndFlush(admin)).thenAnswer(invocation -> {
            assertThat(admin.isEmailVerified()).isFalse();
            assertThat(admin.getEmailVerifiedAt()).isNull();
            return admin;
        });

        companyAdminService.updatePendingAdminEmail(31L, " New.Admin@VanRides.dev ");

        var order = inOrder(loginIdentityService, companyAdminRepository);
        order.verify(loginIdentityService).requireOwnedBy(
                "old.admin@vanrides.dev",
                UserRole.COMPANY_ADMIN,
                31L
        );
        order.verify(loginIdentityService).requireAvailableFor(
                " New.Admin@VanRides.dev ",
                UserRole.COMPANY_ADMIN,
                31L
        );
        order.verify(loginIdentityService).executeIdentityWrite(any());
        verify(companyAdminRepository).saveAndFlush(admin);
        assertThat(admin.getEmail()).isEqualTo("new.admin@vanrides.dev");
        assertThat(admin.isEmailVerified()).isFalse();
        assertThat(admin.getEmailVerifiedAt()).isNull();
    }

    @Test
    void shouldResolveLegacyEmailLookupThroughTheGlobalOwnerId() {
        CompanyAdmin admin = new CompanyAdmin();
        ReflectionTestUtils.setField(admin, "id", 32L);
        admin.setEmail("admin.owner@vanrides.dev");
        var owner = new LoginIdentityService.LoginIdentityOwner(
                UserRole.COMPANY_ADMIN,
                32L,
                "admin.owner@vanrides.dev"
        );
        when(loginIdentityService.findOwner(" Admin.Owner@VanRides.dev "))
                .thenReturn(Optional.of(owner));
        when(companyAdminRepository.findById(32L)).thenReturn(Optional.of(admin));

        assertThat(companyAdminService.findByEmail(" Admin.Owner@VanRides.dev "))
                .isSameAs(admin);
        verify(companyAdminRepository, never()).findByEmail(any());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
