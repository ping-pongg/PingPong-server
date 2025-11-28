package pingpong.backend.domain.member;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NaturalId;
import org.springframework.security.crypto.password.PasswordEncoder;
import pingpong.backend.domain.member.dto.MemberRegisterRequest;
import static java.util.Objects.requireNonNull;

@Entity
@Getter
@Builder
@Table
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @NaturalId
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String nickname;

    public static Member register(MemberRegisterRequest registerRequest, PasswordEncoder passwordEncoder) {
        return Member.builder()
                .email(requireNonNull(registerRequest.email()))
                .password(requireNonNull(passwordEncoder.encode(registerRequest.password())))
                .nickname(registerRequest.nickname())
                .build();
    }
}
