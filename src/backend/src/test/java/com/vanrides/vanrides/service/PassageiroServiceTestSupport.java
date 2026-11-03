package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminPassageiroUpdateRequest;
import com.vanrides.vanrides.dto.PublicPassengerRegistrationRequest;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

abstract class PassageiroServiceTestSupport {

    protected static final String BCRYPT_SHAPED_PASSWORD =
            "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u";

    @Mock
    protected PassageiroRepository passageiroRepository;

    @Mock
    protected MotoristaRepository motoristaRepository;

    @Mock
    protected GeocodingService geocodingService;

    @Mock
    protected EmailVerificationService emailVerificationService;

    @Mock
    protected LoginIdentityService loginIdentityService;

    @InjectMocks
    protected PassageiroService passageiroService;

    @BeforeEach
    void setUpIdentityWriteBoundary() {
        ReflectionTestUtils.setField(
                passageiroService,
                "persistencePreparer",
                new PassageiroPersistencePreparer(motoristaRepository, geocodingService)
        );
        lenient().when(loginIdentityService.requireAvailable(anyString()))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).trim().toLowerCase());
        lenient().when(loginIdentityService.executeIdentityWrite(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
    }

    protected PublicPassengerRegistrationRequest publicRequest(Passageiro passageiro) {
        return new PublicPassengerRegistrationRequest(
                passageiro.getNome(),
                passageiro.getEmail(),
                passageiro.getTelefone(),
                passageiro.getSenha(),
                passageiro.getEndereco(),
                passageiro.getBairro(),
                passageiro.getCidade(),
                passageiro.getEstado(),
                passageiro.getCep(),
                passageiro.getLatitude(),
                passageiro.getLongitude(),
                passageiro.getDataNascimento()
        );
    }

    protected AdminPassageiroUpdateRequest adminUpdateRequest(String email, String rawPassword) {
        AdminPassageiroUpdateRequest request = new AdminPassageiroUpdateRequest();
        request.setNome("Passageiro Atualizado");
        request.setEmail(email);
        request.setTelefone("51999990011");
        request.setEndereco("Rua Teste");
        request.setBairro("Centro");
        request.setCidade("Porto Alegre");
        request.setEstado("RS");
        request.setCep("90000-000");
        request.setSenha(rawPassword);
        request.setLatitude(-30.0);
        request.setLongitude(-51.0);
        return request;
    }

    protected Passageiro buildPassageiro() {
        Passageiro passageiro = new Passageiro();
        passageiro.setNome("Passageiro Teste");
        passageiro.setEmail("passageiro.service@vanrides.dev");
        passageiro.setTelefone("51999990011");
        passageiro.setSenha("Secret123");
        passageiro.setEndereco("Rua Teste");
        passageiro.setBairro("Centro");
        passageiro.setCidade("Porto Alegre");
        passageiro.setEstado("RS");
        passageiro.setCep("90000-000");
        return passageiro;
    }
}
