package pingpong.backend.global.annotation.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import pingpong.backend.global.annotation.IndexOnRead;
import pingpong.backend.global.rag.indexing.config.IndexingProperties;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;
import pingpong.backend.global.rag.indexing.dto.IndexJob;
import pingpong.backend.global.rag.indexing.job.IndexJobPublisher;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IndexOnReadResolver {

    private final IndexJobPublisher indexJobPublisher;
    private final IndexingProperties properties;

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @AfterReturning(pointcut = "@annotation(indexOnRead)", returning = "result")
    public void publishJobAfterRead(JoinPoint joinPoint, IndexOnRead indexOnRead, Object result) {
        try {
            if (!properties.isEnabled()) {
                return;
            }
            if (!(result instanceof JsonNode jsonNode)) {
                log.warn("VECTORIZE: @IndexOnRead expects JsonNode return type but got {}",
                        result == null ? "null" : result.getClass().getSimpleName());
                return;
            }

            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            Object[] args = joinPoint.getArgs();

            IndexSourceType sourceType = indexOnRead.sourceType();
            Long teamId = castToLong(evaluateExpression(indexOnRead.teamId(), method, args));
            String apiPath = castToString(evaluateExpression(indexOnRead.apiPath(), method, args));
            String resourceId = castToNullableString(evaluateExpression(indexOnRead.resourceId(), method, args));

            if (sourceType == null || teamId == null || apiPath == null || apiPath.isBlank()) {
                log.warn("VECTORIZE: invalid annotation values sourceType={} teamId={} apiPath={}", sourceType, teamId, apiPath);
                return;
            }

            if (!isConditionSatisfied(indexOnRead.condition(), method, args)) {
                return;
            }

            if (indexOnRead.skipIfPageSizePresent()) {
                Object pageSizeValue = evaluateExpression(indexOnRead.pageSize(), method, args);
                if (isPageSizeProvided(pageSizeValue)) {
                    return;
                }
            }

            if (indexOnRead.skipIfStartCursorPresent()) {
                Object startCursorValue = evaluateExpression(indexOnRead.startCursor(), method, args);
                if (startCursorValue instanceof String startCursor && !startCursor.isBlank()) {
                    return;
                }
            }

            indexJobPublisher.publish(new IndexJob(sourceType, teamId, apiPath, resourceId, jsonNode.deepCopy()));
        } catch (Exception e) {
            log.error("VECTORIZE: failed to publish async indexing job", e);
        }
    }

    private Object evaluateExpression(String expression, Method method, Object[] args) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);

        // SpEL 변수: #p0/#a0 = 첫 번째 파라미터, #p1/#a1 = 두 번째 파라미터, ... 또는 #파라미터이름
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
            if (parameterNames != null && i < parameterNames.length) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        return expressionParser.parseExpression(expression).getValue(context);
    }

    private boolean isConditionSatisfied(String condition, Method method, Object[] args) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        Object value = evaluateExpression(condition, method, args);
        if (value instanceof Boolean result) {
            return result;
        }
        return false;
    }

    private boolean isPageSizeProvided(Object pageSizeValue) {
        if (pageSizeValue == null) {
            return false;
        }
        if (pageSizeValue instanceof Number number) {
            return number.longValue() > 0;
        }
        if (pageSizeValue instanceof String text) {
            return !text.isBlank();
        }
        return true;
    }

    private Long castToLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String castToString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private String castToNullableString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
