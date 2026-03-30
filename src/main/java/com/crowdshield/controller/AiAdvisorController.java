package com.crowdshield.controller;

import com.crowdshield.service.AiAdvisorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiAdvisorController {

    private final AiAdvisorService aiAdvisorService;

    /**
     * GET /api/ai/analyse
     * Returns full venue analysis: per-zone danger levels, trends,
     * predictions, explanations, action commands, and a venue summary.
     */
    @GetMapping("/analyse")
    public Map<String, Object> analyseVenue() {
        return aiAdvisorService.analyseVenue();
    }

    /**
     * GET /api/ai/analyse/{zoneId}
     * Returns analysis for a single zone only (fast, real-time).
     */
    @GetMapping("/analyse/{zoneId}")
    public Map<String, Object> analyseZone(@PathVariable String zoneId) {
        return aiAdvisorService.analyseZone(zoneId);
    }
}
