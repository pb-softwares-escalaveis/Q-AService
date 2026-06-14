-- Unifica a geração de IDs de `questions` e `answers` em uma ÚNICA sequência,
-- garantindo IDs globalmente únicos entre as duas tabelas.
--
-- Motivo: o evento de volta do Review Service ("reviews.qa.approved" / "reviews.qa.rejected")
-- carrega apenas `messageId` (Long). Com sequências IDENTITY separadas, uma Question e uma
-- Answer podiam ter o MESMO id (ambas começavam em 1), fazendo o consumer aplicar a
-- aprovação/rejeição — e a notificação — ao conteúdo errado. Com a sequência compartilhada,
-- o lookup "acha question, senão answer" passa a ser inequívoco.

-- 1) Remove a identidade das colunas (passam a usar o DEFAULT da sequência compartilhada).
ALTER TABLE questions ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE answers   ALTER COLUMN id DROP IDENTITY IF EXISTS;

-- 2) Cria a sequência compartilhada.
CREATE SEQUENCE IF NOT EXISTS qa_content_id_seq AS BIGINT INCREMENT BY 1 START WITH 1;

-- 3) Posiciona a sequência à frente de qualquer id já existente nas duas tabelas.
SELECT setval(
    'qa_content_id_seq',
    GREATEST(
        (SELECT COALESCE(MAX(id), 0) FROM questions),
        (SELECT COALESCE(MAX(id), 0) FROM answers),
        0
    ) + 1,
    false
);

-- 4) Aponta o DEFAULT das duas tabelas para a sequência compartilhada.
ALTER TABLE questions ALTER COLUMN id SET DEFAULT nextval('qa_content_id_seq');
ALTER TABLE answers   ALTER COLUMN id SET DEFAULT nextval('qa_content_id_seq');
