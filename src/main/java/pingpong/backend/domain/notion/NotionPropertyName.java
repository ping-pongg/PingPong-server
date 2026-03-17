package pingpong.backend.domain.notion;

public enum NotionPropertyName {
    PLANNED_DATE("계획일"),
    COMPLETED_DATE("완료일");

    private final String value;

    NotionPropertyName(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
