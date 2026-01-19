package pingpong.backend.domain.team;

import jakarta.persistence.*;
import lombok.*;
import pingpong.backend.domain.team.enums.Role;

@Entity
@Table(
        name = "member_team",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_team_team_member", columnNames = {"team_id", "member_id"})
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_team_id")
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    public static MemberTeam of(Long teamId, Long memberId, Role role) {
        return MemberTeam.builder()
                .teamId(teamId)
                .memberId(memberId)
                .role(role)
                .build();
    }
}

