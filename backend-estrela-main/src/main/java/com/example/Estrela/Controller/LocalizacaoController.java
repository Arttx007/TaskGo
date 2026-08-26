package com.example.Estrela.Controller;

import com.example.Estrela.Entity.Localizacao;
import com.example.Estrela.repository.LocalizacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/localizacoes")
public class LocalizacaoController {

    @Autowired
    private LocalizacaoRepository repository;

    @PostMapping
    public Localizacao criar(@RequestBody Localizacao localizacao) {
        return repository.save(localizacao);
    }

    @GetMapping
    public List<Localizacao> listar() {
        return repository.findAll();
    }
}
