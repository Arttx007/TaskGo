package com.example.Estrela.Service;

import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.Saque;
import com.example.Estrela.exception.SaldoInsuficienteException;
import com.example.Estrela.repository.PrestadorRepository;
import com.example.Estrela.repository.SaqueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RN03/US-08: saque só é permitido dentro do saldo disponível.
 */
@ExtendWith(MockitoExtension.class)
class SaqueServiceTest {

    @Mock private SaqueRepository saqueRepository;
    @Mock private PrestadorRepository prestadorRepository;

    @InjectMocks
    private SaqueService saqueService;

    private Prestador prestador;

    @BeforeEach
    void setUp() {
        prestador = new Prestador();
        prestador.setIdPrestador(10L);
        prestador.setSaldoDisponivel(new BigDecimal("50.00"));
    }

    @Test
    void permiteSaqueDoValorExatoDoSaldo() {
        when(prestadorRepository.findById(10L)).thenReturn(Optional.of(prestador));
        when(saqueRepository.save(any(Saque.class))).thenAnswer(inv -> inv.getArgument(0));

        Saque saque = saqueService.solicitar(10L, new BigDecimal("50.00"));

        assertThat(prestador.getSaldoDisponivel()).isEqualByComparingTo("0.00");
        assertThat(saque.getValor()).isEqualByComparingTo("50.00");
    }

    @Test
    void permiteSaqueParcialEAbateDoSaldo() {
        when(prestadorRepository.findById(10L)).thenReturn(Optional.of(prestador));
        when(saqueRepository.save(any(Saque.class))).thenAnswer(inv -> inv.getArgument(0));

        saqueService.solicitar(10L, new BigDecimal("20.00"));

        assertThat(prestador.getSaldoDisponivel()).isEqualByComparingTo("30.00");
    }

    @Test
    void rejeitaSaqueAcimaDoSaldoDisponivelSemDebitar() {
        when(prestadorRepository.findById(10L)).thenReturn(Optional.of(prestador));

        assertThatThrownBy(() -> saqueService.solicitar(10L, new BigDecimal("80.00")))
                .isInstanceOf(SaldoInsuficienteException.class);

        assertThat(prestador.getSaldoDisponivel()).isEqualByComparingTo("50.00");
        verify(saqueRepository, never()).save(any());
        verify(prestadorRepository, never()).save(any());
    }
}
