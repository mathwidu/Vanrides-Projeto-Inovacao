package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.MotoristaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

@Component
class PassageiroPersistencePreparer {

    private static final Pattern STORED_BCRYPT_PATTERN =
            Pattern.compile("^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$");

    private static final Logger log = LoggerFactory.getLogger(PassageiroPersistencePreparer.class);

    private final MotoristaRepository motoristaRepository;
    private final GeocodingService geocodingService;

    PassageiroPersistencePreparer(
            MotoristaRepository motoristaRepository,
            GeocodingService geocodingService) {
        this.motoristaRepository = motoristaRepository;
        this.geocodingService = geocodingService;
    }

    void prepareNew(Passageiro passageiro, String rawPassword) {
        hashRawPassword(passageiro, rawPassword);
        preparePassengerFields(passageiro);
    }

    void prepareExisting(Passageiro passageiro, String rawPassword) {
        if (rawPassword == null) {
            if (passageiro.getSenha() == null
                    || !STORED_BCRYPT_PATTERN.matcher(passageiro.getSenha()).matches()) {
                throw new IllegalStateException(
                        "Credencial armazenada incompatível com a política de autenticação."
                );
            }
        } else {
            hashRawPassword(passageiro, rawPassword);
        }
        preparePassengerFields(passageiro);
    }

    private void hashRawPassword(Passageiro passageiro, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha é obrigatória.");
        }
        passageiro.setSenha(BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray()));
    }

    private void preparePassengerFields(Passageiro passageiro) {
        if (passageiro.getId() != null && (passageiro.getSenha() == null
                || !STORED_BCRYPT_PATTERN.matcher(passageiro.getSenha()).matches())) {
            throw new IllegalStateException("Credencial armazenada incompatível com a política de autenticação.");
        }

        Motorista motoristaPersistido = resolveMotorista(passageiro.getMotorista());
        if (motoristaPersistido != null) {
            passageiro.setMotorista(motoristaPersistido);
        }

        if (passageiro.getCompany() == null
                && motoristaPersistido != null
                && motoristaPersistido.getCompany() != null) {
            passageiro.setCompany(motoristaPersistido.getCompany());
        }

        if (passageiro.getLatitude() == null || passageiro.getLongitude() == null) {
            String enderecoCompleto = passageiro.getEndereco() + ", "
                    + passageiro.getBairro() + ", "
                    + passageiro.getCidade() + ", "
                    + passageiro.getEstado() + ", "
                    + passageiro.getCep();

            try {
                GeocodingService.Coordenadas coordenadas = geocodingService.buscarCoordenadas(enderecoCompleto);
                if (coordenadas != null) {
                    passageiro.setLatitude(coordenadas.getLatitude());
                    passageiro.setLongitude(coordenadas.getLongitude());
                }
            } catch (Exception e) {
                log.warn("Erro ao buscar coordenadas para passageiro: {}", e.getMessage());
            }
        }

        applyBirthDatePolicy(passageiro);
    }

    private Motorista resolveMotorista(Motorista motorista) {
        if (motorista == null || motorista.getId() == null) {
            return motorista;
        }

        return motoristaRepository.findById(motorista.getId())
                .orElse(motorista);
    }

    private void applyBirthDatePolicy(Passageiro passageiro) {
        LocalDate dataNascimento = passageiro.getDataNascimento();
        if (dataNascimento == null) {
            return;
        }
        if (dataNascimento.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de nascimento não pode ser futura.");
        }
        passageiro.setMenorDeIdade(Period.between(dataNascimento, LocalDate.now()).getYears() < 18);
    }
}
