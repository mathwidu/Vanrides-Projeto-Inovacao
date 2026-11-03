package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.Presenca;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.PresencaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresencaServiceTest {

    @Mock
    private PresencaRepository presencaRepository;

    @Mock
    private PassageiroRepository passageiroRepository;

    @InjectMocks
    private PresencaService presencaService;

    @Test
    void shouldSavePresencaWhenPassageiroBelongsToCompany() {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(10L);

        Presenca presenca = new Presenca();
        presenca.setPassageiro(new Passageiro());
        presenca.getPassageiro().setId(10L);
        presenca.setDataPresenca(LocalDate.now());
        presenca.setConfirmado(true);

        when(passageiroRepository.findByIdAndCompanyId(10L, 55L)).thenReturn(Optional.of(passageiro));
        when(presencaRepository.save(any(Presenca.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Presenca salva = presencaService.salvarPresenca(presenca, 55L);

        assertThat(salva.getPassageiro()).isEqualTo(passageiro);
    }

    @Test
    void shouldRejectPresencaWhenPassageiroIsOutsideCompanyScope() {
        Presenca presenca = new Presenca();
        presenca.setPassageiro(new Passageiro());
        presenca.getPassageiro().setId(99L);
        presenca.setDataPresenca(LocalDate.now());

        when(passageiroRepository.findByIdAndCompanyId(99L, 55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> presencaService.salvarPresenca(presenca, 55L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Passageiro não encontrado na empresa atual.");
    }
}
