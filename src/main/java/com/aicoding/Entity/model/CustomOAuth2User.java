package com.aicoding.Entity.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter    // 自动生成所有字段的 getter 方法
@RequiredArgsConstructor    // 为 final 字段 生成构造函数
public class CustomOAuth2User implements OAuth2User {

    private final User user;    // 应用中持久化的用户数据（可能包含 ID、邮箱、头像、权限等）
    // // OAuth2 登录成功后，授权服务器返回的用户属性（例如 GitHub 返回的 { "login": "john", "email": "...", "avatar_url": "..." }）
    private final Map<String, Object> attributes;

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return user.getUsername();
    }

    public Long getId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    public String getAvatarUrl() {
        return user.getAvatarUrl();
    }

    public String getGithubId() {
        return user.getGithubId();
    }
}
