package com.crowdshield.service;

import com.crowdshield.model.Alert;
import com.crowdshield.model.Incident;
import com.crowdshield.model.Zone;
import com.crowdshield.model.ZoneHistory;
import com.crowdshield.repository.AlertRepository;
import com.crowdshield.repository.IncidentRepository;
import com.crowdshield.repository.ZoneHistoryRepository;
import com.crowdshield.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Crowd Intelligence Engine
 * ─────────────────────────────
 * Analyses every zone with real crowd data and produces:
 *   - danger level (CRITICAL / HIGH / MODERATE / LOW)
 *   - plain-language explanation of WHY it's risky
 *   - specific action command for staff
 *   - 15-minute density prediction (rising/stable/falling)
 *   - venue-wide summary with top priority zone
 *
 * Uses Claude claude-haiku-4-5 when ANTHROPIC_API_KEY is set.
 * Falls back to rule-based analysis so the feature always works.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAdvisorService {

    private final ZoneRepository         zoneRepo;
    private final AlertRepository        alertRepo;
    private final IncidentRepository     incidentRepo;
    private final ZoneHistoryRepository  historyRepo;

    @Value("${anthropic.api-key:}")
    private String apiKey;

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-haiku-4-5-20251001";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /* ════════════════════════════════════════════════════════
       PUBLIC API
    ════════════════════════════════════════════════════════ */

    /**
     * Full venue analysis — returns a list of per-zone AI insights
     * plus a venue-wide summary. Called by GET /api/ai/analyse.
     */
    public Map<String, Object> analyseVenue() {
        List<Zone>     zones     = zoneRepo.findAll();
        List<Alert>    alerts    = alertRepo.findByAcknowledgedFalseOrderByCreatedAtDesc();
        List<Incident> incidents = incidentRepo.findByStatus(Incident.IncidentStatus.OPEN);

        // Build per-zone insights
        List<Map<String, Object>> zoneInsights = new ArrayList<>();
        for (Zone zone : zones) {
            Map<String, Object> insight = buildZoneInsight(zone, alerts, incidents);
            zoneInsights.add(insight);
        }

        // Sort: highest danger first
        zoneInsights.sort((a, b) ->
            Integer.compare(dangerRank(b.get("dangerLevel")),
                            dangerRank(a.get("dangerLevel"))));

        // Venue summary
        Map<String, Object> summary = buildSummary(zones, alerts, incidents, zoneInsights);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary",     summary);
        result.put("zones",       zoneInsights);
        result.put("generatedAt", LocalDateTime.now().toString());
        result.put("aiPowered",   apiKey != null && !apiKey.isBlank());
        return result;
    }

    /**
     * Quick zone-level check — called when a zone count changes significantly.
     * Returns insight for ONE zone only (fast, used for real-time updates).
     */
    public Map<String, Object> analyseZone(String zoneId) {
        return zoneRepo.findById(zoneId).map(zone -> {
            List<Alert>    alerts    = alertRepo.findByZoneId(zoneId);
            List<Incident> incidents = incidentRepo.findByZoneId(zoneId)
                .stream().filter(i -> i.getStatus() == Incident.IncidentStatus.OPEN)
                .collect(Collectors.toList());
            return buildZoneInsight(zone, alerts, incidents);
        }).orElse(Map.of("error", "Zone not found"));
    }

    /* ════════════════════════════════════════════════════════
       CORE ANALYSIS ENGINE
    ════════════════════════════════════════════════════════ */

    private Map<String, Object> buildZoneInsight(Zone zone,
                                                  List<Alert> allAlerts,
                                                  List<Incident> allIncidents) {
        double density = zone.getDensityPercentage();
        String trend   = computeTrend(zone);
        long   zoneIncs= allIncidents.stream()
                .filter(i -> zone.getId().equals(i.getZoneId())).count();
        long   zoneAlerts = allAlerts.stream()
                .filter(a -> zone.getId().equals(a.getZoneId())).count();
        int    prediction = predictIn15Min(zone, trend);

        String dangerLevel = computeDangerLevel(density, trend, zoneIncs, zone.getStatus());
        String explanation = buildExplanation(zone, density, trend, zoneIncs, zoneAlerts);
        String action      = buildAction(zone, density, trend, dangerLevel, zoneIncs);

        // Try AI enhancement if key is set
        if (apiKey != null && !apiKey.isBlank() &&
                (dangerLevel.equals("CRITICAL") || dangerLevel.equals("HIGH"))) {
            try {
                String aiText = callClaudeForZone(zone, density, trend, zoneIncs, prediction);
                if (aiText != null && !aiText.isBlank()) {
                    explanation = aiText;
                }
            } catch (Exception e) {
                log.warn("Claude call failed for zone {}: {}", zone.getName(), e.getMessage());
            }
        }

        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("zoneId",       zone.getId());
        insight.put("zoneName",     zone.getName());
        insight.put("location",     zone.getLocation());
        insight.put("currentCount", zone.getCurrentCount());
        insight.put("capacity",     zone.getCapacity());
        insight.put("density",      Math.round(density));
        insight.put("status",       zone.getStatus().name());
        insight.put("dangerLevel",  dangerLevel);
        insight.put("trend",        trend);
        insight.put("prediction15", prediction);   // predicted density in 15 min
        insight.put("explanation",  explanation);
        insight.put("action",       action);
        insight.put("openIncidents",zoneIncs);
        insight.put("activeAlerts", zoneAlerts);
        return insight;
    }

    /* ── Danger level ── */
    private String computeDangerLevel(double density, String trend,
                                       long incidents, Zone.ZoneStatus status) {
        if (status == Zone.ZoneStatus.EVACUATING)        return "EVACUATING";
        if (density >= 95 || incidents >= 2)              return "CRITICAL";
        if (density >= 80 || (density >= 70 && "RISING".equals(trend))) return "HIGH";
        if (density >= 60 || (density >= 50 && incidents > 0))          return "MODERATE";
        return "LOW";
    }

    /* ── Trend: compare last 5 history snapshots ── */
    private String computeTrend(Zone zone) {
        if (zone.getId() == null) return "STABLE";
        LocalDateTime since = LocalDateTime.now().minusMinutes(20);
        List<ZoneHistory> hist = historyRepo
                .findByZoneIdAndRecordedAtAfterOrderByRecordedAtAsc(zone.getId(), since);
        if (hist.size() < 2) return "STABLE";
        double first = hist.get(0).getDensityPct();
        double last  = hist.get(hist.size() - 1).getDensityPct();
        double delta = last - first;
        if (delta >= 8)  return "RISING";
        if (delta <= -8) return "FALLING";
        return "STABLE";
    }

    /* ── Predict density in 15 minutes ── */
    private int predictIn15Min(Zone zone, String trend) {
        double current = zone.getDensityPercentage();
        if (zone.getId() == null) return (int) current;
        LocalDateTime since = LocalDateTime.now().minusMinutes(20);
        List<ZoneHistory> hist = historyRepo
                .findByZoneIdAndRecordedAtAfterOrderByRecordedAtAsc(zone.getId(), since);
        if (hist.size() < 2) {
            // no history: extrapolate from trend label
            if ("RISING".equals(trend))   return (int) Math.min(100, current + 12);
            if ("FALLING".equals(trend))  return (int) Math.max(0,   current - 10);
            return (int) current;
        }
        // Linear extrapolation over 15 minutes
        double first  = hist.get(0).getDensityPct();
        double last   = hist.get(hist.size() - 1).getDensityPct();
        double ratePerMin = (last - first) / 20.0;   // change per minute over last 20 min
        double predicted  = current + (ratePerMin * 15);
        return (int) Math.min(100, Math.max(0, Math.round(predicted)));
    }

    /* ── Plain-language explanation ── */
    private String buildExplanation(Zone zone, double density, String trend,
                                     long incidents, long alerts) {
        List<String> reasons = new ArrayList<>();
        if (density >= 95)
            reasons.add("at " + (int)density + "% capacity — dangerously overcrowded");
        else if (density >= 80)
            reasons.add("at " + (int)density + "% capacity — close to limit");
        else if (density >= 60)
            reasons.add("at " + (int)density + "% capacity — moderately busy");
        else
            reasons.add("at " + (int)density + "% capacity — currently safe");

        if ("RISING".equals(trend))
            reasons.add("count is actively rising");
        else if ("FALLING".equals(trend))
            reasons.add("count is decreasing");
        else
            reasons.add("count is stable");

        if (incidents > 0)
            reasons.add(incidents + " open incident" + (incidents>1?"s":"") + " reported here");
        if (alerts > 0)
            reasons.add(alerts + " active alert" + (alerts>1?"s":""));
        if (zone.getStatus() == Zone.ZoneStatus.EVACUATING)
            reasons.add("evacuation in progress");

        return zone.getName() + " is " + String.join(", ", reasons) + ".";
    }

    /* ── Specific action command ── */
    private String buildAction(Zone zone, double density, String trend,
                                String dangerLevel, long incidents) {
        return switch (dangerLevel) {
            case "CRITICAL"   -> "🚨 CLOSE ENTRY NOW — Deploy staff to " + zone.getLocation()
                               + " immediately. Redirect crowd to nearest alternative zone.";
            case "HIGH"       -> "⚠ RESTRICT ENTRY — Limit new entries to " + zone.getName()
                               + ". Alert on-ground staff. "
                               + ("RISING".equals(trend) ? "Count rising — act now." : "Monitor every 2 minutes.");
            case "MODERATE"   -> "👁 WATCH CLOSELY — Station a staff member at "
                               + zone.getLocation() + ". "
                               + ("RISING".equals(trend)
                                  ? "Trend is rising — prepare entry restrictions."
                                  : "No action needed yet.");
            case "EVACUATING" -> "📢 ACTIVE EVACUATION — Guide people to exits calmly. Do not allow new entries.";
            default           -> "✅ All clear — standard monitoring sufficient.";
        };
    }

    /* ── Venue-wide summary ── */
    private Map<String, Object> buildSummary(List<Zone> zones,
                                              List<Alert> alerts,
                                              List<Incident> incidents,
                                              List<Map<String, Object>> insights) {
        long critical = insights.stream().filter(i -> "CRITICAL".equals(i.get("dangerLevel"))).count();
        long high     = insights.stream().filter(i -> "HIGH".equals(i.get("dangerLevel"))).count();
        long rising   = insights.stream().filter(i -> "RISING".equals(i.get("trend"))).count();
        int  totalCap = zones.stream().mapToInt(Zone::getCapacity).sum();
        int  totalPpl = zones.stream().mapToInt(Zone::getCurrentCount).sum();

        String topZone = insights.isEmpty() ? "None" :
                (String) insights.get(0).get("zoneName");
        String topDanger = insights.isEmpty() ? "LOW" :
                (String) insights.get(0).get("dangerLevel");

        String overallStatus;
        String overallMessage;
        if (critical > 0) {
            overallStatus  = "CRITICAL";
            overallMessage = critical + " zone(s) at critical capacity. Immediate intervention required.";
        } else if (high > 0) {
            overallStatus  = "WARNING";
            overallMessage = high + " zone(s) approaching dangerous levels. Increase staff presence.";
        } else if (rising >= 2) {
            overallStatus  = "WATCH";
            overallMessage = rising + " zones are filling up. Monitor entry points closely.";
        } else {
            overallStatus  = "SAFE";
            overallMessage = "Venue operating within safe limits. Maintain standard monitoring.";
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("overallStatus",  overallStatus);
        summary.put("overallMessage", overallMessage);
        summary.put("totalPeople",    totalPpl);
        summary.put("totalCapacity",  totalCap);
        summary.put("overallDensity", totalCap > 0 ? Math.round((double) totalPpl / totalCap * 100) : 0);
        summary.put("criticalZones",  critical);
        summary.put("highZones",      high);
        summary.put("risingZones",    rising);
        summary.put("openIncidents",  incidents.size());
        summary.put("activeAlerts",   alerts.size());
        summary.put("topPriorityZone",topZone);
        summary.put("topDangerLevel", topDanger);
        return summary;
    }

    /* ── Danger rank for sorting ── */
    private int dangerRank(Object level) {
        return switch (String.valueOf(level)) {
            case "CRITICAL"   -> 5;
            case "EVACUATING" -> 4;
            case "HIGH"       -> 3;
            case "MODERATE"   -> 2;
            default           -> 1;
        };
    }

    /* ════════════════════════════════════════════════════════
       CLAUDE API — only for CRITICAL/HIGH zones
    ════════════════════════════════════════════════════════ */

    private String callClaudeForZone(Zone zone, double density, String trend,
                                      long incidents, int predicted) {
        String prompt = String.format("""
            Crowd safety alert for '%s' zone at a live event:
            - Current occupancy: %d/%d people (%.0f%% capacity)
            - Trend: %s
            - Open incidents: %d
            - Predicted density in 15 minutes: %d%%

            In 2 sentences max, explain to a non-technical safety officer:
            1. Why this zone is dangerous right now
            2. What will happen if no action is taken

            Be direct and specific. No jargon.
            """,
            zone.getName(), zone.getCurrentCount(), zone.getCapacity(),
            density, trend, incidents, predicted
        );

        try {
            String body = "{"
                + "\"model\":\"" + MODEL + "\","
                + "\"max_tokens\":150,"
                + "\"messages\":[{\"role\":\"user\",\"content\":" + jsonStr(prompt) + "}]"
                + "}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) return extractText(resp.body());
        } catch (Exception e) {
            log.warn("Claude API error: {}", e.getMessage());
        }
        return null;
    }

    /* ── Helpers ── */
    private String jsonStr(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"")
                       .replace("\n","\\n").replace("\r","") + "\"";
    }

    private String extractText(String json) {
        int start = json.indexOf("\"text\":\"");
        if (start == -1) return null;
        start += 8;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && json.charAt(i-1) != '\\') break;
            if (c == '\\' && i+1 < json.length()) {
                char nx = json.charAt(++i);
                if      (nx == 'n') sb.append('\n');
                else if (nx == '"') sb.append('"');
                else if (nx == '\\') sb.append('\\');
            } else sb.append(c);
        }
        return sb.toString().trim();
    }
}
