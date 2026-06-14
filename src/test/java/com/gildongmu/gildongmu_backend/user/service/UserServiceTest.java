package com.gildongmu.gildongmu_backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import com.gildongmu.gildongmu_backend.global.exception.CustomException;
import com.gildongmu.gildongmu_backend.global.exception.ErrorCode;
import com.gildongmu.gildongmu_backend.user.entity.Provider;
import com.gildongmu.gildongmu_backend.user.entity.User;
import com.gildongmu.gildongmu_backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void 기존_사용자가_사용하지_않은_닉네임으로_변경하면_성공한다() {
        User user = User.register(Provider.KAKAO, "kakao-1", "a@b.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNickname("new-nickname")).thenReturn(false);

        userService.updateNickname(1L, "new-nickname");

        assertThat(user.getNickname()).isEqualTo("new-nickname");
    }

    @Test
    void 존재하지_않는_사용자면_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateNickname(1L, "new-nickname"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 중복된_닉네임이면_DUPLICATE_NICKNAME_예외를_던진다() {
        User user = User.register(Provider.KAKAO, "kakao-1", "a@b.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNickname("duplicate")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateNickname(1L, "duplicate"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_NICKNAME);
    }
}
