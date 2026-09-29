package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.response.DashboardActivities;
import com.priceintel.backend.dto.response.DashboardCharts;
import com.priceintel.backend.dto.response.DashboardSummaryItem;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * The Dashboard's numbers, read straight from the company's own data.
 *
 * <p>Until now the Dashboard ran on sample figures in the frontend — 1,284
 * products and $482,900 of revenue for everyone, whatever they actually had.
 * Every figure here is the company's own, and where a figure cannot honestly be
 * produced it is left out rather than invented.</p>
 *
 * <p>Written as SQL rather than through the entity mappings: these are eight
 * counts and six groupings across a dozen tables, and loading the rows into
 * memory to count them would cost far more than the answers are worth.</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbc;

    /** Which company is asking; null means "no company", which shows nothing. */
    private Long tenantId() {
        return TenantContext.getTenantId();
    }

    // ---------- KPI tiles ----------

    @Transactional(readOnly = true)
    public List<DashboardSummaryItem> summary() {
        Long tenant = tenantId();
        if (tenant == null) {
            return List.of();
        }
        Map<String, Object> p = Map.of("t", tenant);

        long products = count("SELECT count(*) FROM products WHERE tenant_id = :t AND status <> 'ARCHIVED'", p);
        long productsBefore = count("SELECT count(*) FROM products WHERE tenant_id = :t AND status <> 'ARCHIVED' "
                + "AND created_at < now() - interval '30 days'", p);

        long matched = count("SELECT count(DISTINCT product_id) FROM competitor_listings "
                + "WHERE tenant_id = :t AND match_status IN ('MATCHED','EQUIVALENT')", p);
        long matchedBefore = count("SELECT count(DISTINCT product_id) FROM competitor_listings "
                + "WHERE tenant_id = :t AND match_status IN ('MATCHED','EQUIVALENT') "
                + "AND created_at < now() - interval '30 days'", p);

        long listings = count("SELECT count(*) FROM competitor_listings "
                + "WHERE tenant_id = :t AND match_status <> 'REJECTED'", p);
        long listingsBefore = count("SELECT count(*) FROM competitor_listings "
                + "WHERE tenant_id = :t AND match_status <> 'REJECTED' "
                + "AND created_at < now() - interval '30 days'", p);

        long searchesToday = count("SELECT count(*) FROM search_history "
                + "WHERE tenant_id = :t AND created_at >= date_trunc('day', now())", p);
        long searchesYesterday = count("SELECT count(*) FROM search_history WHERE tenant_id = :t "
                + "AND created_at >= date_trunc('day', now()) - interval '1 day' "
                + "AND created_at <  date_trunc('day', now())", p);

        long recommendations = count("SELECT count(*) FROM price_recommendations r "
                + "JOIN products pr ON pr.id = r.product_id "
                + "WHERE pr.tenant_id = :t AND r.status IN ('DRAFT','PENDING_APPROVAL','APPROVED')", p);
        long recommendationsBefore = count("SELECT count(*) FROM price_recommendations r "
                + "JOIN products pr ON pr.id = r.product_id "
                + "WHERE pr.tenant_id = :t AND r.status IN ('DRAFT','PENDING_APPROVAL','APPROVED') "
                + "AND r.created_at < now() - interval '30 days'", p);

        // The newest snapshot of each product, averaged — not an average of every
        // snapshot ever taken, which would drag today's margin towards history.
        BigDecimal margin = number("SELECT round(avg(contribution_margin_pct), 1) FROM ("
                + "  SELECT DISTINCT ON (s.product_id) s.contribution_margin_pct"
                + "  FROM profitability_snapshots s JOIN products pr ON pr.id = s.product_id"
                + "  WHERE pr.tenant_id = :t ORDER BY s.product_id, s.snapshot_date DESC) x", p);
        BigDecimal marginBefore = number("SELECT round(avg(contribution_margin_pct), 1) FROM ("
                + "  SELECT DISTINCT ON (s.product_id) s.contribution_margin_pct"
                + "  FROM profitability_snapshots s JOIN products pr ON pr.id = s.product_id"
                + "  WHERE pr.tenant_id = :t AND s.snapshot_date <= current_date - 30"
                + "  ORDER BY s.product_id, s.snapshot_date DESC) x", p);

        long changes7d = count(priceChangeCountSql("interval '7 days'", "interval '0 days'"), p);
        long changesPrior7d = count(priceChangeCountSql("interval '14 days'", "interval '7 days'"), p);

        long monitors = count("SELECT count(*) FROM monitors WHERE tenant_id = :t AND enabled", p);

        List<DashboardSummaryItem> out = new ArrayList<>();
        out.add(tile("totalProducts", "Total Products", products, "box", "primary",
                delta(products, productsBefore)));
        out.add(tile("matchedProducts", "Matched Products", matched, "check", "success",
                delta(matched, matchedBefore)));
        out.add(tile("competitorListings", "Competitor Listings", listings, "list", "info",
                delta(listings, listingsBefore)));
        out.add(tile("todaySearches", "Today's Searches", searchesToday, "search", "warning",
                delta(searchesToday, searchesYesterday)));
        out.add(tile("aiRecommendations", "AI Recommendations", recommendations, "cpu", "primary",
                delta(recommendations, recommendationsBefore)));
        DashboardSummaryItem marginTile = tile("avgMargin", "Average Margin %",
                margin == null ? BigDecimal.ZERO : margin, "percent", "success",
                margin == null || marginBefore == null ? null
                        : delta(margin.doubleValue(), marginBefore.doubleValue()));
        marginTile.setSuffix("%");
        out.add(marginTile);
        out.add(tile("priceChanges", "Price Changes (7 days)", changes7d, "trend", "info",
                delta(changes7d, changesPrior7d)));
        out.add(tile("activeMonitors", "Active Monitors", monitors, "check", "primary", null));
        return out;
    }

    /**
     * Price-change events on listings this company follows — the same rule the
     * Price Changes page uses: searched by it, or attached to its products.
     */
    private String priceChangeCountSql(String from, String until) {
        return "SELECT count(*) FROM price_change_events e "
                + "WHERE e.observed_at >= now() - " + from + " AND e.observed_at < now() - " + until + " "
                + "AND (EXISTS (SELECT 1 FROM tenant_tracked_items ti WHERE ti.tenant_id = :t "
                + "             AND ti.marketplace = e.marketplace AND ti.storefront = e.storefront "
                + "             AND ti.marketplace_item_id = e.marketplace_item_id) "
                + "  OR EXISTS (SELECT 1 FROM competitor_listings cl WHERE cl.tenant_id = :t "
                + "             AND cl.marketplace = e.marketplace AND cl.storefront = e.storefront "
                + "             AND cl.marketplace_item_id = e.marketplace_item_id))";
    }

    // ---------- charts ----------

    @Transactional(readOnly = true)
    public DashboardCharts charts() {
        Long tenant = tenantId();
        if (tenant == null) {
            return DashboardCharts.builder().build();
        }
        Map<String, Object> p = Map.of("t", tenant);

        List<String> monthLabels = new ArrayList<>();
        List<BigDecimal> ours = new ArrayList<>();
        List<BigDecimal> market = new ArrayList<>();
        jdbc.query(MONTHS
                + "SELECT m.label,"
                + "  (SELECT round(avg(s.selling_price), 2) FROM profitability_snapshots s"
                + "     JOIN products pr ON pr.id = s.product_id"
                + "    WHERE pr.tenant_id = :t AND date_trunc('month', s.snapshot_date) = m.m) AS ours,"
                + "  (SELECT round(avg(ls.landed_price), 2) FROM listing_price_snapshots ls"
                + "     JOIN competitor_listings cl ON cl.id = ls.listing_id"
                + "    WHERE cl.tenant_id = :t AND date_trunc('month', ls.observed_at)::date = m.m) AS market"
                + " FROM months m ORDER BY m.m", p, rs -> {
                    monthLabels.add(rs.getString("label"));
                    ours.add(rs.getBigDecimal("ours"));
                    market.add(rs.getBigDecimal("market"));
                });

        List<String> profitLabels = new ArrayList<>();
        List<BigDecimal> gross = new ArrayList<>();
        List<BigDecimal> net = new ArrayList<>();
        jdbc.query(MONTHS
                // Gross uses the purchase cost that applied on the snapshot's own
                // date, not today's — a supplier price change must not rewrite
                // last quarter's profit.
                + "SELECT m.label,"
                + "  (SELECT round(avg(s.selling_price - coalesce(cp.cogs, 0)), 2)"
                + "     FROM profitability_snapshots s JOIN products pr ON pr.id = s.product_id"
                + "     LEFT JOIN LATERAL (SELECT c.cogs FROM cost_profiles c"
                + "        WHERE c.product_id = s.product_id AND c.valid_from <= s.snapshot_date"
                + "        ORDER BY c.valid_from DESC LIMIT 1) cp ON true"
                + "    WHERE pr.tenant_id = :t AND date_trunc('month', s.snapshot_date) = m.m) AS gross,"
                + "  (SELECT round(avg(s.net_profit), 2) FROM profitability_snapshots s"
                + "     JOIN products pr ON pr.id = s.product_id"
                + "    WHERE pr.tenant_id = :t AND date_trunc('month', s.snapshot_date) = m.m) AS net"
                + " FROM months m ORDER BY m.m", p, rs -> {
                    profitLabels.add(rs.getString("label"));
                    gross.add(rs.getBigDecimal("gross"));
                    net.add(rs.getBigDecimal("net"));
                });

        return DashboardCharts.builder()
                .priceTrend(DashboardCharts.PriceTrend.builder()
                        .labels(monthLabels).ours(ours).market(market).build())
                .profitAnalysis(DashboardCharts.ProfitSeries.builder()
                        .labels(profitLabels).gross(gross).net(net).build())
                .marketplaceComparison(series(
                        "SELECT marketplace AS label, count(*) AS value FROM competitor_listings"
                        + " WHERE tenant_id = :t AND match_status <> 'REJECTED'"
                        + " GROUP BY marketplace ORDER BY count(*) DESC", p))
                .topCompetitors(series(
                        "SELECT seller AS label, count(*) AS value FROM competitor_listings"
                        + " WHERE tenant_id = :t AND match_status <> 'REJECTED'"
                        + "   AND seller IS NOT NULL AND seller <> ''"
                        + " GROUP BY seller ORDER BY count(*) DESC LIMIT 5", p))
                .categoryDistribution(series(
                        "SELECT coalesce(c.name, 'Uncategorised') AS label, count(*) AS value"
                        + " FROM products pr LEFT JOIN product_categories c ON c.id = pr.category_id"
                        + " WHERE pr.tenant_id = :t AND pr.status <> 'ARCHIVED'"
                        + " GROUP BY 1 ORDER BY count(*) DESC LIMIT 6", p))
                // Every day of the week is listed, including the quiet ones: a
                // gap in the line is information, a missing Sunday is a puzzle.
                .searchActivity(series(
                        "WITH days AS (SELECT generate_series(current_date - 6, current_date,"
                        + "                    interval '1 day')::date AS d)"
                        + " SELECT to_char(d, 'Dy') AS label,"
                        + "   (SELECT count(*) FROM search_history h"
                        + "     WHERE h.tenant_id = :t AND h.created_at::date = d) AS value"
                        + " FROM days ORDER BY d", p))
                .build();
    }

    /** Six months ending with this one, so a chart always has an x-axis. */
    private static final String MONTHS =
            "WITH months AS (SELECT to_char(d, 'Mon') AS label, d::date AS m"
            + " FROM generate_series(date_trunc('month', current_date) - interval '5 months',"
            + "                      date_trunc('month', current_date), interval '1 month') d) ";

    // ---------- activity lists ----------

    @Transactional(readOnly = true)
    public DashboardActivities activities() {
        Long tenant = tenantId();
        if (tenant == null) {
            return DashboardActivities.builder()
                    .searches(List.of()).alerts(List.of()).recommendations(List.of()).build();
        }
        Map<String, Object> p = Map.of("t", tenant);

        List<DashboardActivities.SearchItem> searches = jdbc.query(
                "SELECT id, query_text, matched_stage, match_count, created_at FROM search_history"
                + " WHERE tenant_id = :t ORDER BY created_at DESC LIMIT 5", p,
                (rs, i) -> {
                    Instant at = rs.getTimestamp("created_at").toInstant();
                    return DashboardActivities.SearchItem.builder()
                            .id(rs.getLong("id"))
                            .query(rs.getString("query_text"))
                            .type(readable(rs.getString("matched_stage")))
                            .results(rs.getInt("match_count"))
                            .time(ago(at)).at(at).build();
                });

        List<DashboardActivities.AlertItem> alerts = jdbc.query(
                "SELECT a.id, a.condition, pr.title, a.last_fired_at FROM alert_rules a"
                + " JOIN products pr ON pr.id = a.product_id"
                + " WHERE a.tenant_id = :t AND a.last_fired_at IS NOT NULL"
                + " ORDER BY a.last_fired_at DESC LIMIT 5", p,
                (rs, i) -> {
                    Instant at = rs.getTimestamp("last_fired_at").toInstant();
                    String condition = rs.getString("condition");
                    return DashboardActivities.AlertItem.builder()
                            .id(rs.getLong("id"))
                            .type(readable(condition))
                            .product(rs.getString("title"))
                            .severity(severity(condition))
                            .time(ago(at)).at(at).build();
                });

        List<DashboardActivities.RecommendationItem> recommendations = jdbc.query(
                "SELECT r.id, pr.title, r.recommended_price, r.confidence FROM price_recommendations r"
                + " JOIN products pr ON pr.id = r.product_id"
                + " WHERE pr.tenant_id = :t AND r.status IN ('DRAFT','PENDING_APPROVAL','APPROVED')"
                + " ORDER BY r.created_at DESC LIMIT 5", p,
                (rs, i) -> {
                    BigDecimal confidence = rs.getBigDecimal("confidence");
                    return DashboardActivities.RecommendationItem.builder()
                            .id(rs.getLong("id"))
                            .product(rs.getString("title"))
                            .price(rs.getBigDecimal("recommended_price"))
                            // Stored 0-100; the screen multiplies by 100 to print it.
                            .confidence(confidence == null ? null
                                    : confidence.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                            .risk(risk(confidence)).build();
                });

        return DashboardActivities.builder()
                .searches(searches).alerts(alerts).recommendations(recommendations).build();
    }

    // ---------- helpers ----------

    /** PRICE_DROP → "Price Drop". */
    static String readable(String enumName) {
        if (enumName == null || enumName.isBlank()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String word : enumName.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return out.toString();
    }

    /** How loudly a fired rule should read. A cheaper rival costs money today. */
    static String severity(String condition) {
        if (condition == null) {
            return "info";
        }
        return switch (condition) {
            case "PRICE_DROP", "MARGIN_BREACH" -> "danger";
            case "NEW_SELLER", "PRICE_GAP", "OUT_OF_STOCK", "DATA_STALE" -> "warning";
            default -> "info";
        };
    }

    /** How much to trust a suggested price, from the model's own confidence. */
    static String risk(BigDecimal confidencePercent) {
        if (confidencePercent == null) {
            return "High";
        }
        double c = confidencePercent.doubleValue();
        if (c >= 80) {
            return "Low";
        }
        return c >= 55 ? "Medium" : "High";
    }

    /**
     * Percentage change, or null when there is nothing to compare against —
     * everything looks like infinite growth from a base of zero.
     */
    static Double delta(double now, double before) {
        if (before <= 0) {
            return null;
        }
        return BigDecimal.valueOf((now - before) / before * 100)
                .setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** "2 min ago" — the same phrasing the screen already shows. */
    static String ago(Instant at) {
        if (at == null) {
            return null;
        }
        long minutes = Duration.between(at, Instant.now()).toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }

    private DashboardSummaryItem tile(String key, String label, Number value,
                                      String icon, String color, Double delta) {
        return DashboardSummaryItem.builder()
                .key(key).label(label)
                .value(value instanceof BigDecimal bd ? bd : BigDecimal.valueOf(value.doubleValue()))
                .icon(icon).color(color).delta(delta).build();
    }

    private long count(String sql, Map<String, Object> params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n == null ? 0L : n;
    }

    private BigDecimal number(String sql, Map<String, Object> params) {
        return jdbc.queryForObject(sql, params, BigDecimal.class);
    }

    private DashboardCharts.ValueSeries series(String sql, Map<String, Object> params) {
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        jdbc.query(sql, params, rs -> {
            labels.add(rs.getString("label"));
            BigDecimal v = rs.getBigDecimal("value");
            values.add(v == null ? BigDecimal.ZERO : v);
        });
        return DashboardCharts.ValueSeries.builder().labels(labels).values(values).build();
    }
}
