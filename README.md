# QA Service

Microserviço responsável por gerenciar o sistema de Perguntas e Respostas (Q&A) da plataforma de leilões.

## Sumário
1. [Visão Geral](#-visão-geral)
2. [Modelo de Domínio](#-modelo-de-domínio)
3. [Fluxo de Moderação](#-fluxo-de-moderação)
4. [Arquitetura e Padrões](#-arquitetura-e-padrões)
5. [Tecnologias](#-tecnologias)
6. [Estrutura do Projeto](#-estrutura-do-projeto)
7. [Integrações](#-integrações)
   - [Síncronas (OpenFeign)](#síncronas-openfeign)
   - [Assíncronas (Kafka)](#assíncronas-kafka)
8. [Configurações e Profiles](#-configurações-e-profiles)
9. [Endpoints (API Reference)](#-endpoints-api-reference)
10. [Execução do Projeto](#-execução-do-projeto)
11. [Pontos de Atenção / Pendências](#-pontos-de-atenção--pendências)

---

## Visão Geral
O **QA-Service** é o domínio central para comunicação entre potenciais compradores e vendedores. Ele gerencia perguntas sobre leilões em andamento e as respectivas respostas, com um fluxo de **moderação assíncrona** (Review) para garantir conformidade do conteúdo.

Porta padrão: **`8098`**.

## Modelo de Domínio

| Conceito | Quem é | Onde mora |
|---|---|---|
| `auctionId` | Identificador do leilão | `Question.auctionId` (Long, vem do `auctionService`) |
| `sellerId` | Dono do anúncio (= vendedor) | `Question.sellerId` (cacheado no momento da criação) |
| `authorId` (em `questions`) | Quem fez a pergunta (comprador) | `Question.authorId` |
| `authorId` (em `answers`) | Quem respondeu — sempre o vendedor | `Answer.authorId` (= `Question.sellerId`) |
| `userId` (parâmetro de service / header) | Caller HTTP autenticado | Header `X-User-Id` |

**`ContentStatus`** (`PENDING_ANALYSIS → ACTIVE | REJECTED | DELETED`)
- `PENDING_ANALYSIS`: aguardando review.
- `ACTIVE`: aprovado e visível publicamente.
- `REJECTED`: bloqueado pela moderação (com `rejectionReason`).
- `DELETED`: soft delete pelo autor. Deletar uma pergunta cascateia a resposta para `DELETED`.

> **Sequência de ID compartilhada (V4):** `questions` e `answers` compartilham `qa_content_id_seq`. Isso é proposital — o `MessageReviewApproved/Rejected` do review-service só carrega `messageId` (Long), e o consumer faz "achou em `questions`, senão em `answers`". Sem sequência única as duas tabelas podiam ter IDs colidindo.

## Fluxo de Moderação

```
1. POST /auctions/{id}/questions
        ↓
2. QuestionService:
   - chama auctionService (Feign) → pega sellerId
   - salva Question(PENDING_ANALYSIS) no Postgres
   - grava MessageCreatedPendingReview no outbox_events (mesma TX)
        ↓
3. OutboxProcessor (@Scheduled fixedDelay=10s, batch 50, status=PENDING)
   - publica em "qa.review.created-pending" com key = aggregateId (= auctionId)
   - sucesso → deleta a linha do outbox
   - falha → incrementa attempt_count, grava last_attempt_at, mantém PENDING
   - atinge app.outbox.max-attempts (default 10) → marca status=FAILED e para de tentar (forense)
        ↓
4. Review-service processa e devolve:
   - "reviews.qa.approved" → consumer marca ACTIVE + enfileira QuestionApprovedNotification (para o vendedor)
   - "reviews.qa.rejected" → consumer marca REJECTED + grava rejectionReason + enfileira QuestionRejectedNotification (para o autor da pergunta)
        ↓
5. OutboxProcessor publica em qa.{question|answer}.{approved|rejected} (também com key = auctionId)
        ↓
6. Notification-service recebe e dispara e-mail/push.
```

Fluxo da resposta (`Answer`) é simétrico. **Regra de destinatário das notificações:**

- `QuestionApprovedNotification` → vai para o **vendedor** (`question.sellerId`) — quem fez a pergunta agora pode esperar a resposta dele.
- `QuestionRejectedNotification` → vai para o **autor da pergunta** (`question.authorId`, = comprador) — para ele saber que a pergunta não passou na moderação.
- `AnswerApprovedNotification` → vai para o **autor da pergunta** (`question.authorId`, = comprador) — a pergunta dele foi respondida.
- `AnswerRejectedNotification` → vai para o **autor da resposta** (`answer.authorId`, = vendedor) — a resposta dele não passou na moderação.

Essas regras vivem dentro dos factory methods estáticos dos records (`forSellerOf`, `forAuthorOf`, `forBuyerOf`) e são a única fonte de verdade sobre "quem recebe o quê".

## Arquitetura e Padrões
- **Microsserviços** com discovery via **Eureka Client** (`user-service`, `auction-service`).
- **Tolerância a falhas** com **Resilience4j** (Circuit Breaker + Retry) sobre os clientes Feign — fallbacks implementados em `AuctionClientFallbackFactory` (lança `AuctionServiceUnavailableException`) e `UserClientFallbackFactory` (modo degradado: devolve `UserResponse` com nome/e-mail nulos).
- **Transactional Outbox**: eventos gravados em `outbox_events` na mesma transação de negócio; `OutboxProcessor` publica e remove.
- **Moderação assíncrona** via Kafka — a requisição REST original não espera o review.
- **Factory methods** nos records de notificação (`QuestionApprovedNotification.forSellerOf`, `AnswerApprovedNotification.forBuyerOf`, `QuestionRejectedNotification.forAuthorOf`, `AnswerRejectedNotification.forAuthorOf`) encapsulam a regra "quem é o destinatário" e fecham a porta para o bug histórico em que o `authorId` significava papéis diferentes em `questions` (autor=comprador) e `answers` (autor=vendedor) — antes da migration V5, ambas as colunas se chamavam `user_id` e a confusão gerou UUIDs errados como destinatário.

## Tecnologias
- **Java 21**
- **Spring Boot 3.3.4** — Web, Data JPA, Validation, AOP, Actuator, Cache.
- **Spring Cloud 2023.0.3** — OpenFeign, Netflix Eureka Client, Resilience4j Circuit Breaker.
- **PostgreSQL** + **Flyway** (v10.10.0).
- **Redis** (cache de listagens, TTL = 120 min em `CacheConfig`).
- **Apache Kafka** (4.3.0 no compose externo).
- **Lombok**, **JUnit 5**, **Mockito**, **Spring Cloud Contract WireMock**.

## 📁 Estrutura do Projeto

```text
br.com.leilao
├── config/                      # CacheConfig (Redis), KafkaConsumerConfig (JSON), Resilience4jConfig (eventos de CB/Retry)
├── controller/                  # AnswerController, QuestionController
├── domain/
│   ├── entity/                  # Question, Answer, OutboxEvent
│   └── enums/                   # ContentStatus
├── dto/
│   ├── request/                 # CreateQuestionRequest, CreateAnswerRequest (com Bean Validation)
│   └── response/                # QuestionResponse, AnswerResponse, ErrorResponse, RestResponsePage
├── exception/                   # 5 exceções customizadas + GlobalExceptionHandler
├── integration/
│   ├── feign/                   # AuctionClient, UserClient + FallbackFactories + DTOs
│   └── kafka/
│       ├── OutboxEventPublisher # salva no outbox
│       ├── OutboxProcessor      # @Scheduled lê outbox e publica no Kafka
│       ├── consumer/            # KafkaReviewConsumer (adapter fino — delega para ReviewResultService)
│       └── events/              # MessageCreatedPendingReview, MessageReviewApproved/Rejected,
│                                # QuestionApprovedNotification, AnswerApprovedNotification,
│                                # QuestionRejectedNotification, AnswerRejectedNotification
├── repository/                  # QuestionRepository, AnswerRepository, OutboxEventRepository
└── service/
    ├── QuestionService, AnswerService
    ├── ReviewResultService      # aplica resultado da moderação (chamado pelo KafkaReviewConsumer)
    └── mapper/                  # QuestionMapper, AnswerMapper
```

## 🔗 Integrações

### Síncronas (OpenFeign)

| Cliente | name= | Path consumido | Fallback |
|---|---|---|---|
| `AuctionClient` | `auction-service` | `GET /auctions/{auctionId}` | `AuctionServiceUnavailableException` → HTTP 503 |
| `UserClient` | `user-service` | `GET /usuarios/{userId}` | `UserResponse(null, null)` (degradado) |

**Resilience4j** (`application.yaml`):
- Retry: `maxAttempts=3`, `waitDuration=2s`.
- Circuit Breaker: `slidingWindowSize=10`, `minimumNumberOfCalls=5`, `failureRateThreshold=50%`, `waitDurationInOpenState=15s`.
- Exposto em `/actuator/circuitbreakers` e `/actuator/health`.

**Resolução do host:**
- Em `dev`/`prod`: Eureka (`@EnableDiscoveryClient` + `name=` no `@FeignClient`).
- Em `local`: sobrescrita por YAML (`spring.cloud.openfeign.client.config.<name>.url`) apontando para `localhost:8066` e `localhost:8080`. Sem Eureka.
- Em `test`: `SimpleDiscoveryClient` estático apontando para WireMock.

### Assíncronas (Kafka)

Tópicos produzidos (via OutboxProcessor):

| Chave em app.kafka.topics | Tópico real | Quando |
|---|---|---|
| qa-review-created-pending | qa.review.created-pending | Q ou A criada — pede review |
| notify-question-approved | qa.question.approved | Após aprovar Q — recipient = vendedor |
| notify-answer-approved | qa.answer.approved | Após aprovar A — recipient = comprador (autor da pergunta) |
| notify-question-rejected | qa.question.rejected | Após rejeitar Q — recipient = autor da pergunta |
| notify-answer-rejected | qa.answer.rejected | Após rejeitar A — recipient = autor da resposta (vendedor) |

Tópicos consumidos (KafkaReviewConsumer, groupId=qa-group):

| Chave em app.kafka.topics | Tópico real | Efeito |
|---|---|---|
| qa-review-approved | reviews.qa.approved | Status → ACTIVE + grava notify-*approved no outbox |
| qa-review-rejected | reviews.qa.rejected | Status → REJECTED + grava rejectionReason + grava notify-*rejected no outbox |

## Configurações e Profiles

| Profile | Quando usar | O que muda |
|---|---|---|
| `dev` (default) | Rodando local com Postgres/Redis/Kafka via Docker | Logs SQL e Feign em DEBUG; aponta para `localhost:5432`/`6379`/`9092` e Eureka em `8761`. |
| `prod` | Ambiente real | Tudo vem de envs (`DB_URL`, `DB_USER`, `DB_PASS`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`); logs INFO; SQL não-formatado. |
| `local` | Subir sem Docker / sem Eureka | Desliga Eureka e (tenta) apontar Feign para `localhost:8080`/`8081`. Veja a ressalva sobre `url=` em [Síncronas](#síncronas-openfeign). |
| `test` (em `src/test/resources/application-test.yaml`) | Testes de integração | Exclui auto-config de JPA/Redis/Kafka/Flyway; substitui Eureka por discovery estático apontando para WireMock; `retry.maxAttempts=1` para encurtar testes de CB. |
| `review-stub` | Testar o ciclo de moderação sem o review-service real | Ativa `ReviewServiceStub`: consome `qa.review.created-pending` e devolve `reviews.qa.approved` (ou `reviews.qa.rejected` se o texto contiver `spam`, `ofensa` ou `fraude`). Combine com `dev`: `-Dspring-boot.run.profiles=dev,review-stub`. |

**Cache (`CacheConfig.java`):** TTL parametrizado via `app.cache.auction-questions.ttl` (default `PT2H` = 120 min), namespace `auction_questions`, chave `auctionId-pageNumber`. Invalidado por `@CacheEvict(allEntries=true)` em create/delete de Question e Answer **e** em aprovação/rejeição vinda do `KafkaReviewConsumer`.

## Endpoints (API Reference)

Todos os endpoints exigem os headers de gateway: **`X-User-Id`** (UUID) e, nos `POST`, **`X-User-Allowed`** (boolean).

### Questions

| Método | Path | Comportamento |
|---|---|---|
| `POST` | `/api/qa/auctions/{auctionId}/questions` | Cria pergunta (`status=PENDING_ANALYSIS`) — exige `X-User-Allowed=true`. |
| `GET` | `/api/qa/auctions/{auctionId}/questions?page=0&size=10` | Lista perguntas `ACTIVE` paginadas (sort `createdAt ASC`). Cacheado por (`auctionId`, `page`). |
| `DELETE` | `/api/qa/questions/{questionId}` | Soft delete (só o autor; bloqueado se ainda em `PENDING_ANALYSIS`). |

### Answers

| Método | Path | Comportamento |
|---|---|---|
| `POST` | `/api/qa/questions/{questionId}/answers` | Cria resposta (`status=PENDING_ANALYSIS`) — só o vendedor (`question.sellerId == X-User-Id`); a pergunta precisa estar `ACTIVE`; uma única resposta por pergunta. |
| `DELETE` | `/api/qa/questions/{questionId}/answers/{answerId}` | Soft delete (só o autor; bloqueado se ainda em `PENDING_ANALYSIS`). |

### Erros (via `GlobalExceptionHandler`)

| Exceção | HTTP |
|---|---|
| `ResourceNotFoundException` | 404 |
| `ForbiddenOperationException` | 403 |
| `AnswerAlreadyExistsException` | 409 |
| `InvalidOperationException` | 400 |
| `MissingRequestHeaderException` | 400 |
| `MethodArgumentNotValidException` | 422 |
| `RateLimitExceededException` | 429 |
| `AuctionServiceUnavailableException` | 503 |
| `Exception` (catch-all) | 500 |

### Rate Limiting

Os endpoints de criação são protegidos por um aspecto AOP (`RateLimitAspect`) que conta requisições no Redis por `userId`.

| Ação | Chave | Limite default |
|---|---|---|
| `create-question` (POST pergunta) | `rl:qa:create-question:{userId}` | 10 / 60s |
| `create-answer` (POST resposta) | `rl:qa:create-answer:{userId}` | 10 / 60s |

- **Janela fixa** (`INCR` + `EXPIRE` na primeira chamada).
- **Fail-open:** se o Redis cair, o request passa (log WARN) — Redis não derruba o serviço.
- **Resposta no estouro:** HTTP **429 Too Many Requests** (sem `Retry-After`).
- Ajustável via `app.rate-limit.actions.<ação>.{limit,window-seconds}` no `application.yaml`.
- Para limitar uma nova ação: anote o controller com `@RateLimited("nome")` e configure a regra no YAML. O aspecto usa o primeiro `UUID` dos args como `userId`.

### Migrations (Flyway)

**Histórico de migrations:**

| Versão | O que faz |
|---|---|
| V1 | Cria questions e answers. |
| V2 | Cria outbox_events. |
| V3 | rejection_reason vira TEXT (era VARCHAR(50)). |
| V4 | Unifica IDs de questions+answers na sequência qa_content_id_seq (resolve colisão de messageId no consumer). |
| V5 | Renomeia user_id → author_id nas duas tabelas (resolve ambiguidade: em `questions` o autor é o comprador; em `answers` o autor é o vendedor — coluna com o mesmo nome representando papéis diferentes gerou bug histórico de destinatário errado). |
| V6 | Adiciona `attempt_count`, `last_attempt_at`, `status` e `aggregate_id` em `outbox_events` + índice `(status, created_at)`. Habilita retry com contador, terminal `FAILED` e Kafka key por aggregate. |
