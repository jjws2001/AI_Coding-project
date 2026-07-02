package com.aicoding.Controller;

import com.aicoding.ai.harness.HarnessHeartbeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/harness")
@RequiredArgsConstructor
public class HarnessController {

    private final HarnessHeartbeatService heartbeatService;

    @GetMapping("/heartbeat")
    public HarnessHeartbeatService.HeartbeatReport heartbeat() {
        return heartbeatService.latest();
    }
}
