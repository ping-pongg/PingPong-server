package pingpong.backend.global.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.repository.MemberRepository;
import pingpong.backend.global.auth.dto.CustomUserDetails;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        Member member = memberRepository.getByEmail(email);

        // UserDetails에 반환하면 AuthenticationManager가 검증
        return new CustomUserDetails(member);
    }
}
