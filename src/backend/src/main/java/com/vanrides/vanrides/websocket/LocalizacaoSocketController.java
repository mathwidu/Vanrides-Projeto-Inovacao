package com.vanrides.vanrides.websocket;

import com.vanrides.vanrides.dto.LocalizacaoDTO;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.LocalizacaoMotoristaRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.service.ViagemRealtimePublisherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

import static com.vanrides.vanrides.config.StompTenantChannelInterceptor.AUTHENTICATED_USER_ATTR;

@Controller
public class LocalizacaoSocketController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocalizacaoSocketController.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private LocalizacaoMotoristaRepository localizacaoMotoristaRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private ViagemRealtimePublisherService viagemRealtimePublisherService;

    @MessageMapping("/enviarLocalizacao")
    public void receberLocalizacao(LocalizacaoDTO localizacao, SimpMessageHeaderAccessor headerAccessor) {
        log.debug("Recebendo localizacao via WebSocket: motoristaId={}, latitude={}, longitude={}",
                localizacao.getMotoristaId(), localizacao.getLatitude(), localizacao.getLongitude());

        AuthenticatedUser authenticatedUser = getAuthenticatedUser(headerAccessor);
        if (!authenticatedUser.isDriver()) {
            throw new RuntimeException("Apenas motoristas podem compartilhar localização");
        }

        if (!authenticatedUser.getUserId().equals(localizacao.getMotoristaId())) {
            throw new RuntimeException("Motorista autenticado não pode enviar localização para outro motorista");
        }

        Motorista motorista = motoristaRepository.findByIdAndCompanyId(localizacao.getMotoristaId(), authenticatedUser.getCompanyId())
                .orElseThrow(() -> {
                    log.debug("Motorista nao encontrado para localizacao WebSocket: motoristaId={}, companyId={}",
                            localizacao.getMotoristaId(), authenticatedUser.getCompanyId());
                    return new RuntimeException("Motorista não encontrado com ID: " + localizacao.getMotoristaId());
                });

        log.debug("Motorista encontrado para localizacao WebSocket: motoristaId={}", motorista.getId());

        LocalizacaoMotorista entidade = new LocalizacaoMotorista();
        entidade.setLatitude(localizacao.getLatitude());
        entidade.setLongitude(localizacao.getLongitude());
        entidade.setDataHora(LocalDateTime.now());
        entidade.setMotorista(motorista);

        localizacaoMotoristaRepository.save(entidade);
        viagemRealtimePublisherService.publishTodayTripSnapshotForMotorista(motorista.getId(), authenticatedUser.getCompanyId());

        log.debug("Localizacao salva via WebSocket para motoristaId={}", motorista.getId());

        String destination = "/topic/localizacao/" + authenticatedUser.getCompanyId();
        messagingTemplate.convertAndSend(destination, localizacao);

        log.debug("Localizacao enviada no topico {}", destination);
    }

    private AuthenticatedUser getAuthenticatedUser(SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getSessionAttributes() == null) {
            throw new RuntimeException("Sessão WebSocket sem autenticação");
        }

        Object user = headerAccessor.getSessionAttributes().get(AUTHENTICATED_USER_ATTR);
        if (user instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }

        throw new RuntimeException("Usuário WebSocket não autenticado");
    }

}
