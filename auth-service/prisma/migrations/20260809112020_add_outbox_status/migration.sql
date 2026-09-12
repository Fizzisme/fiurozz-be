-- DropIndex
DROP INDEX "outbox_events_processed_at_idx";

-- AlterTable
ALTER TABLE "outbox_events" ADD COLUMN     "status" TEXT NOT NULL DEFAULT 'pending';

-- CreateIndex
CREATE INDEX "outbox_events_status_processed_at_created_at_idx" ON "outbox_events"("status", "processed_at", "created_at");
