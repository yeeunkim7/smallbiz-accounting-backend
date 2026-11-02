package com.smallbiz.reconciliation.receivables.matching;

import com.smallbiz.reconciliation.account.TenantAccess;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageService {
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final TenantAccess tenants;
    private final UsagePolicy policy;
    private final UsageClock clock;
    public UsageService(JdbcTemplate jdbc, TenantAccess tenants, UsagePolicy policy, UsageClock clock) {
        this.jdbc = jdbc; this.tenants = tenants; this.policy = policy; this.clock = clock;
    }
    public Summary current(UUID tenant) {
        String plan = tenants.require(tenant).plan();
        Instant now = clock.instant();
        LocalDate month = month(now);
        var counts = jdbc.query("SELECT completed FROM receivable_monthly_usage WHERE tenant_id=? AND usage_month=?",
                (rs,row) -> rs.getInt(1), tenant, month);
        return summary(month, plan, counts.isEmpty() ? 0 : counts.getFirst());
    }
    // The matching run/key must be acquired first. This method cannot create an independent transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    Ticket approve(UUID tenant) {
        LocalDate month = month(clock.instant());
        jdbc.update("""
                INSERT INTO receivable_monthly_usage (tenant_id,usage_month,limit_count) VALUES (?,?,0)
                ON CONFLICT (tenant_id,usage_month) DO NOTHING
                """, tenant, month);
        int completed = jdbc.queryForObject("""
                SELECT completed FROM receivable_monthly_usage WHERE tenant_id=? AND usage_month=? FOR UPDATE
                """, Integer.class, tenant, month);
        Instant now = clock.instant();
        if (!month(now).equals(month)) throw MatchingException.monthChanged();
        String plan = tenants.require(tenant).plan();
        int limit = policy.limit(plan);
        if (completed >= limit) {
            Duration untilReset = Duration.between(now, reset(month));
            throw new UsageException(summary(month, plan, completed),
                    Math.max(1, untilReset.getSeconds() + (untilReset.getNano() > 0 ? 1 : 0)));
        }
        // Preserve historic successes after policy reductions while enforcing the current admission limit.
        jdbc.update("UPDATE receivable_monthly_usage SET limit_count=? WHERE tenant_id=? AND usage_month=?",
                Math.max(completed, limit), tenant, month);
        return new Ticket(month, plan, policy.version(), limit, now);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    void complete(UUID tenant, UUID run, Ticket ticket) {
        jdbc.update("""
                INSERT INTO receivable_matching_usage_event
                (tenant_id,run_id,usage_month,plan,policy_version,limit_count,approved_at) VALUES (?,?,?,?,?,?,?)
                """, tenant, run, ticket.month(), ticket.plan(), ticket.policyVersion(), ticket.limit(),
                OffsetDateTime.ofInstant(ticket.approvedAt(), ZoneOffset.UTC));
        int updated = jdbc.update("""
                UPDATE receivable_monthly_usage SET completed=completed+1
                WHERE tenant_id=? AND usage_month=? AND completed<limit_count
                """, tenant, ticket.month());
        if (updated != 1) throw MatchingException.storage();
    }
    private Summary summary(LocalDate month, String plan, int completed) {
        int limit = policy.limit(plan);
        return new Summary(YearMonth.from(month).toString(), ZONE.getId(), plan, policy.version(), limit,
                completed, Math.max(0, limit-completed), reset(month));
    }
    private static LocalDate month(Instant now) { return now.atZone(ZONE).toLocalDate().withDayOfMonth(1); }
    private static Instant reset(LocalDate month) { return month.plusMonths(1).atStartOfDay(ZONE).toInstant(); }
    public record Summary(String month, String timezone, String plan, String policyVersion, int limit,
            int completed, int remaining, Instant resetAt) {}
    record Ticket(LocalDate month, String plan, String policyVersion, int limit, Instant approvedAt) {}
}
