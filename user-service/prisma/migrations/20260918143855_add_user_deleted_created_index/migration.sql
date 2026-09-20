-- CreateIndex
CREATE INDEX "users_deleted_at_created_at_idx" ON "users"("deleted_at", "created_at");
