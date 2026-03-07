package pingpong.backend.domain.member;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "member_mcp_connection",
        uniqueConstraints = @UniqueConstraint(name = "uk_member_mcp_member_id", columnNames = "member_id")
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberMcpConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public static MemberMcpConnection create(Long memberId, Long teamId) {
        return MemberMcpConnection.builder()
                .memberId(memberId)
                .teamId(teamId)
                .connectedAt(LocalDateTime.now())
                .lastUsedAt(LocalDateTime.now())
                .build();
    }

    public void updateLastUsedAt() {
        this.lastUsedAt = LocalDateTime.now();
    }

    public void updateTeamId(Long teamId) {
        this.teamId = teamId;
    }
}
