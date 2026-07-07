/**
 * File storage (memos pattern): an immutable, sha256-deduplicated metadata row
 * per file; the bytes' location is the row's storage type — DATABASE (bytea)
 * today, LOCAL/S3 as future backends behind the same service API.
 */
package com.northstar.core.attachment;
