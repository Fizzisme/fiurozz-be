// Structural check only (8-4-4-4-12 hex) -- not version/variant-specific,
// so it accepts UUIDv7 (this project's id format) as well as any other
// UUID version. Used to reject a malformed :id path param with a clean
// 400 before it hits a @db.Uuid column and Postgres throws a cast error.
export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
