package pingpong.backend.domain.swagger.client;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.enums.CrudMethod;
import pingpong.backend.global.exception.CustomException;

@Slf4j
@Component
public class ApiExecuteClient {

	private final RestTemplate restTemplate;

	public ApiExecuteClient(@Qualifier("apiExecuteRestTemplate") RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public ResponseEntity<String> execute(
		String baseUrl,
		CrudMethod crudMethod,
		Map<String, String> queryParams,
		Map<String, String> requestHeaders,
		Object body
	) {
		HttpMethod httpMethod = toHttpMethod(crudMethod);

		UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUrl);
		if (queryParams != null) {
			queryParams.forEach(uriBuilder::queryParam);
		}
		String url = uriBuilder.build().toUriString();

		HttpHeaders headers = new HttpHeaders();
		if (requestHeaders != null) {
			requestHeaders.forEach(headers::add);
		}

		HttpEntity<Object> entity = new HttpEntity<>(body, headers);

		log.debug("Executing API request: {} {}", httpMethod, url);
		try {
			return restTemplate.exchange(url, httpMethod, entity, String.class);
		} catch (Exception e) {
			log.error("External API request failed: {} {}", httpMethod, url, e);
			throw new CustomException(SwaggerErrorCode.API_EXECUTE_ERROR);
		}
	}

	private HttpMethod toHttpMethod(CrudMethod crudMethod) {
		return switch (crudMethod) {
			case GET -> HttpMethod.GET;
			case POST -> HttpMethod.POST;
			case PUT -> HttpMethod.PUT;
			case PATCH -> HttpMethod.PATCH;
			case DELETE -> HttpMethod.DELETE;
		};
	}
}
