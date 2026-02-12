package pingpong.backend.domain.notion.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public class NotionDateValidator {

    private NotionDateValidator() {
    }

    public static boolean isIsoDateOrDateTime(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException ignore) {
        }
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException ignore) {
        }
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException ignore) {
        }
        return false;
    }
}
