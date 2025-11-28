package pingpong.backend.domain.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.MemberErrorCode;
import pingpong.backend.global.exception.CustomException;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    default Member getById(Long memberId) {
        return findById(memberId)
                .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    default Member getByEmail(String email) {
        return findByEmail(email)
                .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);
}

