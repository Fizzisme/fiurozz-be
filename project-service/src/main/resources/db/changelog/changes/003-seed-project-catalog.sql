--liquibase formatted sql

--changeset philia:003-seed-project-catalog dbms:postgresql
INSERT INTO project_categories (id, key, slug, title, icon, sort_order)
VALUES
    ('00000000-0000-4000-8000-000000000001', 'e-commerce', 'e-commerce', 'E-commerce', 'shopping-cart', 10),
    ('00000000-0000-4000-8000-000000000002', 'community-social', 'community-social', 'Community & Social', 'users', 20),
    ('00000000-0000-4000-8000-000000000003', 'education', 'education', 'Education', 'graduation-cap', 30),
    ('00000000-0000-4000-8000-000000000004', 'finance-fintech', 'finance-fintech', 'Finance & Fintech', 'credit-card', 40),
    ('00000000-0000-4000-8000-000000000005', 'healthcare-lifestyle', 'healthcare-lifestyle', 'Healthcare & Lifestyle', 'heart-pulse', 50),
    ('00000000-0000-4000-8000-000000000006', 'entertainment-media', 'entertainment-media', 'Entertainment & Media', 'play-circle', 60),
    ('00000000-0000-4000-8000-000000000007', 'ai-data', 'ai-data', 'AI & Data', 'brain-cog', 70),
    ('00000000-0000-4000-8000-000000000008', 'developer-tools', 'developer-tools', 'Developer Tools', 'code', 80)
ON CONFLICT (slug) DO UPDATE SET
    title = EXCLUDED.title,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE,
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP;

WITH seeded_sub_categories(id, category_slug, key, slug, title, sort_order) AS (
    VALUES
        ('10000000-0000-4000-8000-000000000001'::uuid, 'e-commerce', 'online-store', 'online-store', 'Online Store', 10),
        ('10000000-0000-4000-8000-000000000002'::uuid, 'e-commerce', 'market-place', 'market-place', 'Marketplace', 20),
        ('10000000-0000-4000-8000-000000000003'::uuid, 'e-commerce', 'booking-system', 'booking-system', 'Booking System', 30),
        ('10000000-0000-4000-8000-000000000004'::uuid, 'e-commerce', 'subscription-service', 'subscription-service', 'Subscription Service', 40),
        ('10000000-0000-4000-8000-000000000005'::uuid, 'community-social', 'forum', 'forum', 'Forum', 10),
        ('10000000-0000-4000-8000-000000000006'::uuid, 'community-social', 'chat-application', 'chat-application', 'Chat Application', 20),
        ('10000000-0000-4000-8000-000000000007'::uuid, 'community-social', 'social-network', 'social-network', 'Social Network', 30),
        ('10000000-0000-4000-8000-000000000008'::uuid, 'education', 'elearning-platform', 'elearning-platform', 'E-learning Platform', 10),
        ('10000000-0000-4000-8000-000000000009'::uuid, 'education', 'online-courses', 'online-courses', 'Online Courses', 20),
        ('10000000-0000-4000-8000-000000000010'::uuid, 'education', 'quiz-system', 'quiz-system', 'Quiz System', 30),
        ('10000000-0000-4000-8000-000000000011'::uuid, 'education', 'student-management', 'student-management', 'Student Management', 40),
        ('10000000-0000-4000-8000-000000000012'::uuid, 'finance-fintech', 'expense-tracker', 'expense-tracker', 'Expense Tracker', 10),
        ('10000000-0000-4000-8000-000000000013'::uuid, 'finance-fintech', 'payment-system', 'payment-system', 'Payment System', 20),
        ('10000000-0000-4000-8000-000000000014'::uuid, 'finance-fintech', 'crypto-dashboard', 'crypto-dashboard', 'Crypto Dashboard', 30),
        ('10000000-0000-4000-8000-000000000015'::uuid, 'finance-fintech', 'invoice-and-billing', 'invoice-and-billing', 'Invoice & Billing', 40),
        ('10000000-0000-4000-8000-000000000016'::uuid, 'healthcare-lifestyle', 'appointment-booking', 'appointment-booking', 'Appointment Booking', 10),
        ('10000000-0000-4000-8000-000000000017'::uuid, 'healthcare-lifestyle', 'fitness-tracker', 'fitness-tracker', 'Fitness Tracker', 20),
        ('10000000-0000-4000-8000-000000000018'::uuid, 'healthcare-lifestyle', 'health-records', 'health-records', 'Health Records', 30),
        ('10000000-0000-4000-8000-000000000019'::uuid, 'healthcare-lifestyle', 'mental-health-app', 'mental-health-app', 'Mental Health App', 40),
        ('10000000-0000-4000-8000-000000000020'::uuid, 'entertainment-media', 'streaming-platform', 'streaming-platform', 'Streaming Platform', 10),
        ('10000000-0000-4000-8000-000000000021'::uuid, 'entertainment-media', 'music-player', 'music-player', 'Music Player', 20),
        ('10000000-0000-4000-8000-000000000022'::uuid, 'entertainment-media', 'mini-games', 'mini-games', 'Mini Games', 30),
        ('10000000-0000-4000-8000-000000000023'::uuid, 'entertainment-media', 'podcast', 'podcast', 'Podcast Platform', 40),
        ('10000000-0000-4000-8000-000000000024'::uuid, 'ai-data', 'ai-chatbot', 'ai-chatbot', 'AI Chatbot', 10),
        ('10000000-0000-4000-8000-000000000025'::uuid, 'ai-data', 'recommendation-system', 'recommendation-system', 'Recommendation System', 20),
        ('10000000-0000-4000-8000-000000000026'::uuid, 'ai-data', 'data-visualization', 'data-visualization', 'Data Visualization', 30),
        ('10000000-0000-4000-8000-000000000027'::uuid, 'ai-data', 'ai-saas-tool', 'ai-saas-tool', 'AI SaaS Tool', 40),
        ('10000000-0000-4000-8000-000000000028'::uuid, 'developer-tools', 'component-library', 'component-library', 'Component Library', 10),
        ('10000000-0000-4000-8000-000000000029'::uuid, 'developer-tools', 'api-platform', 'api-platform', 'API Platform', 20),
        ('10000000-0000-4000-8000-000000000030'::uuid, 'developer-tools', 'code-snippet-manager', 'code-snippet-manager', 'Code Snippet Manager', 30),
        ('10000000-0000-4000-8000-000000000031'::uuid, 'developer-tools', 'dev-dashboard', 'dev-dashboard', 'Dev Dashboard', 40)
)
INSERT INTO project_sub_categories (id, category_id, key, slug, title, sort_order)
SELECT seeded.id, category.id, seeded.key, seeded.slug, seeded.title, seeded.sort_order
FROM seeded_sub_categories seeded
JOIN project_categories category ON category.slug = seeded.category_slug
ON CONFLICT (category_id, slug) DO UPDATE SET
    title = EXCLUDED.title,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE,
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO tags (id, slug, display_name, normalized_name)
VALUES
    ('20000000-0000-4000-8000-000000000001', 'next-js', 'Next.js', 'next.js'),
    ('20000000-0000-4000-8000-000000000002', 'typescript', 'TypeScript', 'typescript'),
    ('20000000-0000-4000-8000-000000000003', 'tailwind-css', 'Tailwind CSS', 'tailwind css'),
    ('20000000-0000-4000-8000-000000000004', 'react', 'React', 'react'),
    ('20000000-0000-4000-8000-000000000005', 'node-js', 'Node.js', 'node.js'),
    ('20000000-0000-4000-8000-000000000006', 'spring-boot', 'Spring Boot', 'spring boot'),
    ('20000000-0000-4000-8000-000000000007', 'postgresql', 'PostgreSQL', 'postgresql'),
    ('20000000-0000-4000-8000-000000000008', 'mongodb', 'MongoDB', 'mongodb'),
    ('20000000-0000-4000-8000-000000000009', 'docker', 'Docker', 'docker'),
    ('20000000-0000-4000-8000-000000000010', 'redis', 'Redis', 'redis'),
    ('20000000-0000-4000-8000-000000000011', 'java', 'Java', 'java'),
    ('20000000-0000-4000-8000-000000000012', 'kotlin', 'Kotlin', 'kotlin'),
    ('20000000-0000-4000-8000-000000000013', 'vue', 'Vue', 'vue'),
    ('20000000-0000-4000-8000-000000000014', 'angular', 'Angular', 'angular'),
    ('20000000-0000-4000-8000-000000000015', 'svelte', 'Svelte', 'svelte')
ON CONFLICT (slug) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    normalized_name = EXCLUDED.normalized_name,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;

--rollback DELETE FROM tags WHERE id::text LIKE '20000000-0000-4000-8000-%';
--rollback DELETE FROM project_sub_categories WHERE id::text LIKE '10000000-0000-4000-8000-%';
--rollback DELETE FROM project_categories WHERE id::text LIKE '00000000-0000-4000-8000-%';
