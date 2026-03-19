package pingpong.backend.domain.qa.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

@Converter
@RequiredArgsConstructor
public class JsonNodeConverter implements AttributeConverter<JsonNode, String> {
	private final ObjectMapper objectMapper;

	@Override
	public String convertToDatabaseColumn(JsonNode attribute) {
		if (attribute == null || attribute.isNull()) return null;
		return attribute.toString();
	}

	@Override
	public JsonNode convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isEmpty()) return null;
		try {
			return objectMapper.readTree(dbData);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("JSON 읽기 에러", e);
		}
	}


}