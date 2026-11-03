package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AccountMeResponse;
import com.vanrides.vanrides.dto.AccountPasswordChangeRequest;
import com.vanrides.vanrides.dto.AccountProfileUpdateRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyAdmin;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountMeServiceTest {

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private GuardianRepository guardianRepository;

    @Mock
    private CompanyAdminRepository companyAdminRepository;

    @InjectMocks
    private AccountMeService accountMeService;

    @Test
    void shouldResolvePassengerProfileWithCompanyDriverLinkAndProfileFields() {
        Company company = company(66L, "Escola Aurora", "escola-aurora");
        Motorista motorista = new Motorista("Joao Driver", "driver@vanrides.dev", "51911111111", "hash");
        motorista.setId(44L);
        Passageiro passageiro = passenger(21L);
        passageiro.setEmailVerified(true);
        passageiro.setCompany(company);
        passageiro.setMotorista(motorista);
        passageiro.setDataNascimento(LocalDate.of(2012, 5, 20));
        passageiro.setFotoPerfilUrl("/uploads/avatars/passenger-21.png");
        passageiro.setMenorDeIdade(true);
        when(passageiroRepository.findById(21L)).thenReturn(Optional.of(passageiro));

        AccountMeResponse response = accountMeService.resolve(UserRole.PASSENGER, 21L);

        assertThat(response.getId()).isEqualTo(21L);
        assertThat(response.getTipoUsuario()).isEqualTo("Passageiro");
        assertThat(response.getPapel()).isEqualTo("PASSENGER");
        assertThat(response.getNome()).isEqualTo("Ana Passageira");
        assertThat(response.getEmail()).isEqualTo("ana@vanrides.dev");
        assertThat(response.getTelefone()).isEqualTo("51999999999");
        assertThat(response.getDataNascimento()).isEqualTo(LocalDate.of(2012, 5, 20));
        assertThat(response.getFotoPerfilUrl()).isEqualTo("/uploads/avatars/passenger-21.png");
        assertThat(response.getMenorDeIdade()).isTrue();
        assertThat(response.isEmailVerified()).isTrue();
        assertThat(response.getPassageiroId()).isEqualTo(21L);
        assertThat(response.getMotoristaId()).isEqualTo(44L);
        assertThat(response.getEmpresa().getId()).isEqualTo(66L);
        assertThat(response.getEmpresa().getNome()).isEqualTo("Escola Aurora");
        assertThat(response.getEmpresa().getSlug()).isEqualTo("escola-aurora");
    }

    @Test
    void shouldResolveDriverGuardianAndCompanyAdminProfileFields() {
        Company company = company(88L, "VanRides Operacao", "vanrides-operacao");
        Motorista motorista = new Motorista("Bruno Motorista", "bruno@vanrides.dev", "51922222222", "hash");
        motorista.setId(31L);
        motorista.setDataNascimento(LocalDate.of(1988, 7, 14));
        motorista.setFotoPerfilUrl("/uploads/avatars/bruno.webp");
        Guardian guardian = guardian(71L, company);
        guardian.setDataNascimento(LocalDate.of(1980, 1, 2));
        guardian.setFotoPerfilUrl("/uploads/avatars/guardian-71.jpg");
        CompanyAdmin admin = companyAdmin(90L, company);
        admin.setDataNascimento(LocalDate.of(1978, 11, 3));
        admin.setFotoPerfilUrl("/uploads/avatars/admin-90.jpg");
        when(motoristaRepository.findById(31L)).thenReturn(Optional.of(motorista));
        when(guardianRepository.findById(71L)).thenReturn(Optional.of(guardian));
        when(companyAdminRepository.findById(90L)).thenReturn(Optional.of(admin));

        AccountMeResponse driver = accountMeService.resolve(UserRole.DRIVER, 31L);
        AccountMeResponse guardianResponse = accountMeService.resolve(UserRole.GUARDIAN, 71L);
        AccountMeResponse adminResponse = accountMeService.resolve(UserRole.COMPANY_ADMIN, 90L);

        assertThat(driver.getFotoPerfilUrl()).isEqualTo("/uploads/avatars/bruno.webp");
        assertThat(driver.getMenorDeIdade()).isNull();
        assertThat(guardianResponse.getFotoPerfilUrl()).isEqualTo("/uploads/avatars/guardian-71.jpg");
        assertThat(guardianResponse.getResponsavelId()).isEqualTo(71L);
        assertThat(adminResponse.getFotoPerfilUrl()).isEqualTo("/uploads/avatars/admin-90.jpg");
        assertThat(adminResponse.getCompanyAdminId()).isEqualTo(90L);
        assertThat(adminResponse.getEmpresa().getSlug()).isEqualTo("vanrides-operacao");
    }

    @Test
    void shouldPatchPassengerProfileWithoutChangingSensitiveFields() {
        Company company = company(66L, "Escola Aurora", "escola-aurora");
        Passageiro passageiro = passenger(21L);
        passageiro.setCompany(company);
        passageiro.setEmailVerified(true);
        passageiro.setSenha("hash-sensivel");
        passageiro.setFotoPerfilUrl("/uploads/avatars/passenger-21.png");
        when(passageiroRepository.findById(21L)).thenReturn(Optional.of(passageiro));
        when(passageiroRepository.save(any(Passageiro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountMeResponse response = accountMeService.updateProfile(
                UserRole.PASSENGER,
                21L,
                new AccountProfileUpdateRequest("  Ana Atualizada  ", " 51988888888 ", LocalDate.of(2011, 8, 9), null)
        );

        assertThat(response.getNome()).isEqualTo("Ana Atualizada");
        assertThat(response.getTelefone()).isEqualTo("51988888888");
        assertThat(response.getEmail()).isEqualTo("ana@vanrides.dev");
        assertThat(response.getPapel()).isEqualTo("PASSENGER");
        assertThat(response.getDataNascimento()).isEqualTo(LocalDate.of(2011, 8, 9));
        assertThat(response.getFotoPerfilUrl()).isEqualTo("/uploads/avatars/passenger-21.png");
        assertThat(response.getMenorDeIdade()).isTrue();
        assertThat(response.getEmpresa().getId()).isEqualTo(66L);
        assertThat(passageiro.getSenha()).isEqualTo("hash-sensivel");
        assertThat(passageiro.getCompany()).isSameAs(company);
    }

    @Test
    void shouldRejectFutureBirthDateWhenPatchingProfile() {
        Passageiro passageiro = passenger(21L);
        when(passageiroRepository.findById(21L)).thenReturn(Optional.of(passageiro));

        assertThatThrownBy(() -> accountMeService.updateProfile(
                UserRole.PASSENGER,
                21L,
                new AccountProfileUpdateRequest(null, null, LocalDate.now().plusDays(1), null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldPersistDriverProfilePhotoDataUriOnProfilePatch() {
        Motorista motorista = new Motorista("Bruno Motorista", "bruno@vanrides.dev", "51922222222", "hash");
        motorista.setId(31L);
        when(motoristaRepository.findById(31L)).thenReturn(Optional.of(motorista));
        when(motoristaRepository.save(any(Motorista.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountMeResponse response = accountMeService.updateProfile(
                UserRole.DRIVER,
                31L,
                new AccountProfileUpdateRequest(null, null, null, tinyPngDataUri())
        );

        assertThat(response.getFotoPerfilUrl()).isEqualTo(tinyPngDataUri());
        assertThat(motorista.getFotoPerfilUrl()).isEqualTo(tinyPngDataUri());
    }

    @Test
    void shouldRejectInvalidProfilePhotoUrlBeforeSavingAccount() {
        Motorista motorista = new Motorista("Bruno Motorista", "bruno@vanrides.dev", "51922222222", "hash");
        motorista.setId(31L);
        when(motoristaRepository.findById(31L)).thenReturn(Optional.of(motorista));

        assertThatThrownBy(() -> accountMeService.updateProfile(
                UserRole.DRIVER,
                31L,
                new AccountProfileUpdateRequest(null, null, null, "http://example.com/avatar.txt")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldChangePasswordForAllRoles() {
        Passageiro passageiro = passenger(21L);
        passageiro.setSenha(hash("Atual!123"));
        Motorista motorista = new Motorista("Bruno Motorista", "bruno@vanrides.dev", "51922222222", hash("Atual!123"));
        motorista.setId(31L);
        Guardian guardian = guardian(71L, company(66L, "Escola Aurora", "escola-aurora"));
        guardian.setSenha(hash("Atual!123"));
        CompanyAdmin admin = companyAdmin(90L, company(88L, "VanRides Operacao", "vanrides-operacao"));
        admin.setSenha(hash("Atual!123"));
        when(passageiroRepository.findById(21L)).thenReturn(Optional.of(passageiro));
        when(motoristaRepository.findById(31L)).thenReturn(Optional.of(motorista));
        when(guardianRepository.findById(71L)).thenReturn(Optional.of(guardian));
        when(companyAdminRepository.findById(90L)).thenReturn(Optional.of(admin));
        when(passageiroRepository.save(any(Passageiro.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(motoristaRepository.save(any(Motorista.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(guardianRepository.save(any(Guardian.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyAdminRepository.save(any(CompanyAdmin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountMeService.changePassword(UserRole.PASSENGER, 21L, passwordChange("Atual!123", "Nova!1234"));
        accountMeService.changePassword(UserRole.DRIVER, 31L, passwordChange("Atual!123", "Nova!1234"));
        accountMeService.changePassword(UserRole.GUARDIAN, 71L, passwordChange("Atual!123", "Nova!1234"));
        accountMeService.changePassword(UserRole.COMPANY_ADMIN, 90L, passwordChange("Atual!123", "Nova!1234"));

        assertThat(BCrypt.verifyer().verify("Nova!1234".toCharArray(), passageiro.getSenha().toCharArray()).verified).isTrue();
        assertThat(BCrypt.verifyer().verify("Nova!1234".toCharArray(), motorista.getSenha().toCharArray()).verified).isTrue();
        assertThat(BCrypt.verifyer().verify("Nova!1234".toCharArray(), guardian.getSenha().toCharArray()).verified).isTrue();
        assertThat(BCrypt.verifyer().verify("Nova!1234".toCharArray(), admin.getSenha().toCharArray()).verified).isTrue();
    }

    @Test
    void shouldRejectWrongCurrentPassword() {
        Motorista motorista = new Motorista("Bruno Motorista", "bruno@vanrides.dev", "51922222222", hash("Atual!123"));
        motorista.setId(31L);
        when(motoristaRepository.findById(31L)).thenReturn(Optional.of(motorista));

        assertThatThrownBy(() -> accountMeService.changePassword(
                UserRole.DRIVER,
                31L,
                passwordChange("Errada!123", "Nova!1234")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturnNotFoundWhenAuthenticatedAccountDisappears() {
        when(passageiroRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountMeService.resolve(UserRole.PASSENGER, 404L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Conta autenticada não encontrada");
    }

    private AccountPasswordChangeRequest passwordChange(String senhaAtual, String novaSenha) {
        AccountPasswordChangeRequest request = new AccountPasswordChangeRequest();
        request.setSenhaAtual(senhaAtual);
        request.setNovaSenha(novaSenha);
        return request;
    }

    private String tinyPngDataUri() {
        return "data:image/png;base64,iVBORw0KGgo=";
    }

    private String hash(String rawPassword) {
        return BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());
    }

    private Company company(Long id, String nome, String slug) {
        Company company = new Company(nome, slug);
        company.setId(id);
        return company;
    }

    private Guardian guardian(Long id, Company company) {
        Guardian guardian = new Guardian();
        ReflectionTestUtils.setField(guardian, "id", id);
        guardian.setCompany(company);
        guardian.setNome("Carla Responsavel");
        guardian.setEmail("carla@vanrides.dev");
        guardian.setTelefone("51933333333");
        guardian.setSenha("hash");
        return guardian;
    }

    private CompanyAdmin companyAdmin(Long id, Company company) {
        CompanyAdmin admin = new CompanyAdmin();
        ReflectionTestUtils.setField(admin, "id", id);
        admin.setCompany(company);
        admin.setNome("Admin Empresa");
        admin.setEmail("admin@vanrides.dev");
        admin.setTelefone("51944444444");
        admin.setSenha("hash");
        return admin;
    }

    private Passageiro passenger(Long id) {
        Passageiro passageiro = new Passageiro(
                "Ana Passageira",
                "ana@vanrides.dev",
                "51999999999",
                "hash",
                "Rua A",
                "Centro",
                "Igrejinha",
                "RS",
                "95650-000"
        );
        passageiro.setId(id);
        return passageiro;
    }
}
