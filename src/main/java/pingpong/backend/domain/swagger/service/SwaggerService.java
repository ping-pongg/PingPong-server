package pingpong.backend.domain.swagger.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.flow.enums.SnapshotDiffStatus;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerEndpointSecurity;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.SwaggerResponse;
import pingpong.backend.domain.swagger.SwaggerSnapshot;
import pingpong.backend.domain.swagger.dto.EndpointAggregate;
import pingpong.backend.domain.swagger.dto.SnapshotSecurity;
import pingpong.backend.domain.swagger.dto.request.SnapshotRequest;
import pingpong.backend.domain.swagger.dto.response.EndpointDetailResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointDiffDetailResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointGroupResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointResponse;
import pingpong.backend.domain.swagger.dto.response.ParameterResponse;
import pingpong.backend.domain.swagger.dto.response.ParameterSnapshotResponse;
import pingpong.backend.domain.swagger.dto.response.RequestBodyResponse;
import pingpong.backend.domain.swagger.dto.response.ResponseBodyResponse;
import pingpong.backend.domain.swagger.dto.response.SnapshotResponse;
import pingpong.backend.domain.swagger.dto.response.SnapshotSecurityResponse;
import pingpong.backend.domain.swagger.enums.ChangeType;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerEndpointSecurityRepository;
import pingpong.backend.domain.swagger.repository.SwaggerParameterRepository;
import pingpong.backend.domain.swagger.repository.SwaggerRequestRepository;
import pingpong.backend.domain.swagger.repository.SwaggerResponseRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.domain.swagger.util.SwaggerHashUtil;
import pingpong.backend.domain.team.service.TeamService;
import pingpong.backend.global.exception.CustomException;

@Service
@Slf4j
@RequiredArgsConstructor
public class SwaggerService {


	private final SwaggerUrlResolver swaggerUrlResolver;
	private final SwaggerParser swaggerParser;
	private final SwaggerHashUtil swaggerHashUtil;
	private final SwaggerRequestRepository swaggerRequestRepository;
	private final SwaggerResponseRepository swaggerResponseRepository;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;
	private final EndpointRepository endpointRepository;
	private final SwaggerParameterRepository swaggerParameterRepository;
	private final TeamService teamService;
	private final DiffService diffService;
	private final EndpointService endpointService;
	private final ObjectMapper objectMapper;
	private final SwaggerEndpointSecurityRepository swaggerEndpointSecurityRepository;

	/**
	 * swagger JSON Node 형태로 읽어오기
	 * @param teamId
	 * @return
	 */
	public JsonNode readSwaggerDocs(Long teamId){
		String swaggerJsonUrl=swaggerUrlResolver.resolveSwaggerUrl(teamService.getTeam(teamId).getSwagger());
		JsonNode swaggerJson=swaggerParser.fetchJson(swaggerJsonUrl);
		return swaggerJson;
	}

	/**
	 * endpoint 단건 조회 시, parameters/request/response schema 변화 내용 조회
	 * @param endpointId
	 * @return
	 */
	public EndpointDiffDetailResponse getEndpointDiffDetails(Long endpointId) {
		//현재 endpoint 조회
		Endpoint curr=endpointRepository.findById(endpointId)
			.orElseThrow(()->new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		//이전 버전 endpoint 조회
		Endpoint prev = endpointRepository
			.findTopByPathAndMethodAndSnapshotCreatedAtLessThanOrderBySnapshotCreatedAtDesc(
				curr.getPath(),
				curr.getMethod(),
				curr.getSnapshot().getCreatedAt()
			);

		//이전 버전 parameter 조회
		List<SwaggerParameter> currParams=
			swaggerParameterRepository.findByEndpointId(curr.getId());
		//현재 버전 parameter 조회
		List<SwaggerParameter> prevParams=
			prev!=null
				?swaggerParameterRepository.findByEndpointId(prev.getId())
				:List.of();

		//이전 버전 request 조회
		List<SwaggerRequest> currRequests =
			swaggerRequestRepository.findByEndpointId(curr.getId());
		//현재 버전 request 조회
		List<SwaggerRequest> prevRequests =
			prev != null
				? swaggerRequestRepository.findByEndpointId(prev.getId())
				: List.of();

		//이전 버전 response 조회
		List<SwaggerResponse> currResponses =
			swaggerResponseRepository.findByEndpointId(curr.getId());
		//현재 버전 response 조회
		List<SwaggerResponse> prevResponses =
			prev != null
				? swaggerResponseRepository.findByEndpointId(prev.getId())
				: List.of();

		//parameter diff
		List<ParameterResponse> parameterDiff =
			diffService.diffParameters(prevParams, currParams);

		//request diff
		List<RequestBodyResponse> requestDiff=
			diffService.diffRequests(prevRequests,currRequests);

		//response diff
		List<ResponseBodyResponse> responseDiff =
			diffService.diffResponses(prevResponses, currResponses);

		return new EndpointDiffDetailResponse(
			curr.getPath(),
			curr.getMethod(),
			parameterDiff,
			requestDiff,
			responseDiff
		);
	}

	/**
	 * endpoint 단건 조회
	 * @param endpointId
	 * @return
	 */
	@Transactional(readOnly = true)
	public EndpointDetailResponse getEndpointDetails(Long endpointId) {

		Endpoint endpoint = endpointRepository.findById(endpointId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		return new EndpointDetailResponse(
			endpoint.getPath(),
			endpoint.getMethod(),
			toSnapshotParameters(
				swaggerParameterRepository.findByEndpointId(endpointId)
			),
			toSnapshotRequests(
				swaggerRequestRepository.findByEndpointId(endpointId)
			),
			toSnapshotResponses(
				swaggerResponseRepository.findByEndpointId(endpointId)
			),
			toSnapshotSecurity(
				swaggerEndpointSecurityRepository.findByEndpointId(endpointId)
			)
		);
	}


	private List<SnapshotSecurityResponse> toSnapshotSecurity(
		List<SwaggerEndpointSecurity> securityList
	) {
		if (securityList == null || securityList.isEmpty()) {
			return List.of();
		}

		return securityList.stream()
			.map(SnapshotSecurityResponse::from)
			.toList();
	}
	private List<ParameterSnapshotResponse> toSnapshotParameters(
		List<SwaggerParameter> parameterList
	) {
		return parameterList.stream()
			.map(p -> ParameterSnapshotResponse.from(p, objectMapper))
			.toList();
	}

	private List<SnapshotRequest> toSnapshotRequests(
		List<SwaggerRequest> requestList
	) {
		return requestList.stream()
			.map(r -> SnapshotRequest.from(r, objectMapper))
			.toList();
	}

	private List<SnapshotResponse> toSnapshotResponses(
		List<SwaggerResponse> responseList
	) {
		return responseList.stream()
			.map(r -> SnapshotResponse.from(r, objectMapper))
			.toList();
	}


	/**
	 * 가장 최신의 swagger를 업데이트해서 비교
	 * @param teamId
	 * @param member
	 * @return
	 */
	public List<EndpointGroupResponse> syncSwagger(Long teamId,Member member){
		String swaggerJsonUrl=swaggerUrlResolver.resolveSwaggerUrl(teamService.getTeam(teamId).getSwagger());
		JsonNode swaggerJson=swaggerParser.fetchJson(swaggerJsonUrl);
		//전체 스펙 해시 계산
		String specHash=swaggerHashUtil.generateSpecHash(swaggerJson);

		//기존 스냅샷 조회
		Optional<SwaggerSnapshot> latest=swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId);
		//변화 없을 시에, 기존 반환 값 그대로 반환
		if (latest.isPresent() && latest.get().getSpecHash().equals(specHash)) {

			List<Endpoint> previousEndpoints =
				endpointRepository.findBySnapshotId(latest.get().getId());

			Map<String, List<EndpointResponse>> grouped =
				previousEndpoints.stream()
					.filter(e -> Boolean.TRUE.equals(e.getIsChanged()))
					.map(EndpointResponse::toDto)
					.collect(Collectors.groupingBy(EndpointResponse::tag));

			return grouped.entrySet().stream()
				.map(e -> new EndpointGroupResponse(
					SnapshotDiffStatus.NOT_CHANGED,
					e.getKey(),
					e.getValue()
				))
				.toList();
		}

		//이전 endpoint map 준비
		Map<String,Endpoint> prevMap;
		if(latest.isPresent()){
			List<Endpoint> prevEndpoints=
				endpointRepository.findBySnapshotId(latest.get().getId());
			prevMap=prevEndpoints.stream()
				.collect(Collectors.toMap(
					e->e.getPath()+"|"+e.getMethod(), //path+method 단위로 endpoint 동일성 판단
					Function.identity()
				));
		} else {
			prevMap = new HashMap<>();
		}

		//새로운 snapshot 생성
		List<EndpointAggregate> aggregates=swaggerParser.parseAll(swaggerJson);
		SwaggerSnapshot snapshot=SwaggerSnapshot.builder()
			.team(teamService.getTeam(teamId))
			.specHash(specHash)
			.createdAt(LocalDateTime.now())
			.endpointCount(aggregates.size())
			.rawJson(swaggerJson.toString())
			.build();
		swaggerSnapshotRepository.save(snapshot);

		//현재 endpoint 저장 + 변경 감지
		List<Endpoint> endpoints=saveAggregates(aggregates,member,snapshot,prevMap);

		//삭제된 endpoint 감지
		Set<String> currentKeys=endpoints.stream()
			.map(e->e.getPath()+"|"+e.getMethod())
			.collect(Collectors.toSet());
		List<Endpoint> deletedEndpoints=new ArrayList<>();
		for(Map.Entry<String,Endpoint> entry:prevMap.entrySet()){
			String key=entry.getKey();
			if(!currentKeys.contains(key)){
				Endpoint deleted=markDeleted(
					entry.getValue(),
					member,
					snapshot
				);
				deletedEndpoints.add(endpointRepository.save(deleted));
			}
		}

		// current + deleted 합치기
		List<Endpoint> allEndpoints = Stream.concat(
			endpoints.stream(),
			deletedEndpoints.stream()
		).toList();

		endpointService.unlinkChangedEndpoints(allEndpoints);

		// endpoint diff
		Map<String, List<EndpointResponse>> grouped =
			allEndpoints.stream()
				.filter(e -> Boolean.TRUE.equals(e.getIsChanged()))
				.map(EndpointResponse::toDto)
				.collect(Collectors.groupingBy(EndpointResponse::tag));

		return grouped.entrySet().stream()
			.map(e->new EndpointGroupResponse(
				SnapshotDiffStatus.CHANGED,
				e.getKey(),
				e.getValue()
			))
			.toList();
	}

	/**
	 * Swagger에서 파싱된 EndpointAggregate들을 실제 DB 엔티티로 저장하고, 이전 스냅샷과 비교(diff) 수행
	 * @param aggregates
	 * @param member
	 * @param snapshot
	 * @param prevMap
	 * @return
	 */
	private List<Endpoint> saveAggregates(
		List<EndpointAggregate> aggregates,
		Member member,
		SwaggerSnapshot snapshot,
		Map<String,Endpoint> prevMap) {

		List<Endpoint> savedEndpoints=new ArrayList<>();
		for(EndpointAggregate aggregate:aggregates){
			List<SwaggerParameter> parameters=aggregate.parameters();
			List<SwaggerRequest> requests=aggregate.requests();
			List<SwaggerResponse> responses=aggregate.responses();
			List<SwaggerEndpointSecurity> securities=aggregate.endpointSecuritys();

			Endpoint endpoint=aggregate.endpoint();
			String structureHash= swaggerHashUtil.computeStructureHash(
				endpoint,
				parameters,
				requests,
				responses,
				securities
			);
			endpoint.updateStructureHash(structureHash);
			//생성 정보 세팅
			endpoint.markCreated(aggregate.createdAt(),member,snapshot);
			//이전 endpoint 찾기
			String key=endpoint.getPath()+"|"+endpoint.getMethod();
			Endpoint prev=prevMap.get(key);
			//diff 계산
			endpoint.applyDiff(prev);
			//endpoint 저장
			Endpoint saved=endpointRepository.save(endpoint);
			savedEndpoints.add(saved);

			//하위 요소 저장
			swaggerEndpointSecurityRepository.saveAll(securities);
			swaggerParameterRepository.saveAll(parameters);
			swaggerRequestRepository.saveAll(requests);
			swaggerResponseRepository.saveAll(responses);
		}
		return savedEndpoints;
	}


	/**
	 * 삭제된 endpoint 감지
	 * @param prev
	 * @param member
	 * @param snapshot
	 * @return
	 */
	public Endpoint markDeleted(
		Endpoint prev,
		Member member,
		SwaggerSnapshot snapshot
	){
		return Endpoint.builder()
			.path(prev.getPath())
			.method(prev.getMethod())
			.summary(prev.getSummary())
			.description(prev.getDescription())
			.isChanged(true)
			.changeType(ChangeType.DELETED)
			.updatedBy(member)
			.updatedAt(LocalDateTime.now())
			.snapshot(snapshot)
			.build();
	}



}
