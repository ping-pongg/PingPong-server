package pingpong.backend.domain.notion;

import jakarta.persistence.*;
import lombok.*;
import pingpong.backend.domain.team.Team;

import java.time.Instant;

@Entity
@Table(name = "notion",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notion_team_id", columnNames = "team_id")
        })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Notion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notion_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, unique = true)
    private Team team;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "workspace_id")
    private String workspaceId;

    @Column(name = "bot_id")
    private String botId;

    @Column(name = "workspace_name")
    private String workspaceName;

    @Column(name = "database_id")
    private String databaseId;

    @Column(name = "data_source_id")
    private String dataSourceId;

    @Column(name = "token_updated_at")
    private Instant tokenUpdatedAt;

    @Column(name = "verification_token")
    private String verificationToken;

    @Version
    private Long version;

    public static Notion create(Team team) {
        return Notion.builder()
                .team(team)
                .build();
    }

    public void updateTokens(String accessToken,
                             String refreshToken,
                             Instant tokenUpdatedAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenUpdatedAt = tokenUpdatedAt;
    }

    public void updateWorkspace(String workspaceId, String botId, String workspaceName) {
        this.workspaceId = workspaceId;
        this.botId = botId;
        this.workspaceName = workspaceName;
    }

    public void updateDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    public void updateConnection(String databaseId, String dataSourceId) {
        this.databaseId = databaseId;
        this.dataSourceId = dataSourceId;
    }

    public void updateVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }
}
