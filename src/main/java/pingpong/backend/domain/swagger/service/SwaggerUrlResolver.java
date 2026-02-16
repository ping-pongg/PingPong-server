package pingpong.backend.domain.swagger.service;

import java.net.URI;

import org.springframework.stereotype.Component;

@Component
public class SwaggerUrlResolver {

	/**
	 * swagger JSON 형태의 URL 추출
	 * @param swaggerUrl
	 * @return
	 */
	public String resolveSwaggerUrl(String swaggerUrl) {
		URI uri=URI.create(swaggerUrl);
		String baseUrl=uri.getScheme()+"://"+uri.getHost();

		System.out.println(baseUrl);
		return baseUrl+"/v3/api-docs";
	}
}
