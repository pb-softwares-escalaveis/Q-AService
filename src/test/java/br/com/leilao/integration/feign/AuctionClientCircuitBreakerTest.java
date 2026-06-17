package br.com.leilao.integration.feign;

import br.com.leilao.exception.AuctionServiceUnavailableException;
import br.com.leilao.repository.AnswerRepository;
import br.com.leilao.repository.OutboxEventRepository;
import br.com.leilao.repository.QuestionRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)

@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test") //reduz o retry para 1 tentativa
class AuctionClientCircuitBreakerTest
{
    @Autowired
    private AuctionClient auctionClient;

    @Autowired
    private CircuitBreakerRegistry registry;

    @MockBean
    private QuestionRepository questionRepository;

    @MockBean
    private AnswerRepository answerRepository;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // Stub para o OutboxProcessor (@Scheduled cada 5s) não estourar NPE durante o teste.
        when(outboxEventRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(Collections.emptyList());

        // Captura a instância exata do Circuit Breaker configurada no seu application.yaml
        circuitBreaker = registry.circuitBreaker("auctionService");

        // Garante que todo teste comece com o circuito fechado (estado normal)
        circuitBreaker.transitionToClosedState();

        // Limpa as configurações do WireMock entre os testes
        WireMock.reset();
    }

    @Test
    @DisplayName("Deve abrir o Circuit Breaker após taxa de falha atingir o limite (50%) na janela de 10 chamadas")
    void abreCircuitAposFalhasSeguidas() {
        // Configura o WireMock para simular o auction-service retornando erro 500
        stubFor(get(urlMatching("/auctions/.*"))
                .willReturn(aResponse().withStatus(500)));

        // Nossa janela é de 10 chamadas (slidingWindowSize: 10).
        // Disparamos 12 para garantir que a janela encha e a transição de estado ocorra.
        for (int i = 0; i < 12; i++) {
            assertThrows(Exception.class, () -> auctionClient.getAuctionById(1L));
        }

        // Verifica se o Resilience4j reagiu corretamente à configuração do YAML
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    @DisplayName("Deve lançar AuctionServiceUnavailableException quando o circuito estiver ABERTO")
    void fallbackEhAcionadoQuandoCircuitAbre() {
        // Simula o erro do serviço
        stubFor(get(urlMatching("/auctions/.*"))
                .willReturn(aResponse().withStatus(500)));

        // Estoura o circuito deliberadamente
        for (int i = 0; i < 12; i++) {
            try {
                auctionClient.getAuctionById(1L);
            } catch (Exception ignored) {
            }
        }

        // Confirma que o circuito abriu
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // Agora, qualquer nova chamada nem deve bater no WireMock,
        // deve cair direto no Fallback do Feign ou estourar a exceção customizada
        AuctionServiceUnavailableException ex = assertThrows(
                AuctionServiceUnavailableException.class,
                () -> auctionClient.getAuctionById(1L)
        );

        assertNotNull(ex.getMessage());

        // Verifica que o WireMock recebeu apenas as tentativas antes da abertura,
        // comprovando que o Circuit Breaker "cortou" as chamadas subsequentes.
        verify(lessThan(12), getRequestedFor(urlMatching("/auctions/.*")));
    }
}