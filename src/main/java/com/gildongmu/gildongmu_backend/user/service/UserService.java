package com.gildongmu.gildongmu_backend.user.service;

import com.gildongmu.gildongmu_backend.global.exception.CustomException;
import com.gildongmu.gildongmu_backend.global.exception.ErrorCode;
import com.gildongmu.gildongmu_backend.user.entity.User;
import com.gildongmu.gildongmu_backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void updateNickname(Long userId, String nickname) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }

        user.updateNickname(nickname);
        log.info("닉네임 업데이트: userId={}, nickname={}", userId, nickname);
    }
}
