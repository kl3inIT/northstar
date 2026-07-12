# Durable Web Sessions Plan

1. Add the Spring Session JDBC starter and Flyway-owned PostgreSQL schema.
2. Configure a 30-day idle timeout and persistent session cookie.
3. Verify configuration binding, cookie lifetime, JDBC persistence, protected
   API access, and logout invalidation.
4. Consolidate the behavior into the authentication spec, test matrix,
   architecture, decision, and roadmap.
