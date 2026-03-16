package pingpong.backend.domain.swaggerdiff.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openapitools.openapidiff.core.model.ChangedParameter;
import org.openapitools.openapidiff.core.model.ChangedParameters;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.swaggerdiff.dto.DiffType;
import pingpong.backend.domain.swaggerdiff.dto.EndpointParameterDto;
import pingpong.backend.domain.swaggerdiff.dto.ParameterChangeDto;

@Component
@RequiredArgsConstructor
public class ParameterChangeMapper {

	private final SchemaNormalizer schemaNormalizer;
	private final SchemaChangeMapper schemaChangeMapper;
	private final ObjectMapper objectMapper;

	public List<ParameterChangeDto> mapModified(ChangedParameters changedParams, Operation newOp) {
		List<ParameterChangeDto> result = new ArrayList<>();
		Set<String> handledKeys = new HashSet<>();

		if (changedParams != null) {
			for (Parameter p : nullSafe(changedParams.getIncreased())) {
				result.add(new ParameterChangeDto(DiffType.ADDED, null, toParamDto(p), false, null, null));
				handledKeys.add(paramKey(p));
			}
			for (Parameter p : nullSafe(changedParams.getMissing())) {
				result.add(new ParameterChangeDto(DiffType.REMOVED, toParamDto(p), null, false, null, null));
				handledKeys.add(paramKey(p));
			}
			for (ChangedParameter cp : nullSafe(changedParams.getChanged())) {
				result.add(toParameterChangeDto(cp));
				handledKeys.add(paramKey(cp.getNewParameter()));
			}
		}

		if (newOp != null) {
			for (Parameter p : nullSafe(newOp.getParameters())) {
				if (!handledKeys.contains(paramKey(p))) {
					EndpointParameterDto dto = toParamDto(p);
					result.add(new ParameterChangeDto(DiffType.UNCHANGED, dto, dto, false, null, null));
				}
			}
		}

		return result;
	}

	public List<ParameterChangeDto> mapStatic(DiffType type, List<Parameter> parameters) {
		List<ParameterChangeDto> result = new ArrayList<>();
		for (Parameter p : nullSafe(parameters)) {
			EndpointParameterDto dto = toParamDto(p);
			result.add(new ParameterChangeDto(type,
				type != DiffType.ADDED ? dto : null,
				type != DiffType.REMOVED ? dto : null,
				false, null, null));
		}
		return result;
	}

	public EndpointParameterDto toParamDto(Parameter p) {
		if (p == null) return null;
		Object rawExample = p.getExample() != null ? p.getExample()
			: (p.getSchema() != null ? p.getSchema().getExample() : null);
		if (rawExample == null && p.getSchema() != null && p.getSchema().getType() != null) {
			rawExample = p.getSchema().getType();
		}
		JsonNode exampleValue = rawExample != null ? objectMapper.valueToTree(rawExample) : null;
		return new EndpointParameterDto(
			p.getName(),
			p.getIn(),
			schemaNormalizer.extractType(p.getSchema()),
			p.getRequired(),
			p.getDescription(),
			exampleValue
		);
	}

	private ParameterChangeDto toParameterChangeDto(ChangedParameter cp) {
		return new ParameterChangeDto(
			DiffType.MODIFIED,
			toParamDto(cp.getOldParameter()),
			toParamDto(cp.getNewParameter()),
			cp.isDeprecated(),
			schemaChangeMapper.toChangedMetadataDto(cp.getDescription()),
			schemaChangeMapper.toSchemaChangeDto(cp.getSchema())
		);
	}

	private String paramKey(Parameter p) {
		return p.getName() + "|" + p.getIn();
	}

	private <T> List<T> nullSafe(List<T> list) {
		return list != null ? list : Collections.emptyList();
	}
}
