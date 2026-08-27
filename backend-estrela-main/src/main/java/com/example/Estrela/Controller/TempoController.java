package com.example.Estrela.Controller;

import com.example.Estrela.Entity.Tempo;
import com.example.Estrela.repository.TempoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tempos")
public class TempoController {

    private final TempoRepository repository;

    public TempoController(TempoRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public Tempo criar(@RequestBody Tempo tempo) {
        return repository.save(tempo);
    }

    @GetMapping
    public List<Tempo> listar() {
        return repository.findAll();
    }
}
