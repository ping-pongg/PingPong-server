package pingpong.backend.global.storage.service;

import java.util.UUID;

import org.springframework.stereotype.Component;


@Component
public class IdentityGenerator {

	public String generateIdentity(){
		return UUID.randomUUID().toString();
	}
}
