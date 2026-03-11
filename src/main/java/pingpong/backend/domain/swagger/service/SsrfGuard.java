package pingpong.backend.domain.swagger.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.springframework.stereotype.Component;

import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.global.exception.CustomException;

@Component
public class SsrfGuard {

	public void validate(String url) {
		if (url == null || url.isBlank()) {
			return;
		}

		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			throw new CustomException(SwaggerErrorCode.SSRF_BLOCKED);
		}

		String scheme = uri.getScheme();
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw new CustomException(SwaggerErrorCode.SSRF_BLOCKED);
		}

		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new CustomException(SwaggerErrorCode.SSRF_BLOCKED);
		}

		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		} catch (UnknownHostException e) {
			throw new CustomException(SwaggerErrorCode.SSRF_BLOCKED);
		}

		for (InetAddress addr : addresses) {
			if (isBlocked(addr)) {
				throw new CustomException(SwaggerErrorCode.SSRF_BLOCKED);
			}
		}
	}

	private boolean isBlocked(InetAddress addr) {
		return addr.isLoopbackAddress()    // 127.x.x.x, ::1
			|| addr.isLinkLocalAddress()   // 169.254.x.x (AWS 메타데이터), fe80::/10
			|| addr.isSiteLocalAddress()   // 10.x.x.x, 172.16-31.x.x, 192.168.x.x
			|| addr.isAnyLocalAddress();   // 0.0.0.0
	}
}
