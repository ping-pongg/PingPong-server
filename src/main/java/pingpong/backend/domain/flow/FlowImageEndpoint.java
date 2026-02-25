package pingpong.backend.domain.flow;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pingpong.backend.domain.swagger.Endpoint;

@Getter
@Entity
@Builder
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PRIVATE)
@Table(
	name = "flow_image_endpoint",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_flow_image_endpoint",
		columnNames = {"image_id", "endpoint_id"}
	)
)
public class FlowImageEndpoint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id", nullable = false)
	private FlowImage image;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "endpoint_id", nullable = false)
	private Endpoint endpoint;

	@Column
	private Float x;

	@Column
	private Float y;

	@Column
	private Boolean isChanged;

	@Column
	private Boolean isLinked;

	// 생성/변경 메서드
	public static FlowImageEndpoint create(FlowImage img, Endpoint ep, Float x, Float y) {
		FlowImageEndpoint m = new FlowImageEndpoint();
		m.image = img;
		m.endpoint = ep;
		m.x = x;
		m.y = y;
		m.isChanged = ep.getIsChanged();
		m.isLinked = false;
		return m;
	}

	public void updatePosition(Float x, Float y) {
		this.x = x;
		this.y = y;
	}

	public Boolean markLinked() {
		this.isLinked = true;
		return null;
	}

	public void markChanged() {
		this.isChanged = true;
	}
}
