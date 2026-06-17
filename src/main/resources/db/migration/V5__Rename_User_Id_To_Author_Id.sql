-- Renomeia `user_id` para `author_id` em `questions` e `answers`.
--
-- Motivo: `user_id` significava coisas diferentes nas duas tabelas
-- (em `questions` é o autor da pergunta = comprador; em `answers` é o autor
-- da resposta = vendedor). Já houve um bug por causa dessa ambiguidade
-- (commit 639c5c7). `author_id` deixa o papel explícito em ambas.

ALTER TABLE questions RENAME COLUMN user_id TO author_id;
ALTER TABLE answers   RENAME COLUMN user_id TO author_id;
