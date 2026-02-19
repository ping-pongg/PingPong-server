package pingpong.backend.domain.swagger.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.SwaggerResponse;
import pingpong.backend.domain.swagger.dto.ParameterResponse;
import pingpong.backend.domain.swagger.dto.ParameterSnapshotRes;
import pingpong.backend.domain.swagger.dto.RequestBodyResponse;
import pingpong.backend.domain.swagger.dto.ResponseBodyResponse;
import pingpong.backend.domain.swagger.enums.DiffType;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiffService {

	private final ObjectMapper objectMapper;

	/**
	 * parameters 변경사항 파악 
	 * @param prevList
	 * @param currList
	 * @return
	 */
	public List<ParameterResponse> diffParameters(
		List<SwaggerParameter> prevList,
		List<SwaggerParameter> currList
	) {

		//이전 파라미터를 Map으로 변환
		Map<String, SwaggerParameter> prevMap =
			prevList.stream().collect(Collectors.toMap(
				this::paramKey,
				Function.identity()
			));

		//현재 파라미터를 Map으로 변환
		Map<String, SwaggerParameter> currMap =
			currList.stream().collect(Collectors.toMap(
				this::paramKey,
				Function.identity()
			));

		//모든 key 집합 만들기
		Set<String> allKeys = new HashSet<>();
		allKeys.addAll(prevMap.keySet());
		allKeys.addAll(currMap.keySet());

		List<ParameterResponse> result = new ArrayList<>();

		//key 단위로 diff 판정
		for (String key : allKeys) {

			SwaggerParameter prev = prevMap.get(key);
			SwaggerParameter curr = currMap.get(key);

			DiffType diff;

			if (prev == null) diff = DiffType.ADDED;
			else if (curr == null) diff = DiffType.REMOVED;
			else if (!Objects.equals(prev.getSchemaHash(), curr.getSchemaHash()))
				diff = DiffType.MODIFIED;
			else diff = DiffType.UNCHANGED;

			result.add(ParameterResponse.of(prev,curr, diff,objectMapper));
		}

		return result;
	}

	private String paramKey(SwaggerParameter prev) {
		return prev.getName()+"|"+prev.getInType();
	}

	/**
	 * response 변경사항 파악
	 * @param prevList
	 * @param currList
	 * @return
	 */
	public List<ResponseBodyResponse> diffResponses(
		List<SwaggerResponse> prevList,
		List<SwaggerResponse> currList
	) {

		Map<String, SwaggerResponse> prevMap =
			prevList.stream().collect(Collectors.toMap(
				this::responseKey,
				Function.identity()
			));

		Map<String, SwaggerResponse> currMap =
			currList.stream().collect(Collectors.toMap(
				this::responseKey,
				Function.identity()
			));

		Set<String> allKeys = new HashSet<>();
		allKeys.addAll(prevMap.keySet());
		allKeys.addAll(currMap.keySet());

		List<ResponseBodyResponse> result = new ArrayList<>();

		for (String key : allKeys) {

			SwaggerResponse prev = prevMap.get(key);
			SwaggerResponse curr = currMap.get(key);

			DiffType diff;

			if (prev == null) diff = DiffType.ADDED;
			else if (curr == null) diff = DiffType.REMOVED;
			else if (!Objects.equals(prev.getSchemaHash(), curr.getSchemaHash()))
				diff = DiffType.MODIFIED;
			else diff = DiffType.UNCHANGED;

			result.add(ResponseBodyResponse.of(prev,curr, diff,objectMapper));
		}

		return result;
	}

	private String responseKey(SwaggerResponse r) {
		return r.getStatusCode()+"|"+r.getMediaType();
	}

	/**
	 * request 변경사항 파악
	 * @param prevList
	 * @param currList
	 * @return
	 */
	public List<RequestBodyResponse> diffRequests(
		List<SwaggerRequest> prevList,
		List<SwaggerRequest> currList
	) {

		Map<String, SwaggerRequest> prevMap =
			prevList.stream().collect(Collectors.toMap(
				this::requestKey,
				Function.identity()
			));

		Map<String, SwaggerRequest> currMap =
			currList.stream().collect(Collectors.toMap(
				this::requestKey,
				Function.identity()
			));

		Set<String> allKeys = new HashSet<>();
		allKeys.addAll(prevMap.keySet());
		allKeys.addAll(currMap.keySet());

		List<RequestBodyResponse> result = new ArrayList<>();

		for (String key : allKeys) {

			SwaggerRequest prev = prevMap.get(key);
			SwaggerRequest curr = currMap.get(key);

			DiffType diff;

			if (prev == null) {
				diff = DiffType.ADDED;
			} else if (curr == null) {
				diff = DiffType.REMOVED;
			} else if (
				!Objects.equals(prev.getSchemaHash(), curr.getSchemaHash())
					|| prev.isRequired() != curr.isRequired()   // ⭐⭐⭐ 중요
			) {
				diff = DiffType.MODIFIED;
			} else {
				diff = DiffType.UNCHANGED;
			}

			result.add(
				RequestBodyResponse.of(prev, curr, diff, objectMapper)
			);
		}

		return result;
	}

	private String requestKey(SwaggerRequest r) {
		if(r.getMediaType()==null)return "null";
		return r.getMediaType().toLowerCase();
	}

}
