package com.vanrides.vanrides.websocket;

import com.vanrides.vanrides.config.StompTenantChannelInterceptor;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.LocalizacaoDTO;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.LocalizacaoMotoristaRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.service.ViagemRealtimePublisherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalizacaoSocketControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LocalizacaoMotoristaRepository localizacaoMotoristaRepository;

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private ViagemRealtimePublisherService viagemRealtimePublisherService;

    @InjectMocks
    private LocalizacaoSocketController controller;

    @Test
    void shouldPersistAndBroadcastWhenMotoristaExists() {
        Motorista motorista = new Motorista();
        motorista.setId(10L);
        motorista.setNome("Motorista WS");
        Company company = new Company("Empresa WS", "empresa-ws");
        company.setId(88L);
        motorista.setCompany(company);
        when(motoristaRepository.findByIdAndCompanyId(10L, 88L)).thenReturn(Optional.of(motorista));

        LocalizacaoDTO payload = new LocalizacaoDTO(-30.0, -51.0, 10L);

        controller.receberLocalizacao(payload, authenticatedHeaders(10L, "MOTORISTA", 88L));

        ArgumentCaptor<LocalizacaoMotorista> captor = ArgumentCaptor.forClass(LocalizacaoMotorista.class);
        verify(localizacaoMotoristaRepository).save(captor.capture());
        LocalizacaoMotorista salva = captor.getValue();

        assertThat(salva.getLatitude()).isEqualTo(-30.0);
        assertThat(salva.getLongitude()).isEqualTo(-51.0);
        assertThat(salva.getMotorista().getId()).isEqualTo(10L);
        assertThat(salva.getDataHora()).isNotNull();

        verify(messagingTemplate).convertAndSend("/topic/localizacao/88", payload);
        verify(viagemRealtimePublisherService).publishTodayTripSnapshotForMotorista(10L, 88L);
    }

    @Test
    void shouldFailAndNotBroadcastWhenMotoristaDoesNotExist() {
        when(motoristaRepository.findByIdAndCompanyId(99L, 88L)).thenReturn(Optional.empty());
        LocalizacaoDTO payload = new LocalizacaoDTO(-30.0, -51.0, 99L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> controller.receberLocalizacao(payload, authenticatedHeaders(99L, "MOTORISTA", 88L)));
        assertThat(ex.getMessage()).contains("Motorista não encontrado");

        verify(localizacaoMotoristaRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void shouldRejectWhenAuthenticatedMotoristaTriesToSendForAnotherMotorista() {
        LocalizacaoDTO payload = new LocalizacaoDTO(-30.0, -51.0, 99L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> controller.receberLocalizacao(payload, authenticatedHeaders(10L, "MOTORISTA", 88L)));

        assertThat(ex.getMessage()).contains("não pode enviar localização para outro motorista");
        verifyNoInteractions(motoristaRepository, localizacaoMotoristaRepository, messagingTemplate);
    }

    @Test
    void shouldRejectWhenNonMotoristaTriesToSendLocation() {
        LocalizacaoDTO payload = new LocalizacaoDTO(-30.0, -51.0, 10L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> controller.receberLocalizacao(payload, authenticatedHeaders(77L, "PASSAGEIRO", 88L)));

        assertThat(ex.getMessage()).contains("Apenas motoristas");
        verifyNoInteractions(motoristaRepository, localizacaoMotoristaRepository, messagingTemplate);
    }

    private SimpMessageHeaderAccessor authenticatedHeaders(Long userId, String role, Long companyId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put(
                StompTenantChannelInterceptor.AUTHENTICATED_USER_ATTR,
                new AuthenticatedUser(userId, role, "user@vanrides.dev", "Usuário", null, companyId)
        );
        return accessor;
    }
}
