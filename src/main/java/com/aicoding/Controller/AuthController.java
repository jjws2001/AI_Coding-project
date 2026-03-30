package com.aicoding.Controller;

import com.aicoding.Entity.model.CustomOAuth2User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {

        if (oAuth2User == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", oAuth2User.getId());
        userInfo.put("username", oAuth2User.getName());
        userInfo.put("email", oAuth2User.getEmail());
        userInfo.put("avatarUrl", oAuth2User.getAvatarUrl());
        userInfo.put("githubId", oAuth2User.getGithubId());

        return ResponseEntity.ok(userInfo);
    }

    /**
     * 检查认证状态
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkAuth(
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {

        Map<String, Object> response = new HashMap<>();

        if (oAuth2User != null) {
            response.put("authenticated", true);
            response.put("userId", oAuth2User.getId());
            response.put("username", oAuth2User.getName());
        } else {
            response.put("authenticated", false);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }
}