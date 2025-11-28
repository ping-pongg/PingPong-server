package pingpong.backend.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.MemberErrorCode;
import pingpong.backend.domain.member.dto.MemberRegisterRequest;
import pingpong.backend.domain.member.dto.MemberResponse;
import pingpong.backend.domain.member.repository.MemberRepository;
import pingpong.backend.global.exception.CustomException;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder passwordEncoder;

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
     * 회원 단건 조회 (memberId 기준)
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
}
