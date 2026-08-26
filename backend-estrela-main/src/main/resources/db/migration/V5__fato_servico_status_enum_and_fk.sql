-- status já era VARCHAR; a coluna não muda de tipo, só passa a ser tratada como enum (StatusSolicitacao)
-- do lado da aplicação. Migra os valores livres existentes para o vocabulário fechado de RN02.
UPDATE fato_servicos SET status = 'ACEITO' WHERE status = 'AGENDADO';
-- CONCLUIDO e CANCELADO já batem com os nomes do enum, nenhuma outra conversão necessária.

ALTER TABLE fato_servicos
    ADD COLUMN id_servico_ofertado BIGINT REFERENCES servico_ofertado(id),
    ADD COLUMN comentario_avaliacao VARCHAR(1000);
