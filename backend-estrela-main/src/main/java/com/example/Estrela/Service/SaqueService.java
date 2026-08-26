package com.example.Estrela.Service;

import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.Saque;
import com.example.Estrela.Entity.StatusSaque;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.exception.SaldoInsuficienteException;
import com.example.Estrela.repository.PrestadorRepository;
import com.example.Estrela.repository.SaqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Saque Pix do saldo disponível do prestador (RN03, US-08). O repasse em si é simulado como
 * instantâneo — não há integração real com Pix — mas a validação de saldo e o débito são reais.
 */
@Service
public class SaqueService {

    private final SaqueRepository saqueRepository;
    private final PrestadorRepository prestadorRepository;

    public SaqueService(SaqueRepository saqueRepository, PrestadorRepository prestadorRepository) {
        this.saqueRepository = saqueRepository;
        this.prestadorRepository = prestadorRepository;
    }

    /**
     * @throws RecursoNaoEncontradoException se o prestador não existir
     * @throws SaldoInsuficienteException    se o valor solicitado exceder o saldo disponível
     */
    @Transactional
    public Saque solicitar(Long prestadorId, BigDecimal valor) {
        Prestador prestador = prestadorRepository.findById(prestadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Prestador não encontrado"));

        if (valor.compareTo(prestador.getSaldoDisponivel()) > 0) {
            throw new SaldoInsuficienteException("Valor solicitado excede o saldo disponível");
        }

        prestador.setSaldoDisponivel(prestador.getSaldoDisponivel().subtract(valor));
        prestadorRepository.save(prestador);

        Saque saque = new Saque();
        saque.setPrestador(prestador);
        saque.setValor(valor);
        saque.setChavePixDestino(prestador.getChavePix());
        saque.setStatus(StatusSaque.PROCESSADO);
        saque.setCriadoEm(LocalDateTime.now());

        return saqueRepository.save(saque);
    }
}
