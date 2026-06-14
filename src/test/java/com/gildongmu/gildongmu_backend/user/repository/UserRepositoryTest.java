package com.gildongmu.gildongmu_backend.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gildongmu.gildongmu_backend.user.entity.Provider;
import com.gildongmu.gildongmu_backend.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void provider와_providerId로_회원을_조회한다() {
        User saved = userRepository.save(
                User.register(Provider.KAKAO, "kakao-123", "a@b.com"));

        var found = userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-123");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void 존재하지_않으면_빈_Optional을_반환한다() {
        var found = userRepository.findByProviderAndProviderId(Provider.NAVER, "none");

        assertThat(found).isEmpty();
    }
}
