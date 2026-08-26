package com.example.Estrela.repository;

import com.example.Estrela.Entity.Tempo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TempoRepository extends JpaRepository<Tempo, Long> {

    Optional<Tempo> findByData(LocalDate data);
}
