package pingpong.backend.domain.qa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pingpong.backend.domain.team.Team;

@Getter
@Entity
@Table(name = "qa_param_default", uniqueConstraints =
	@UniqueConstraint(columnNames = {"team_id", "param_name"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QaParamDefault {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@Column(name = "param_name", nullable = false)
	private String paramName;

	@Column(name = "param_value", nullable = false)
	private String paramValue;

	public static QaParamDefault create(Team team, String paramName, String paramValue) {
		QaParamDefault param = new QaParamDefault();
		param.team = team;
		param.paramName = paramName;
		param.paramValue = paramValue;
		return param;
	}
}
