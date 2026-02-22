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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.server.service.ServerService;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.SwaggerResponse;
import pingpong.backend.domain.swagger.SwaggerSnapshot;
import pingpong.backend.domain.swagger.dto.EndpointAggregate;
import pingpong.backend.domain.swagger.dto.EndpointDetailResponse;
import pingpong.backend.domain.swagger.dto.EndpointGroupResponse;
import pingpong.backend.domain.swagger.dto.EndpointResponse;
import pingpong.backend.domain.swagger.dto.ParameterResponse;
import pingpong.backend.domain.swagger.dto.ParameterSnapshotRes;
import pingpong.backend.domain.swagger.dto.RequestBodyResponse;
import pingpong.backend.domain.swagger.dto.ResponseBodyResponse;
import pingpong.backend.domain.swagger.enums.ChangeType;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
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
	public EndpointDetailResponse getEndpointDetails(Long endpointId) {
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

		return new EndpointDetailResponse(
			curr.getPath(),
			curr.getMethod(),
			parameterDiff,
			requestDiff,
			responseDiff
		);
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

		//기존 최신 스냅샷 조회
		Optional<SwaggerSnapshot> latest=swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId);

		if(latest.isPresent() && latest.get().getSpecHash().equals(specHash)){
			return List.of();
		}

		//이전 endpoint map 준비
		Map<String,Endpoint> prevMap;
		if(latest.isPresent()){
			List<Endpoint> prevEndpoints=
				endpointRepository.findBySnapshotId(latest.get().getId());
			prevMap=prevEndpoints.stream()
				.collect(Collectors.toMap(
					e->e.getPath()+"|"+e.getMethod(),
					Function.identity()
				));
		} else {
			prevMap = new HashMap<>();
		}

		List<EndpointAggregate> aggregates=swaggerParser.parseAll(swaggerJson);
		SwaggerSnapshot snapshot=SwaggerSnapshot.builder()
			.team(teamService.getTeam(teamId))
			.createdAt(LocalDateTime.now())
			.specHash(specHash)
			.endpointCount(aggregates.size())
			.rawJson(swaggerJson.toString())
			.build();

		swaggerSnapshotRepository.save(snapshot);
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

		// endpoint diff
		Map<String, List<EndpointResponse>> grouped =
			allEndpoints.stream()
				.filter(e -> Boolean.TRUE.equals(e.getIsChanged()))
				.map(EndpointResponse::toDto)
				.collect(Collectors.groupingBy(EndpointResponse::tag));

		return grouped.entrySet().stream()
			.map(e->new EndpointGroupResponse(
				e.getKey(),
				e.getValue()
			))
			.toList();
	}

	/**
	 * endpoint 단위 diff 비교 및 swagger endpoint,response,request,parameter 정규화 후 DB 저장
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
			Endpoint endpoint=aggregate.endpoint();

			endpoint.markCreated(aggregate.createdAt(),member,snapshot);

			String key=endpoint.getPath()+"|"+endpoint.getMethod();
			Endpoint prev=prevMap.get(key);
			endpoint.applyDiff(prev);

			Endpoint saved=endpointRepository.save(endpoint);
			savedEndpoints.add(saved);

			swaggerParameterRepository.saveAll(aggregate.parameters());
			swaggerRequestRepository.saveAll(aggregate.requests());
			swaggerResponseRepository.saveAll(aggregate.responses());
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
