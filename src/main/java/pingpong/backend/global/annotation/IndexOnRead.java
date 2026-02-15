package pingpong.backend.global.annotation;

import pingpong.backend.global.rag.indexing.enums.IndexSourceType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IndexOnRead {

    IndexSourceType sourceType();

    String teamId();

    String apiPath();

    String resourceId() default "";

    String pageSize() default "";

    String startCursor() default "";

    String condition() default "";

    boolean skipIfPageSizePresent() default true;

    boolean skipIfStartCursorPresent() default true;
}
