package pingpong.backend.domain.team;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "figma")
    private String figma;

    @Column(name = "discord")
    private String discord;

    @Column(name = "swagger")
    private String swagger;

    @Column(name = "github")
    private String github;

    @Column(name = "is_updated")
    private Boolean isUpdated;

    public static Team create(String name, String figma, String discord,
                              String swagger, String github) {
        return Team.builder()
                .name(name)
                .figma(figma)
                .discord(discord)
                .swagger(swagger)
                .github(github)
                .isUpdated(Boolean.FALSE)
                .build();
    }

    public void markUpdated() {
        this.isUpdated = Boolean.TRUE;
    }
}

