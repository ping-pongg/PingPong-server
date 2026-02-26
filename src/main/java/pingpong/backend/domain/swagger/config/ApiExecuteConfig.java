package pingpong.backend.domain.swagger.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ApiExecuteConfig {

	@Bean
	public RestTemplate apiExecuteRestTemplate(RestTemplateBuilder builder) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(Duration.ofSeconds(30));
		return builder
			.requestFactory(() -> factory)
			.errorHandler(new DefaultResponseErrorHandler() {
				@Override
				public boolean hasError(@NonNull ClientHttpResponse response) {
					return false;
				}
			})
			.build();
	}
}
