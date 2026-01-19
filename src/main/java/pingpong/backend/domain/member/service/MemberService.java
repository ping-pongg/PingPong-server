package pingpong.backend.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.MemberErrorCode;
import pingpong.backend.domain.member.dto.MemberRegisterRequest;
import pingpong.backend.domain.member.dto.MemberResponse;
import pingpong.backend.domain.member.dto.MemberSearchResponse;
import pingpong.backend.domain.member.repository.MemberRepository;
import pingpong.backend.global.exception.CustomException;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원가입
     */
    public MemberResponse register(MemberRegisterRequest registerRequest) {
        checkDuplicateEmail(registerRequest);

        Member member = Member.register(registerRequest, passwordEncoder);
        memberRepository.save(member);

        return MemberResponse.of(member);
    }

    /**
     * 회원 DTO 단건 조회
     */
    public MemberResponse find(Long memberId) {
        Member member = memberRepository.getById(memberId);
        return MemberResponse.of(member);
    }

    /**
     * 이메일로 회원 엔티티 조회
     */
    public Member findByEmail(String email) {
        return memberRepository.getByEmail(email);
    }

    /**
     * 이메일 중복 체크
     */
    private void checkDuplicateEmail(MemberRegisterRequest registerRequest) {
        if (memberRepository.existsByEmail(registerRequest.email())) {
            throw new CustomException(MemberErrorCode.EMAIL_DUPLICATED);
        }
    }

    /**
     * 이메일 검색
     */
    @Transactional(readOnly = true)
    public List<MemberSearchResponse> findByEmailContaining(String keyword) {
        return memberRepository.findByEmailContainingIgnoreCase(keyword).stream()
                .map(MemberSearchResponse::of)
                .toList();
    }

    /**
     * 회원 엔티티 단건 조회
     */
    @Transactional(readOnly = true)
    public Member findById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 회원 엔티티 다건조회
     */
    @Transactional(readOnly = true)
    public List<Member> findAllByIds(List<Long> memberIds) {
        return memberRepository.findAllById(memberIds);
    }
}
