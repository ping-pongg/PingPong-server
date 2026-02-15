package pingpong.backend.global.rag.indexing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;

import java.time.Instant;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_indexing_state_source_key", columnNames = "source_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndexingState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "indexing_state_id")
    private Long id;

    @Column(name = "source_type", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private IndexSourceType sourceType;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "api_path", nullable = false, length = 300)
    private String apiPath;

    @Column(name = "resource_id", length = 120)
    private String resourceId;

    @Column(name = "source_key", nullable = false, length = 512)
    private String sourceKey;

    @Column(name = "document_prefix", nullable = false, length = 64)
    private String documentPrefix;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private IndexingState(IndexSourceType sourceType,
                               Long teamId,
                               String apiPath,
                               String resourceId,
                               String sourceKey,
                               String documentPrefix,
                               String contentHash,
                               int chunkCount,
                               Instant updatedAt) {
        this.sourceType = sourceType;
        this.teamId = teamId;
        this.apiPath = apiPath;
        this.resourceId = resourceId;
        this.sourceKey = sourceKey;
        this.documentPrefix = documentPrefix;
        this.contentHash = contentHash;
        this.chunkCount = chunkCount;
        this.updatedAt = updatedAt;
    }

    public static IndexingState create(IndexSourceType sourceType,
                                            Long teamId,
                                            String apiPath,
                                            String resourceId,
                                            String sourceKey,
                                            String documentPrefix,
                                            String contentHash,
                                            int chunkCount,
                                            Instant updatedAt) {
        return new IndexingState(sourceType, teamId, apiPath, resourceId, sourceKey, documentPrefix, contentHash, chunkCount, updatedAt);
    }

    public void refresh(IndexSourceType sourceType,
                        String apiPath,
                        String resourceId,
                        String contentHash,
                        int chunkCount,
                        Instant updatedAt) {
        this.sourceType = sourceType;
        this.apiPath = apiPath;
        this.resourceId = resourceId;
        this.contentHash = contentHash;
        this.chunkCount = chunkCount;
        this.updatedAt = updatedAt;
    }
}
