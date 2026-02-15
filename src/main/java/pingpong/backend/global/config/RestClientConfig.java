package pingpong.backend.global.config;

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
}
