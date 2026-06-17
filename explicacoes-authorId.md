# Refactor `userId` → `authorId` + factories de notificação

Material de estudo para a hora de integrar os microserviços. Cobre o **porquê**, o **o que mudou** e o **como isso evita o bug voltar**.

---

## 1. O problema que motivou tudo

### O bug

O dev que estava integrando o Notification Service teve que chumbar um UUID literal no `KafkaReviewConsumer`:

```java
new AnswerApprovedNotification(
    UUID.fromString("bddfe29d-3bd1-47e5-bf4b-03a50c65d534"),  // !!!! AQUI !!!!!
    ...
)
```

Esse UUID **não vem de lugar nenhum do sistema** — `grep` confirma que ele só aparecia nesse arquivo. Era um id de usuário do banco do user-service local dele, plantado pra destravar o teste de integração. O commit `639c5c7` ("exibindo erro ao publicar answerApprovedTopic") trocou `question.getUserId()` por esse literal.

### A causa-raiz: ambiguidade no nome `userId`

A coluna `user_id` existia em duas tabelas com significados diferentes:

| Tabela | Coluna | Quem era |
|---|---|---|
| `questions` | `user_id` | quem perguntou (= comprador) |
| `answers` | `user_id` | quem respondeu (= **vendedor**, sempre) |

Mesmo nome, papel diferente. Quando você lê `question.getUserId()` no meio do código, precisa parar e pensar "esse user é o quê mesmo?". Pior: na hora de publicar `AnswerApprovedNotification`, o destinatário (`recipientId`) é o **autor da pergunta** (`question.getAuthorId()`) — não o autor da resposta. Fácil de errar, e o Rodrigo errou.

---

## 2. O modelo mental (decora isso)

Cenário concreto:

- **Maria** (`M-001`) — interessada num anúncio.
- **João** (`J-001`) — vendedor, dono do anúncio.
- Anúncio = `auction 42`.

Maria pergunta. João responde. As duas tabelas ficam:

**`questions`**
| coluna | valor | papel |
|---|---|---|
| `author_id` | `M-001` | Maria (escreveu a pergunta) |
| `seller_id` | `J-001` | João (dono do anúncio) |

**`answers`**
| coluna | valor | papel |
|---|---|---|
| `author_id` | `J-001` | João (escreveu a resposta) |

E quem **recebe** cada notificação:

| Evento | recipientId | Por quê |
|---|---|---|
| Pergunta aprovada | João (vendedor) | "alguém te fez uma pergunta no seu anúncio" |
| **Resposta aprovada** | **Maria (compradora)** | "o vendedor respondeu sua pergunta" ← era aqui que dava o bug |
| Pergunta rejeitada | Maria (autora da pergunta) | "sua pergunta foi rejeitada" |
| Resposta rejeitada | João (autor da resposta) | "sua resposta foi rejeitada" |

Sempre que você for codar algo nessa região: **reconstrua essa tabela na cabeça**.

---

## 3. As alterações nesta PR

### 3.1. Migration Flyway — V5 (nova)

`src/main/resources/db/migration/V5__Rename_User_Id_To_Author_Id.sql`

```sql
ALTER TABLE questions RENAME COLUMN user_id TO author_id;
ALTER TABLE answers   RENAME COLUMN user_id TO author_id;
```

`RENAME COLUMN` no Postgres preserva todo o dado existente — não há backfill nem downtime de leitura. A migration roda automaticamente no boot da app (`spring.flyway.enabled=true`).

### 3.2. Entidades — `Question.java`, `Answer.java`

```diff
- @Column(nullable = false)
- private UUID userId;
+ @Column(nullable = false)
+ private UUID authorId;
```

Como elas usam Lombok (`@Getter @Setter @Builder`), todos os getters/setters/builder se renomeiam automaticamente:
- `getUserId()` → `getAuthorId()`
- `.userId(...)` no builder → `.authorId(...)`

### 3.3. DTOs de resposta HTTP — ⚠️ quebra de contrato

`QuestionResponse` e `AnswerResponse` (records expostos pelos endpoints `/api/qa/...`):

```diff
public record QuestionResponse(
        Long id,
        Long auctionId,
-       UUID userId,
+       UUID authorId,
        ...
)
```

**Isso muda o JSON devolvido pelo serviço.** Quem consome via HTTP (frontend ou outro microserviço) vai receber:

```json
{ "id": 1, "auctionId": 42, "authorId": "M-001", ... }
```

…em vez de `"userId"`. Avise quem consome a API antes de subir em ambiente compartilhado.

### 3.4. Services — parâmetros NÃO mudaram

`QuestionService.createQuestion(Long auctionId, UUID userId, ...)` continua recebendo `userId` como parâmetro porque ele vem do header `X-User-Id` do gateway — ali, "userId" é só "o caller". Só quando esse valor é gravado na entidade ele vira `authorId`:

```diff
  Question question = Question.builder()
          .auctionId(auctionId)
          .sellerId(auctionResponse.sellerId())
-         .userId(userId)
+         .authorId(userId)
```

A regra mental: **`userId` na fronteira HTTP (header / parâmetro do controller / parâmetro do service)**; **`authorId` na entidade**.

### 3.5. Factory methods nos events de notificação

O coração da blindagem contra o bug. Cada record de notificação agora encapsula sua própria regra de "quem é o destinatário":

```java
// QuestionApprovedNotification
public static QuestionApprovedNotification forSellerOf(Question question, UUID correlationId) {
    return new QuestionApprovedNotification(
            question.getSellerId(),       // recipient = vendedor
            question.getAuctionId(),
            ...
    );
}

// AnswerApprovedNotification
public static AnswerApprovedNotification forBuyerOf(Answer answer, UUID correlationId) {
    var question = answer.getQuestion();
    return new AnswerApprovedNotification(
            question.getAuthorId(),       // recipient = autor da pergunta (comprador)
            ...
    );
}

// QuestionRejectedNotification
public static QuestionRejectedNotification forAuthorOf(Question question, String reason, UUID correlationId) {
    return new QuestionRejectedNotification(
            question.getAuthorId(),       // recipient = autor da pergunta
            ...
    );
}

// AnswerRejectedNotification
public static AnswerRejectedNotification forAuthorOf(Answer answer, String reason, UUID correlationId) {
    var question = answer.getQuestion();
    return new AnswerRejectedNotification(
            answer.getAuthorId(),         // recipient = autor da resposta
            ...
    );
}
```

O `Instant.now()` também migrou pra dentro da factory — quem chama não precisa mais lembrar de passar.

### 3.6. `KafkaReviewConsumer.java` — comparativo antes/depois

**Antes** (8 argumentos posicionais, fácil errar qual UUID vai onde):

```java
outboxEventPublisher.publish(answerApprovedTopic, new AnswerApprovedNotification(
        UUID.fromString("bddfe29d-3bd1-47e5-bf4b-03a50c65d534"),  // !!!! AQUI !!!!!
        question.getAuctionId(),
        question.getId(),
        answer.getId(),
        question.getText(),
        answer.getText(),
        Instant.now(),
        event.correlationId()
));
```

**Depois** (1 argumento de domínio + correlationId — sem UUID solto pra escolher):

```java
outboxEventPublisher.publish(answerApprovedTopic,
        AnswerApprovedNotification.forBuyerOf(answer, event.correlationId()));
```

Também removi o `System.out.println("")` solto que tinha sido deixado no método `consumeApproved` no mesmo commit do bug.

---

## 4. Glossário definitivo dos IDs

Quando você for integrar com os outros microserviços e bater nesses nomes, use esta tabela como referência:

| Nome | Onde aparece | O que é |
|---|---|---|
| `userId` | Header `X-User-Id`, parâmetros de controller/service | "o caller HTTP" — quem está fazendo a requisição agora |
| `authorId` | Colunas `questions.author_id`, `answers.author_id`, campos das entidades, DTOs de resposta | quem escreveu **aquele conteúdo específico** |
| `sellerId` | `Question.sellerId` (vem do `auctionService`), `AuctionResponse.sellerId()` | dono do anúncio |
| `recipientId` | Records `*Notification` publicados pro Notification Service | destinatário da notificação — quem vai receber o e-mail/push |

Em `answers`, **o `authorId` é sempre igual ao `sellerId` da pergunta** — porque `AnswerService.createAnswer` valida `userId == question.sellerId` antes de criar. Mas gravamos `authorId` mesmo assim porque essa redundância deixa cada tabela auto-explicativa: você não precisa fazer JOIN só pra saber quem criou.

---

## 5. Por que o bug não volta mais

A invariante "destinatário de `AnswerApproved` é o autor da pergunta, não o autor da resposta" antes morava na cabeça de quem programava o consumer. Agora ela mora dentro de `AnswerApprovedNotification.forBuyerOf(Answer)`:

- Quem chama só passa o `Answer`. Não há lacuna de UUID pra preencher errado.
- Se alguém tentar `new AnswerApprovedNotification(...)` direto, vai precisar passar 8 argumentos posicionais — o que provavelmente vai parar no code review porque o factory `forBuyerOf` está logo ali, óbvio.
- Os 4 factories (`forSellerOf`, `forBuyerOf`, `forAuthorOf` × 2) descrevem o **papel do destinatário em linguagem de domínio**. Você lê `forBuyerOf(answer, ...)` e sabe imediatamente que o e-mail vai pro comprador.

---

## 6. Lista de arquivos tocados

**Novos:**
- `src/main/resources/db/migration/V5__Rename_User_Id_To_Author_Id.sql`

**Modificados (entidades + Java):**
- `src/main/java/br/com/leilao/domain/entity/Question.java`
- `src/main/java/br/com/leilao/domain/entity/Answer.java`
- `src/main/java/br/com/leilao/dto/response/QuestionResponse.java`
- `src/main/java/br/com/leilao/dto/response/AnswerResponse.java`
- `src/main/java/br/com/leilao/service/mapper/QuestionMapper.java`
- `src/main/java/br/com/leilao/service/mapper/AnswerMapper.java`
- `src/main/java/br/com/leilao/service/QuestionService.java`
- `src/main/java/br/com/leilao/service/AnswerService.java`
- `src/main/java/br/com/leilao/integration/kafka/events/QuestionApprovedNotification.java`
- `src/main/java/br/com/leilao/integration/kafka/events/AnswerApprovedNotification.java`
- `src/main/java/br/com/leilao/integration/kafka/events/QuestionRejectedNotification.java`
- `src/main/java/br/com/leilao/integration/kafka/events/AnswerRejectedNotification.java`
- `src/main/java/br/com/leilao/integration/kafka/consumer/KafkaReviewConsumer.java`

**Testes ajustados:**
- `src/test/java/br/com/leilao/service/QuestionServiceTest.java`
- `src/test/java/br/com/leilao/service/AnswerServiceTest.java`
- `src/test/java/br/com/leilao/integration/kafka/consumer/KafkaReviewConsumerTest.java`

---

## 7. Checklist para subir em integração

- [ ] Avisar quem consome `/api/qa/...` via HTTP que o campo `userId` virou `authorId` no JSON de resposta.
- [ ] Confirmar que o Notification Service consome `qa.answer.approved` esperando `recipientId` = autor da pergunta (e não o vendedor). Se ele estava resolvendo o e-mail pelo `bddfe29d-...` chumbado, agora vai chegar o id real — precisa que esse id exista no banco do user-service.
- [ ] Garantir que o `X-User-Id` enviado pelo cliente no `POST /api/qa/auctions/{id}/questions` corresponda a um usuário válido nos demais serviços. Era exatamente isso que faltava na integração anterior.
