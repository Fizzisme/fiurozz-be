-- Verified no duplicate display_name values exist before applying (see PR discussion).
DROP INDEX "user_profiles_display_name_idx";

-- AlterTable
ALTER TABLE "user_profiles" ADD CONSTRAINT "user_profiles_display_name_key" UNIQUE ("display_name");
