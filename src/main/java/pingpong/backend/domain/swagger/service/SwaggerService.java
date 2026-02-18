package pingpong.backend.domain.swagger.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
import pingpong.backend.domain.swagger.dto.EndpointResponse;
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
	public List<EndpointResponse> syncSwagger(Long serverId,Member member){
		String swaggerJsonUrl=swaggerUrlResolver.resolveSwaggerUrl(serverService.getServer(serverId).getSwaggerURI());
		JsonNode swaggerJson=swaggerParser.fetchJson(swaggerJsonUrl);
		//전체 스펙 해시 계산
		String specHash=swaggerHashUtil.generateSpecHash(swaggerJson);
		//기존 최신 스냅샷 조회
		Optional<SwaggerSnapshot> latest=swaggerSnapshotRepository.findTopByServerIdOrderByIdDesc(serverId);

		if(latest.isPresent() && latest.get().getSpecHash().equals(specHash)){
			return null;
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
		List<Endpoint> endpoints=saveAggregates(aggregates,member);

		return endpoints.stream()
			.map(EndpointResponse::toDto)
			.toList();

	}

	private List<Endpoint> saveAggregates(List<EndpointAggregate> aggregates,Member member){
		List<Endpoint> savedEndpoints=new ArrayList<>();

		for(EndpointAggregate aggregate:aggregates){
			Endpoint endpoint=aggregate.endpoint();

			endpoint.markCreated(aggregate.createdAt(),member);
			Endpoint saved=endpointRepository.save(endpoint);
			savedEndpoints.add(saved);

			swaggerParameterRepository.saveAll(aggregate.parameters());
			swaggerRequestRepository.saveAll(aggregate.requests());
			swaggerResponseRepository.saveAll(aggregate.responses());
		}
		return savedEndpoints;
	}

	/**
	 * 기존 specHash값과 비교하여 변경된 부분이 있는지 확인
	 * @param newSpecHash
	 * @param serverId
	 */
	private boolean isSpecChanged(String newSpecHash,Long serverId,JsonNode swaggerJson) {
		return swaggerSnapshotRepository
			.findTopByServerIdOrderByIdDesc(serverId)
			.map(snapshot->!Objects.equals(snapshot.getSpecHash(),newSpecHash))
			.orElse(true);
	}


	/**
	 * swagger request, response 저장
	 * @param operationNode
	 * @param endpoint
	 */
	private void attachRequestAndResponses(JsonNode operationNode,
		Endpoint endpoint){
		List<SwaggerRequest> requests=swaggerParser.extractRequests(operationNode,endpoint);
		List<SwaggerResponse> responses=swaggerParser.extractResponses(operationNode,endpoint);

		swaggerRequestRepository.saveAll(requests);
		swaggerResponseRepository.saveAll(responses);

	}


}
