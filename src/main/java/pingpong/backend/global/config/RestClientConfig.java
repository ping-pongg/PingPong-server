package pingpong.backend.global.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

	@Bean
	public RestClient restClient() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(3000); //연결 timeout 3초
		requestFactory.setReadTimeout(5000); //응답 timeout 5초
		return RestClient.builder()
			.requestFactory(requestFactory)
			.build();
	}

	@Bean("githubRestTemplate")
	public RestTemplate githubRestTemplate(RestTemplateBuilder builder) {
		return builder
			.connectTimeout(Duration.ofSeconds(5))
			.readTimeout(Duration.ofSeconds(20)) // Diff 분석은 시간이 걸릴 수 있으므로 20초 부여
			.defaultHeader("Accept", "application/vnd.github.v3+json") // GitHub API 버전 명시
			.build();
	}
}
