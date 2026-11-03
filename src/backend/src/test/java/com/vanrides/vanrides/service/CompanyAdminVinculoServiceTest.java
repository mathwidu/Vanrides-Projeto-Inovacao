package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminResponsavelPassageiroLinkResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminVinculoServiceTest {

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private GuardianRepository guardianRepository;

    @Mock
    private GuardianPassageiroRepository guardianPassageiroRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private PassageiroService passageiroService;

    @InjectMocks
    private CompanyAdminVinculoService companyAdminVinculoService;

    @Test
    void shouldListPassengerDriverLinks() {
        Passageiro passageiro = passageiro(21L, "Aluno Um");
        passageiro.setMotorista(motorista(8L, "Motorista Azul"));
        when(passageiroRepository.findAllByCompanyIdOrderByNomeAsc(66L)).thenReturn(List.of(passageiro));

        var result = companyAdminVinculoService.listarVinculosPassageiroMotorista(66L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPassageiroId()).isEqualTo(21L);
        assertThat(result.get(0).getMotorista().getId()).isEqualTo(8L);
    }

    @Test
    void shouldRemovePassengerDriverLink() {
        companyAdminVinculoService.removerVinculoPassageiroMotorista(22L, 66L);

        verify(passageiroService).removerMotorista(22L, 66L);
    }

    @Test
    void shouldListGuardianPassengerLinks() {
        Guardian guardian = guardian(71L, "Mae Portal");
        Passageiro passageiro = passageiro(23L, "Aluno Tres");
        passageiro.setMotorista(motorista(10L, "Motorista Roxo"));
        GuardianPassageiro link = new GuardianPassageiro();
        link.setGuardian(guardian);
        link.setPassageiro(passageiro);

        when(guardianPassageiroRepository.findAllWithGuardianAndPassageiroByCompanyId(66L)).thenReturn(List.of(link));

        var result = companyAdminVinculoService.listarVinculosResponsavelPassageiro(66L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getResponsavel().getId()).isEqualTo(71L);
        assertThat(result.get(0).getPassageiro().getId()).isEqualTo(23L);
    }

    @Test
    void shouldCreateGuardianPassengerLink() {
        Guardian guardian = guardian(72L, "Pai Novo");
        Passageiro passageiro = passageiro(24L, "Aluno Quatro");
        Company company = new Company("VanRides Default", "default");
        company.setId(66L);

        when(guardianRepository.findByIdAndCompanyId(72L, 66L)).thenReturn(Optional.of(guardian));
        when(passageiroRepository.findByIdAndCompanyId(24L, 66L)).thenReturn(Optional.of(passageiro));
        when(guardianPassageiroRepository.existsByGuardianIdAndPassageiroIdAndCompanyId(72L, 24L, 66L)).thenReturn(false);
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(guardianPassageiroRepository.save(any(GuardianPassageiro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminResponsavelPassageiroLinkResponse response = companyAdminVinculoService.criarVinculoResponsavelPassageiro(72L, 24L, 66L);

        assertThat(response.getResponsavel().getId()).isEqualTo(72L);
        assertThat(response.getPassageiro().getId()).isEqualTo(24L);
    }

    @Test
    void shouldRejectDuplicateGuardianPassengerLink() {
        Guardian guardian = guardian(73L, "Mae Duplicada");
        Passageiro passageiro = passageiro(25L, "Aluno Cinco");
        when(guardianRepository.findByIdAndCompanyId(73L, 66L)).thenReturn(Optional.of(guardian));
        when(passageiroRepository.findByIdAndCompanyId(25L, 66L)).thenReturn(Optional.of(passageiro));
        when(guardianPassageiroRepository.existsByGuardianIdAndPassageiroIdAndCompanyId(73L, 25L, 66L)).thenReturn(true);

        assertThatThrownBy(() -> companyAdminVinculoService.criarVinculoResponsavelPassageiro(73L, 25L, 66L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("já existe");
    }

    @Test
    void shouldRemoveGuardianPassengerLink() {
        GuardianPassageiro link = new GuardianPassageiro();
        when(guardianPassageiroRepository.findByGuardianIdAndPassageiroIdAndCompanyId(72L, 24L, 66L)).thenReturn(Optional.of(link));

        companyAdminVinculoService.removerVinculoResponsavelPassageiro(72L, 24L, 66L);

        verify(guardianPassageiroRepository).delete(link);
    }

    private Passageiro passageiro(Long id, String nome) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome(nome);
        passageiro.setEmail(nome.toLowerCase().replace(" ", ".") + "@vanrides.dev");
        passageiro.setTelefone("51999990010");
        return passageiro;
    }

    private Motorista motorista(Long id, String nome) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome(nome);
        motorista.setEmail(nome.toLowerCase().replace(" ", ".") + "@vanrides.dev");
        motorista.setTelefone("51999990099");
        return motorista;
    }

    private Guardian guardian(Long id, String nome) {
        Guardian guardian = new Guardian();
        guardian.setNome(nome);
        guardian.setEmail(nome.toLowerCase().replace(" ", ".") + "@vanrides.dev");
        guardian.setTelefone("51999990000");
        setId(guardian, id);
        return guardian;
    }

    private void setId(Guardian guardian, Long id) {
        try {
            var field = Guardian.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(guardian, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
