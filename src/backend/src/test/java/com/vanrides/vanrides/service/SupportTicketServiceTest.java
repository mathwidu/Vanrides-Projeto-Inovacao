package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.SupportTicketCreateRequest;
import com.vanrides.vanrides.dto.SupportTicketMessageCreateRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.SupportTicket;
import com.vanrides.vanrides.model.SupportTicketMessage;
import com.vanrides.vanrides.model.SupportTicketStatus;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.SupportTicketMessageRepository;
import com.vanrides.vanrides.repository.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private SupportTicketMessageRepository supportTicketMessageRepository;

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private GuardianRepository guardianRepository;

    @InjectMocks
    private SupportTicketService service;

    @Test
    void shouldCreatePassengerTicketWithInitialAuditMessage() {
        Company company = company(66L);
        Passageiro passageiro = passageiro(21L, company);
        when(passageiroRepository.findByIdAndCompanyId(21L, 66L)).thenReturn(Optional.of(passageiro));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket ticket = invocation.getArgument(0);
            ticket.setId(100L);
            return ticket;
        });
        when(supportTicketMessageRepository.save(any(SupportTicketMessage.class))).thenAnswer(invocation -> {
            SupportTicketMessage message = invocation.getArgument(0);
            message.setId(501L);
            message.setCreatedAt(OffsetDateTime.parse("2026-06-18T12:00:00Z"));
            return message;
        });

        var response = service.createTicket(
                UserRole.PASSENGER,
                21L,
                66L,
                new SupportTicketCreateRequest(
                        "ATRASO",
                        "Van atrasada",
                        "A van ainda nao passou na minha parada."
                )
        );

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.category()).isEqualTo("ATRASO");
        assertThat(response.subject()).isEqualTo("Van atrasada");
        assertThat(response.createdByRole()).isEqualTo("PASSENGER");
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).authorRole()).isEqualTo("PASSENGER");
        assertThat(response.messages().get(0).message()).isEqualTo("A van ainda nao passou na minha parada.");

        ArgumentCaptor<SupportTicket> ticketCaptor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(supportTicketRepository).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getCompany()).isEqualTo(company);
        assertThat(ticketCaptor.getValue().getCreatedByPassenger()).isEqualTo(passageiro);
        assertThat(ticketCaptor.getValue().getStatus()).isEqualTo(SupportTicketStatus.OPEN);
    }

    @Test
    void shouldReplyAsCompanyAdminAndResolveTicket() {
        Company company = company(66L);
        SupportTicket ticket = ticket(100L, company, passageiro(21L, company));
        when(supportTicketRepository.findByIdAndCompanyId(100L, 66L)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supportTicketMessageRepository.save(any(SupportTicketMessage.class))).thenAnswer(invocation -> {
            SupportTicketMessage message = invocation.getArgument(0);
            message.setId(502L);
            message.setCreatedAt(OffsetDateTime.parse("2026-06-18T12:05:00Z"));
            return message;
        });

        var response = service.replyAsCompanyAdmin(
                100L,
                66L,
                90L,
                new SupportTicketMessageCreateRequest(
                        "Estamos falando com o motorista agora.",
                        "RESOLVED"
                )
        );

        assertThat(ticket.getStatus()).isEqualTo(SupportTicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();
        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).authorRole()).isEqualTo("COMPANY_ADMIN");
        assertThat(response.messages().get(0).authorUserId()).isEqualTo(90L);
    }

    @Test
    void shouldRejectDriverTicketCreation() {
        assertThatThrownBy(() -> service.createTicket(
                UserRole.DRIVER,
                7L,
                66L,
                new SupportTicketCreateRequest("ROTA", "Duvida", "Mensagem")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Papel sem permissão");

        verify(supportTicketRepository, never()).save(any(SupportTicket.class));
    }

    private Company company(Long id) {
        Company company = new Company("Empresa " + id, "empresa-" + id);
        company.setId(id);
        return company;
    }

    private Passageiro passageiro(Long id, Company company) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Passageiro " + id);
        passageiro.setEmail("passageiro" + id + "@vanrides.dev");
        passageiro.setTelefone("51999990001");
        passageiro.setCompany(company);
        return passageiro;
    }

    private SupportTicket ticket(Long id, Company company, Passageiro passageiro) {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(id);
        ticket.setCompany(company);
        ticket.setCreatedByPassenger(passageiro);
        ticket.setCreatedByRole(UserRole.PASSENGER);
        ticket.setCreatedByUserId(passageiro.getId());
        ticket.setCategory("ATRASO");
        ticket.setSubject("Van atrasada");
        ticket.setStatus(SupportTicketStatus.OPEN);
        return ticket;
    }
}
