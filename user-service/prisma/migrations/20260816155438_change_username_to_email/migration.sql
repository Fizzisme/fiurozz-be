/*
  Warnings:

  - You are about to drop the column `username` on the `user_profiles` table. All the data in the column will be lost.
  - A unique constraint covering the columns `[email]` on the table `user_profiles` will be added. If there are existing duplicate values, this will fail.
  - Added the required column `email` to the `user_profiles` table without a default value. This is not possible if the table is not empty.

*/
-- DropIndex
DROP INDEX "user_profiles_username_key";

-- AlterTable
ALTER TABLE "user_profiles" DROP COLUMN "username",
ADD COLUMN     "email" TEXT NOT NULL;

-- CreateIndex
CREATE UNIQUE INDEX "user_profiles_email_key" ON "user_profiles"("email");
