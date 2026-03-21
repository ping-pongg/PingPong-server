package pingpong.backend.domain.qa.converter;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class MapStringConverter implements AttributeConverter<Map<String, String>, String> {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String convertToDatabaseColumn(Map<String, String> attribute) {
		if (attribute == null) return null;
		try {
			return objectMapper.writeValueAsString(attribute);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Map<String, String> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isEmpty()) return null;
		try {
			return objectMapper.readValue(dbData, new TypeReference<Map<String, String>>() {});
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

}