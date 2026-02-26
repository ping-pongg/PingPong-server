package pingpong.backend.domain.swagger.service;

import java.net.URI;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SwaggerUrlResolver {

	/**
	 * swagger JSON 형태의 URL 추출
	 * @param swaggerUrl
	 * @return
	 */
	public String resolveSwaggerUrl(String swaggerUrl) {
		URI uri=URI.create(swaggerUrl);
		String baseUrl=uri.getScheme()+"://"+uri.getHost();

		log.debug("Base URL for swagger docs: {}", baseUrl);
		return baseUrl+"/v3/api-docs";
	}

	/**
	 * 팀 서버 base URL 추출 (포트 포함)
	 * @param swaggerUrl
	 * @return scheme://host[:port]
	 */
	public String resolveBaseUrl(String swaggerUrl) {
		URI uri = URI.create(swaggerUrl);
		String base = uri.getScheme() + "://" + uri.getHost();
		if (uri.getPort() != -1) {
			base = base + ":" + uri.getPort();
		}
		return base;
	}
}
