package pingpong.backend.domain.eval.enums;

public enum EvalStatus {
    DONE,     // Judge 파싱 성공, 모든 점수 저장
    PARTIAL,  // JSON 파싱 실패했으나 raw 응답을 judge_reason_json에 보존, 점수는 null
    FAILED    // Judge 호출 자체 실패 (네트워크, timeout 등)
}
