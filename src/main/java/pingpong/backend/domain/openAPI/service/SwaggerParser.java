package pingpong.backend.domain.openAPI.service;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.openAPI.SwaggerErrorCode;
import pingpong.backend.global.exception.CustomException;

@Component
@RequiredArgsConstructor
public class SwaggerParser {

	private final RestClient restClient;

	public JsonNode fetchJson(String uri){
		return restClient.get()
			.uri(uri)
			.retrieve()
			.onStatus(HttpStatusCode::is5xxServerError,
				(req,res)->{
					throw new CustomException(SwaggerErrorCode.SWAGGER_CONNECTION_ERROR);
				})
			.body(JsonNode.class);
	}
}
