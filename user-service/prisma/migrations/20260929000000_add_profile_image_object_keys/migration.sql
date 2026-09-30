ALTER TABLE "user_profiles"
ADD COLUMN "avatar_object_key" VARCHAR(512),
ADD COLUMN "avatar_variants" JSONB,
ADD COLUMN "cover_object_key" VARCHAR(512),
ADD COLUMN "cover_variants" JSONB;
