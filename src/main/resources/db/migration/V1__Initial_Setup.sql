CREATE TABLE questions (
    id UUID PRIMARY KEY,
    auction_id BIGINT NOT NULL,
    seller_id UUID NOT NULL,
    user_id UUID NOT NULL,
    text TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    rejection_reason VARCHAR(50),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE answers (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    text TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    rejection_reason VARCHAR(50),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_answers_questions FOREIGN KEY (question_id) REFERENCES questions(id)
);