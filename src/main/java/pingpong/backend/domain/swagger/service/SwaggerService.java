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


	private final SsrfGuard ssrfGuard;
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
		String swagger = teamService.getTeam(teamId).getSwagger();
		ssrfGuard.validate(swagger);
		String swaggerJsonUrl=swaggerUrlResolver.resolveSwaggerUrl(swagger);
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
	 * [Command] Swagger 동기화 및 저장 (변경이 있을 때만 실행)
	 */
	@Transactional
	public boolean syncSwagger(Long teamId, Member member) {
		String swagger = teamService.getTeam(teamId).getSwagger();
		ssrfGuard.validate(swagger);

		String swaggerJsonUrl = swaggerUrlResolver.resolveSwaggerUrl(teamService.getTeam(teamId).getSwagger());
		JsonNode swaggerJson = swaggerParser.fetchJson(swaggerJsonUrl);
		String specHash = swaggerHashUtil.generateSpecHash(swaggerJson);

		Optional<SwaggerSnapshot> latest = swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId);

		if(latest.isPresent()){
			String latestHash=latest.get().getSpecHash();
			if(specHash.equals(latestHash)){
				log.info("기존 스냅샷과 해시가 일치하여 동기화를 진행하지 않습니다. teamId:{}",teamId);
				return false;
			}
		}
		log.info("새로운 스웨거 스냅샷 생성을 시작합니다. teamId:{}",teamId);

		// 2. 이전 Endpoint Map 준비 (비교용)
		Map<String, Endpoint> prevMap = latest.map(snapshot ->
			endpointRepository.findBySnapshotId(snapshot.getId()).stream()
				.collect(Collectors.toMap(
					e -> e.getPath() + "|" + e.getMethod(),
					Function.identity()
				))
		).orElseGet(HashMap::new);

		// 3. 새로운 Snapshot 생성 및 저장
		List<EndpointAggregate> aggregates = swaggerParser.parseAll(swaggerJson);
		SwaggerSnapshot snapshot = SwaggerSnapshot.builder()
			.team(teamService.getTeam(teamId))
			.specHash(specHash)
			.createdAt(LocalDateTime.now())
			.endpointCount(aggregates.size())
			.rawJson(swaggerJson.toString())
			.build();

		swaggerSnapshotRepository.save(snapshot);

		// 4. 현재 Endpoint 저장 및 변경 감지
		List<Endpoint> endpoints = saveAggregates(aggregates, member, snapshot, prevMap);

		// 5. 삭제된 Endpoint 처리
		Set<String> currentKeys = endpoints.stream()
			.map(e -> e.getPath() + "|" + e.getMethod())
			.collect(Collectors.toSet());

		List<Endpoint> deletedEndpoints = prevMap.entrySet().stream()
			.filter(entry -> !currentKeys.contains(entry.getKey()))
			.map(entry -> markDeleted(entry.getValue(), member, snapshot))
			.map(endpointRepository::save)
			.toList();

		// 6. 전체 Endpoint 상태 업데이트 (연관 관계 등)
		List<Endpoint> allEndpoints = Stream.concat(endpoints.stream(), deletedEndpoints.stream()).toList();
		endpointService.unlinkChangedEndpoints(allEndpoints);
		return true;
	}

	/**
	 * [Query] 가장 최신의 스냅샷 데이터를 기반으로 변경 사항 결과 조회
	 * 새로고침 시 혹은 대시보드 진입 시 호출됩니다.
	 * * @param teamId 해당 팀의 ID
	 * @return 태그(Controller)별로 그룹화된 엔드포인트 리스트
	 */
	@Transactional(readOnly = true)
	public List<EndpointGroupResponse> getLatestSnapshotGrouped(Long teamId) {
		// 1. 해당 팀의 가장 최근 스냅샷을 조회
		SwaggerSnapshot latest = swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		// 2. 해당 스냅샷에 속한 모든 엔드포인트(Endpoint) 목록을 가져옴$
		List<Endpoint> allEndpoints = endpointRepository.findBySnapshotId(latest.getId());

		// 3. 엔드포인트들을 DTO로 변환하고, Tag(Swagger의 그룹)별로 그룹화(Grouping)
		Map<String, List<EndpointResponse>> groupedByTag = allEndpoints.stream()
			.map(EndpointResponse::toDto)
			.collect(Collectors.groupingBy(EndpointResponse::tag));

		// 4. 그룹화된 맵을 최종 Response 형태인 List<EndpointGroupResponse>로 변환
		return groupedByTag.entrySet().stream()
			.map(entry -> {
				String tag = entry.getKey();
				List<EndpointResponse> responses = entry.getValue();

				// 해당 그룹(태그) 내에 하나라도 변경(isChanged=true)된 엔드포인트가 있는지 판별
				boolean hasChanged = responses.stream()
					.anyMatch(ep -> Boolean.TRUE.equals(ep.isChanged()));

				// 그룹 전체의 상태 결정 (하나라도 바뀌었으면 CHANGED, 아니면 NOT_CHANGED)
				SnapshotDiffStatus groupStatus = hasChanged
					? SnapshotDiffStatus.CHANGED
					: SnapshotDiffStatus.NOT_CHANGED;

				return new EndpointGroupResponse(
					groupStatus,
					tag,
					responses
				);
			})
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
