package com.example.Estrela.Controller;

import com.example.Estrela.Entity.Tempo;
import com.example.Estrela.repository.TempoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tempos")
public class TempoController {

    @Autowired
    private TempoRepository repository;

    @PostMapping
    public Tempo criar(@RequestBody Tempo tempo) {
        return repository.save(tempo);
    }

    @GetMapping
    public List<Tempo> listar() {
        return repository.findAll();
    }
}
