package com.gildongmu.gildongmu_backend.user.repository;

import java.util.Optional;
import com.gildongmu.gildongmu_backend.user.entity.Provider;
import com.gildongmu.gildongmu_backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    boolean existsByNickname(String nickname);
}
