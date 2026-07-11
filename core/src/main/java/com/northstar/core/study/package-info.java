/**
 * Study — a capture-first log of study effort. A session records what was
 * practiced (skill), for how long, and with what result; a mock test is just a
 * session with {@code kind=MOCK} and a score. Entries arrive through AI capture
 * or assistant tools as natural language and are stored already resolved.
 * Skills come from a seeded vocabulary unioned with values already logged (the
 * finance-category pattern) so the taxonomy converges instead of drifting.
 * Deliberately absent: timers, streaks, lesson content — the log exists to
 * answer "how much, what, and is it moving toward the exam", not to gamify.
 */
package com.northstar.core.study;
