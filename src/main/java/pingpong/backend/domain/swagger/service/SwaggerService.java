package pingpong.backend.domain.swagger.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.server.service.ServerService;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.SwaggerResponse;
import pingpong.backend.domain.swagger.SwaggerSnapshot;
import pingpong.backend.domain.swagger.dto.EndpointAggregate;
import pingpong.backend.domain.swagger.dto.EndpointGroupResponse;
import pingpong.backend.domain.swagger.dto.EndpointResponse;
import pingpong.backend.domain.swagger.enums.ChangeType;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerParameterRepository;
import pingpong.backend.domain.swagger.repository.SwaggerRequestRepository;
import pingpong.backend.domain.swagger.repository.SwaggerResponseRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.domain.swagger.util.SwaggerHashUtil;

@Service
@Slf4j
@RequiredArgsConstructor
public class SwaggerService {


	private final SwaggerUrlResolver swaggerUrlResolver;
	private final SwaggerParser swaggerParser;
	private final SwaggerHashUtil swaggerHashUtil;
	private final ServerService serverService;
	private final SwaggerRequestRepository swaggerRequestRepository;
	private final SwaggerResponseRepository swaggerResponseRepository;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;
	private final EndpointRepository endpointRepository;
	private final SwaggerParameterRepository swaggerParameterRepository;

	public JsonNode readSwaggerDocs(Member member,Long serverId){
		String swaggerJsonUrl=swaggerUrlResolver.resolveSwaggerUrl(serverService.getServer(serverId).getSwaggerURI());
		JsonNode swaggerJson=swaggerParser.fetchJson(swaggerJsonUrl);

		//snapshot 해시 값 생성
		String specHash=swaggerHashUtil.generateSpecHash(swaggerJson);
		// if(isSpecChanged(specHash,serverId,swaggerJson)){
		// 	normalizeJson(swaggerJson);
		// }

		return swaggerJson;
	}

	/**
	 * 가장 최신의 swagger를 업데이트해서 비교
	 * @param serverId
	 * @param member
	 * @return
	 */
	public List<EndpointGroupResponse> syncSwagger(Long serverId,Member member){
		String swaggerJsonUrl=swaggerUrlResolver.resolveSwaggerUrl(serverService.getServer(serverId).getSwaggerURI());
		JsonNode swaggerJson=swaggerParser.fetchJson(swaggerJsonUrl);
		//전체 스펙 해시 계산
		String specHash=swaggerHashUtil.generateSpecHash(swaggerJson);

		//기존 최신 스냅샷 조회
		Optional<SwaggerSnapshot> latest=swaggerSnapshotRepository.findTopByServerIdOrderByIdDesc(serverId);

		if(latest.isPresent() && latest.get().getSpecHash().equals(specHash)){
			return null;
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
			.server(serverService.getServer(serverId))
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
