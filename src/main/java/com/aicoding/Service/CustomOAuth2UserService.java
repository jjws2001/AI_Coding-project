package com.aicoding.Service;

import com.aicoding.Entity.model.CustomOAuth2User;
import com.aicoding.Entity.model.User;
import com.aicoding.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 调用父类方法获取OAuth2User
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 获取访问令牌
        String accessToken = userRequest.getAccessToken().getTokenValue();

        // 处理用户信息
        return processOAuth2User(oAuth2User, accessToken);
    }

    /**
     * 处理OAuth2用户信息
     */
    private OAuth2User processOAuth2User(OAuth2User oAuth2User, String accessToken) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 提取GitHub用户信息
        String githubId = String.valueOf(attributes.get("id"));
        String username = (String) attributes.get("login");
        String email = (String) attributes.get("email");
        String avatarUrl = (String) attributes.get("avatar_url");

        log.info("Processing OAuth2 user: githubId={}, username={}", githubId, username);

        // 查找或创建用户
        User user = userRepository.findByGithubId(githubId)
                .map(existingUser -> updateExistingUser(existingUser, username, email, avatarUrl, accessToken))
                .orElseGet(() -> createNewUser(githubId, username, email, avatarUrl, accessToken));

        // 返回自定义的OAuth2User实现
        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    /**
     * 更新现有用户
     */
    private User updateExistingUser(User user, String username, String email,
                                    String avatarUrl, String accessToken) {
        user.setUsername(username);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        user.setGithubAccessToken(accessToken);
        user.setLastLoginAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    /**
     * 创建新用户
     */
    private User createNewUser(String githubId, String username, String email,
                               String avatarUrl, String accessToken) {
        User newUser = new User();
        newUser.setGithubId(githubId);
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setAvatarUrl(avatarUrl);
        newUser.setGithubAccessToken(accessToken);
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setLastLoginAt(LocalDateTime.now());

        log.info("Creating new user: githubId={}, username={}", githubId, username);

        return userRepository.save(newUser);
    }
}
