package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminPassageiroDetailResponse;
import com.vanrides.vanrides.dto.AdminPassageiroSummaryResponse;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminPassageiroProfileMappingTest {

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private GuardianPassageiroRepository guardianPassageiroRepository;

    private CompanyAdminPassageiroService service;

    @BeforeEach
    void setUp() {
        service = new CompanyAdminPassageiroService();
        ReflectionTestUtils.setField(service, "passageiroRepository", passageiroRepository);
        ReflectionTestUtils.setField(service, "guardianPassageiroRepository", guardianPassageiroRepository);
    }

    @Test
    void summaryPreservesPassengerAndDriverProfileFields() {
        Passageiro passageiro = passenger(21L, "Aluno Portal", LocalDate.of(2012, 5, 20));
        Motorista motorista = motorista(8L, "Motorista Azul", LocalDate.of(1988, 3, 10));
        passageiro.setMotorista(motorista);

        when(passageiroRepository.findAllByCompanyIdOrderByNomeAsc(66L)).thenReturn(List.of(passageiro));
        when(guardianPassageiroRepository.countByPassageiroIdAndCompanyId(21L, 66L)).thenReturn(2L);

        AdminPassageiroSummaryResponse summary = service.listarPassageiros(66L).get(0);

        assertThat(summary.getDataNascimento()).isEqualTo(LocalDate.of(2012, 5, 20));
        assertThat(summary.getFotoPerfilUrl()).isEqualTo("/uploads/avatars/passageiro-21.png");
        assertThat(summary.getMotorista().getFotoPerfilUrl()).isEqualTo("/uploads/avatars/motorista-8.png");
    }

    @Test
    void detailPreservesPassengerAndGuardianProfileFields() {
        Passageiro passageiro = passenger(22L, "Aluno Detalhe", LocalDate.of(2011, 8, 4));
        Guardian guardian = guardian(77L, "Mae Teste", LocalDate.of(1985, 7, 2));
        GuardianPassageiro link = new GuardianPassageiro();
        link.setGuardian(guardian);
        link.setPassageiro(passageiro);

        when(passageiroRepository.findByIdAndCompanyId(22L, 66L)).thenReturn(Optional.of(passageiro));
        when(guardianPassageiroRepository.findAllWithGuardianByPassageiroIdAndCompanyId(22L, 66L))
                .thenReturn(List.of(link));

        AdminPassageiroDetailResponse detail = service.buscarDetalhe(22L, 66L).orElseThrow();

        assertThat(detail.getDataNascimento()).isEqualTo(LocalDate.of(2011, 8, 4));
        assertThat(detail.getFotoPerfilUrl()).isEqualTo("/uploads/avatars/passageiro-22.png");
        assertThat(detail.getResponsaveis().get(0).getFotoPerfilUrl()).isEqualTo("/uploads/avatars/guardian-77.png");
    }

    private Passageiro passenger(Long id, String nome, LocalDate birthDate) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome(nome);
        passageiro.setEmail("passageiro" + id + "@vanrides.dev");
        passageiro.setTelefone("51999990000");
        passageiro.setEndereco("Rua Teste");
        passageiro.setBairro("Centro");
        passageiro.setCidade("Porto Alegre");
        passageiro.setEstado("RS");
        passageiro.setCep("90000-000");
        passageiro.setDataNascimento(birthDate);
        passageiro.setFotoPerfilUrl("/uploads/avatars/passageiro-" + id + ".png");
        return passageiro;
    }

    private Motorista motorista(Long id, String nome, LocalDate birthDate) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome(nome);
        motorista.setEmail("motorista" + id + "@vanrides.dev");
        motorista.setTelefone("51999990099");
        motorista.setDataNascimento(birthDate);
        motorista.setFotoPerfilUrl("/uploads/avatars/motorista-" + id + ".png");
        return motorista;
    }

    private Guardian guardian(Long id, String nome, LocalDate birthDate) {
        Guardian guardian = new Guardian();
        guardian.setNome(nome);
        guardian.setEmail("guardian" + id + "@vanrides.dev");
        guardian.setTelefone("51999990001");
        guardian.setDataNascimento(birthDate);
        guardian.setFotoPerfilUrl("/uploads/avatars/guardian-" + id + ".png");
        ReflectionTestUtils.setField(guardian, "id", id);
        return guardian;
    }
}
