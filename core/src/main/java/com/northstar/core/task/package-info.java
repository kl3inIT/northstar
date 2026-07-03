/**
 * Tasks — the todo store. Friction rules from the methodology: a task is small
 * (&lt;= 2h) and answerable as a yes/no "done?". One table; list (Today), kanban and
 * calendar are later views over the same rows: {@code status} drives board columns,
 * {@code due_date}/{@code due_time} drive Today and calendar placement. Tasks arrive
 * through the global AI capture (classified) or plain quick-add; a discipline FK
 * joins the LDP spine when that module lands.
 */
package com.northstar.core.task;
