package com.example.Estrela.repository;

import com.example.Estrela.Entity.ParametroNegocio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParametroNegocioRepository extends JpaRepository<ParametroNegocio, String> {
}
