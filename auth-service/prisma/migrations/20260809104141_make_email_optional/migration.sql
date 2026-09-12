-- AlterTable
ALTER TABLE "accounts" ALTER COLUMN "email" DROP NOT NULL;

-- AlterTable
ALTER TABLE "oauth_accounts" ALTER COLUMN "provider_email" DROP NOT NULL;
