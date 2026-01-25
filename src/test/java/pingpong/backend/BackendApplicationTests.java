package pingpong.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("CI에서 통합테스트 환경(DB/Redis 등) 구성 전이라 비활성화")
@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
