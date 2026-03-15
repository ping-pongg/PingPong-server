package pingpong.backend.domain.github;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pingpong.backend.domain.team.Team;

@Getter
@Entity
@Builder
@Table
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PRIVATE)
public class Github {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column
	private Long id;

	@OneToOne(fetch= FetchType.LAZY)
	@JoinColumn(name = "team_id",unique=true)
	private Team team;

	@Column(name="repo_owner")
	private String repoOwner;

	@Column(name="repo_name")
	private String repoName;

	@Column
	private String branch;

	@Column(name="last_head_sha")
	private String lastHeadSha;

	@Column(name="last_synced_at")
	private LocalDateTime lastSyncedAt;

	@Column
	private LocalDateTime createdAt;

	@Column
	private LocalDateTime updatedAt;

	public static Github create(String repoOwner, String repoName, String branch,Team team) {
		return Github.builder()
			.repoOwner(repoOwner)
			.repoName(repoName)
			.branch(branch)
			.createdAt(LocalDateTime.now())
			.team(team)
			.build();
	}

	public Github updateSyncInfo(String newSha){
		this.lastHeadSha = newSha;
		this.lastSyncedAt = LocalDateTime.now();
		return this;
	}

	public void updateConfig(String repoOwner,String repoName,String branch){
		this.repoOwner = repoOwner;
		this.repoName = repoName;
		this.branch = (branch==null||branch.isBlank())?"main":branch;
		this.lastSyncedAt=null;
		this.lastHeadSha=null;
	}
}
