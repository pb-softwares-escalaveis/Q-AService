package br.com.leilao.domain.entity;

import br.com.leilao.domain.enums.ContentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "qa_content_seq")
    @SequenceGenerator(name = "qa_content_seq", sequenceName = "qa_content_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private Long auctionId;

    @Column(nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentStatus status;

    private String rejectionReason;

    @OneToOne(mappedBy = "question", fetch = FetchType.LAZY)
    private Answer answer;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}