package pingpong.backend.domain.swagger.util;

import static pingpong.backend.domain.swagger.util.SwaggerNormalizeUtil.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.global.exception.CustomException;

@Component
public class SwaggerHashUtil {

	private static final ObjectMapper mapper = new ObjectMapper()
		.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);

	/**
	 * swagger snapshot SHA-256 해시값 생성
	 * @param root
	 * @return
	 */
	public String generateSpecHash(JsonNode root) {
		try {
			JsonNode paths = root.get("paths");
			JsonNode components = root.get("components");

			//필요한 부분만 JSON으로 구성
			ObjectNode minimal = mapper.createObjectNode();

			if (paths != null) {
				minimal.set("paths", paths);
			}
			if (components != null) {
				minimal.set("components", components);
			}

			//key정렬
			String cannoicalJson = mapper.writeValueAsString(minimal);
			return sha256(cannoicalJson);
		}catch(Exception e){
			throw new CustomException(SwaggerErrorCode.JSON_PROCESSING_EXCEPTION);
		}
	}

	/**
	 * SHA-256A 해시 함수 적용
	 * @param input
	 * @return
	 */
	private static String sha256(String input) {
		try{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

			StringBuilder hexString = new StringBuilder();
			for (byte b:hashBytes){
				hexString.append(String.format("%02x", b));
			}

			return hexString.toString();
		}catch(Exception e){
			throw new CustomException(SwaggerErrorCode.HASHING_EXCEPTION);
		}
	}

	/**
	 * requestBody에 대한 해시값 생성
	 * @param root
	 * @return
	 */
	public String generateRequestHash(JsonNode root) {
		JsonNode requestBody=root.get("requestBody");
		if(requestBody==null||requestBody.isNull()){
			return null;
		}

		JsonNode normalized=normalizeNode(requestBody);
		String canonical=writeCanonical(normalized);
		return sha256(canonical);
	}

	/**
	 * responseBody에 대한 해시값 생성
	 * @param root
	 * @return
	 */
	public String generateResponseHash(JsonNode root) {
		JsonNode requestBody=root.get("responses");
		if(requestBody==null||requestBody.isNull()){
			return null;
		}

		JsonNode normalized=normalizeNode(requestBody);
		String canonical=writeCanonical(normalized);
		return sha256(canonical);
	}


	/**
	 * hash 값 생성 전 정렬
	 * @param node
	 * @return
	 */
	private String writeCanonical(JsonNode node){
		try{
			return mapper.writeValueAsString(node);
		}catch(Exception e){
			throw new CustomException(SwaggerErrorCode.JSON_PROCESSING_EXCEPTION);
		}
	}

	/**
	 * schema에 대한 hash값 생성
	 * @param schemaNode
	 * @return
	 */
	public String generateSchemaHash(JsonNode schemaNode){

		if(schemaNode==null||schemaNode.isNull()){
			return null;
		}

		JsonNode normalized=normalizeNode(schemaNode);
		String canonical=writeCanonical(normalized);
		return sha256(canonical);
	}
}
