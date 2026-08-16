package io.legado.app.data

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.legado.app.constant.AppConst
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType

object DatabaseMigrations {

    val migrations: Array<Migration> by lazy {
        arrayOf(
            migration_10_11, migration_11_12, migration_12_13, migration_13_14,
            migration_14_15, migration_15_17, migration_17_18, migration_18_19,
            migration_19_20, migration_20_21, migration_21_22, migration_22_23,
            migration_23_24, migration_24_25, migration_25_26, migration_26_27,
            migration_27_28, migration_28_29, migration_29_30, migration_30_31,
            migration_31_32, migration_32_33, migration_33_34, migration_34_35,
            migration_35_36, migration_36_37, migration_37_38, migration_38_39,
            migration_39_40, migration_40_41, migration_41_42, migration_42_43,
            migration_82_83, migration_99_100, migration_100_101, migration_101_102,
            migration_102_103, migration_103_104, migration_104_105,
            migration_105_106, migration_106_107, migration_107_108,
            migration_108_109, migration_109_110,
        )
    }

    private val migration_109_110 = object : Migration(109, 110) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_custom_tools` (
                    `id` TEXT NOT NULL,
                    `toolName` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `activeVersionId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_custom_tools_toolName` ON `ai_custom_tools` (`toolName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_custom_tools_enabled` ON `ai_custom_tools` (`enabled`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_custom_tools_updatedAt` ON `ai_custom_tools` (`updatedAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_custom_tool_versions` (
                    `id` TEXT NOT NULL,
                    `toolId` TEXT NOT NULL,
                    `toolName` TEXT NOT NULL,
                    `version` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `manifestJson` TEXT NOT NULL,
                    `checksum` TEXT NOT NULL,
                    `capabilitiesCsv` TEXT NOT NULL,
                    `allowedDomainsJson` TEXT NOT NULL,
                    `lifecycleState` TEXT NOT NULL,
                    `validationStatus` TEXT NOT NULL,
                    `validationMessage` TEXT NOT NULL,
                    `testStatus` TEXT NOT NULL,
                    `testMessage` TEXT NOT NULL,
                    `testOutputJson` TEXT,
                    `fixtureArgumentsJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `validatedAt` INTEGER,
                    `approvedAt` INTEGER,
                    `testedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`toolId`) REFERENCES `ai_custom_tools`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_custom_tool_versions_toolId` ON `ai_custom_tool_versions` (`toolId`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_custom_tool_versions_toolId_version` " +
                    "ON `ai_custom_tool_versions` (`toolId`, `version`)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_custom_tool_versions_toolName` ON `ai_custom_tool_versions` (`toolName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_custom_tool_versions_lifecycleState` ON `ai_custom_tool_versions` (`lifecycleState`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_custom_tool_versions_createdAt` ON `ai_custom_tool_versions` (`createdAt`)")
        }
    }

    private val migration_108_109 = object : Migration(108, 109) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_agent_audits` (
                    `id` TEXT NOT NULL,
                    `runId` TEXT,
                    `proposalId` TEXT,
                    `conversationId` TEXT,
                    `callId` TEXT NOT NULL,
                    `toolName` TEXT NOT NULL,
                    `risk` TEXT NOT NULL,
                    `capabilitiesCsv` TEXT NOT NULL,
                    `approvalScope` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `requestPreview` TEXT NOT NULL,
                    `resultPreview` TEXT,
                    `errorMessage` TEXT,
                    `startedAt` INTEGER NOT NULL,
                    `finishedAt` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`runId`) REFERENCES `ai_agent_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`proposalId`) REFERENCES `ai_agent_proposals`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_agent_audits_runId` ON `ai_agent_audits` (`runId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_agent_audits_proposalId` ON `ai_agent_audits` (`proposalId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_agent_audits_conversationId` ON `ai_agent_audits` (`conversationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_agent_audits_toolName` ON `ai_agent_audits` (`toolName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_agent_audits_status` ON `ai_agent_audits` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_agent_audits_startedAt` ON `ai_agent_audits` (`startedAt`)")
        }
    }

    private val migration_107_108 = object : Migration(107, 108) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `source_check_runs` (
                    `id` TEXT NOT NULL,
                    `sourceUrl` TEXT NOT NULL,
                    `sourceName` TEXT NOT NULL,
                    `sourceGroup` TEXT,
                    `profile` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `healthStatus` TEXT NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `finishedAt` INTEGER,
                    `latencyMs` INTEGER,
                    `httpStatus` INTEGER,
                    `failureStep` TEXT,
                    `messageRedacted` TEXT,
                    `stageCount` INTEGER NOT NULL,
                    `passedStageCount` INTEGER NOT NULL,
                    `failedStageCount` INTEGER NOT NULL,
                    `skippedStageCount` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_check_runs_sourceUrl_startedAt` ON `source_check_runs` (`sourceUrl`, `startedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_check_runs_status_startedAt` ON `source_check_runs` (`status`, `startedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_check_runs_profile_startedAt` ON `source_check_runs` (`profile`, `startedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_check_runs_finishedAt` ON `source_check_runs` (`finishedAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `source_check_stage_results` (
                    `runId` TEXT NOT NULL,
                    `stageKey` TEXT NOT NULL,
                    `stageOrder` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `finishedAt` INTEGER,
                    `latencyMs` INTEGER,
                    `httpStatus` INTEGER,
                    `failureStep` TEXT,
                    `messageRedacted` TEXT,
                    PRIMARY KEY(`runId`, `stageKey`),
                    FOREIGN KEY(`runId`) REFERENCES `source_check_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_check_stage_results_runId` ON `source_check_stage_results` (`runId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_check_stage_results_status` ON `source_check_stage_results` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_check_stage_results_stageOrder` ON `source_check_stage_results` (`stageOrder`)")
        }
    }

    private val migration_106_107 = object : Migration(106, 107) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cookie_vault` (
                    `id` TEXT NOT NULL,
                    `scopeKey` TEXT NOT NULL,
                    `domain` TEXT NOT NULL,
                    `path` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `valueCiphertext` TEXT NOT NULL,
                    `origin` TEXT NOT NULL,
                    `expiresAt` INTEGER,
                    `secure` INTEGER NOT NULL,
                    `httpOnly` INTEGER NOT NULL,
                    `sameSite` TEXT,
                    `hostOnly` INTEGER NOT NULL,
                    `persistent` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cookie_vault_scopeKey` ON `cookie_vault` (`scopeKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cookie_vault_domain` ON `cookie_vault` (`domain`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cookie_vault_expiresAt` ON `cookie_vault` (`expiresAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cookie_vault_scopeKey_name` ON `cookie_vault` (`scopeKey`, `name`)")
        }
    }

    private val migration_105_106 = object : Migration(105, 106) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `browser_bookmarks` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `url` TEXT NOT NULL,
                    `folder` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_browser_bookmarks_url` ON `browser_bookmarks` (`url`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_browser_bookmarks_folder_sortOrder` ON `browser_bookmarks` (`folder`, `sortOrder`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_browser_bookmarks_updatedAt` ON `browser_bookmarks` (`updatedAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `source_bookmark_preferences` (
                    `sourceType` TEXT NOT NULL,
                    `sourceId` TEXT NOT NULL,
                    `pinned` INTEGER NOT NULL,
                    `hidden` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`sourceType`, `sourceId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_bookmark_preferences_hidden` ON `source_bookmark_preferences` (`hidden`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_bookmark_preferences_pinned_sortOrder` ON `source_bookmark_preferences` (`pinned`, `sortOrder`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_bookmark_preferences_updatedAt` ON `source_bookmark_preferences` (`updatedAt`)")
        }
    }

    private val migration_104_105 = object : Migration(104, 105) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `media_download_items` ADD COLUMN `responseEtag` TEXT")
            db.execSQL("ALTER TABLE `media_download_items` ADD COLUMN `responseLastModified` TEXT")
            db.execSQL(
                "ALTER TABLE `media_download_items` ADD COLUMN `responseContentLength` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val migration_103_104 = object : Migration(103, 104) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `media_download_tasks` (
                    `id` TEXT NOT NULL,
                    `bookUrl` TEXT NOT NULL,
                    `bookTitle` TEXT NOT NULL,
                    `coverUrl` TEXT,
                    `status` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_download_tasks_bookUrl` ON `media_download_tasks` (`bookUrl`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_download_tasks_status` ON `media_download_tasks` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_download_tasks_createdAt` ON `media_download_tasks` (`createdAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `media_download_items` (
                    `id` TEXT NOT NULL,
                    `taskId` TEXT NOT NULL,
                    `bookUrl` TEXT NOT NULL,
                    `chapterIndex` INTEGER NOT NULL,
                    `episodeTitle` TEXT NOT NULL,
                    `variantId` TEXT NOT NULL,
                    `sourceUri` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `headersJson` TEXT NOT NULL,
                    `protocol` TEXT NOT NULL,
                    `expiresAt` INTEGER,
                    `status` TEXT NOT NULL,
                    `bytesDownloaded` INTEGER NOT NULL,
                    `totalBytes` INTEGER NOT NULL,
                    `segmentIndex` INTEGER NOT NULL,
                    `tempPath` TEXT NOT NULL,
                    `localPath` TEXT NOT NULL,
                    `checksum` TEXT NOT NULL,
                    `errorMessage` TEXT,
                    `retryCount` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`taskId`) REFERENCES `media_download_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_download_items_taskId` ON `media_download_items` (`taskId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_download_items_status` ON `media_download_items` (`status`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_download_items_taskId_chapterIndex_variantId` ON `media_download_items` (`taskId`, `chapterIndex`, `variantId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_download_items_sortOrder` ON `media_download_items` (`sortOrder`)")
        }
    }

    private val migration_102_103 = object : Migration(102, 103) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `book_source_health` (
                    `sourceUrl` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `lastChecked` INTEGER NOT NULL,
                    `latencyMs` INTEGER,
                    `httpStatus` INTEGER,
                    `failureStep` TEXT,
                    `messageRedacted` TEXT,
                    `consecutiveFailures` INTEGER NOT NULL,
                    PRIMARY KEY(`sourceUrl`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_book_source_health_status` " +
                    "ON `book_source_health` (`status`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_book_source_health_lastChecked` " +
                    "ON `book_source_health` (`lastChecked`)"
            )
        }
    }

    private val migration_101_102 = object : Migration(101, 102) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_skills` (
                    `id` TEXT NOT NULL,
                    `slug` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `activeVersionId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_skills_slug` ON `ai_skills` (`slug`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_skills_enabled` ON `ai_skills` (`enabled`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_skills_updatedAt` ON `ai_skills` (`updatedAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_skill_versions` (
                    `id` TEXT NOT NULL,
                    `skillId` TEXT NOT NULL,
                    `version` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `manifestJson` TEXT NOT NULL,
                    `skillMarkdown` TEXT NOT NULL,
                    `allowedToolsJson` TEXT NOT NULL,
                    `requirementsJson` TEXT NOT NULL,
                    `validationStatus` TEXT NOT NULL,
                    `validationMessage` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`skillId`) REFERENCES `ai_skills`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_skill_versions_skillId` ON `ai_skill_versions` (`skillId`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_skill_versions_skillId_version` " +
                    "ON `ai_skill_versions` (`skillId`, `version`)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_skill_versions_createdAt` ON `ai_skill_versions` (`createdAt`)")
        }
    }

    private val migration_100_101 = object : Migration(100, 101) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `ai_memory_fts`
                USING FTS4(
                    `conversationId` TEXT NOT NULL,
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    `scope` TEXT NOT NULL,
                    `scopeId` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    tokenize=unicode61
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO ai_memory_fts(conversationId, `key`, value, scope, scopeId, type)
                SELECT conversationId, `key`, value, scope, scopeId, type
                FROM ai_memory
                """.trimIndent()
            )
        }
    }

    private val migration_99_100 = object : Migration(99, 100) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE ai_memory ADD COLUMN scope TEXT NOT NULL DEFAULT 'conversation'")
            db.execSQL("ALTER TABLE ai_memory ADD COLUMN scopeId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE ai_memory ADD COLUMN type TEXT NOT NULL DEFAULT 'fact'")
            db.execSQL("ALTER TABLE ai_memory ADD COLUMN sourceConversationId TEXT")
            db.execSQL("ALTER TABLE ai_memory ADD COLUMN sourceMessageId TEXT")
            db.execSQL("ALTER TABLE ai_memory ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE ai_memory ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ai_memory ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                    UPDATE ai_memory
                    SET scope = CASE WHEN conversationId = '' THEN 'global' ELSE 'conversation' END,
                        scopeId = conversationId,
                        sourceConversationId = CASE WHEN conversationId = '' THEN NULL ELSE conversationId END,
                        createdAt = updatedAt
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_memory_scope_scopeId ON ai_memory(scope, scopeId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_memory_type ON ai_memory(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_memory_pinned ON ai_memory(pinned)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_memory_updatedAt ON ai_memory(updatedAt)")
        }
    }

    private val migration_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE txtTocRules")
            database.execSQL(
                """CREATE TABLE txtTocRules(id INTEGER NOT NULL, 
                    name TEXT NOT NULL, rule TEXT NOT NULL, serialNumber INTEGER NOT NULL, 
                    enable INTEGER NOT NULL, PRIMARY KEY (id))"""
            )
        }
    }

    private val migration_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE rssSources ADD style TEXT ")
        }
    }

    private val migration_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE rssSources ADD articleStyle INTEGER NOT NULL DEFAULT 0 ")
        }
    }

    private val migration_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `books_new` (`bookUrl` TEXT NOT NULL, `tocUrl` TEXT NOT NULL, `origin` TEXT NOT NULL,
                    `originName` TEXT NOT NULL, `name` TEXT NOT NULL, `author` TEXT NOT NULL, `kind` TEXT, `customTag` TEXT, `coverUrl` TEXT, 
                    `customCoverUrl` TEXT, `intro` TEXT, `customIntro` TEXT, `charset` TEXT, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, 
                    `latestChapterTitle` TEXT, `latestChapterTime` INTEGER NOT NULL, `lastCheckTime` INTEGER NOT NULL, `lastCheckCount` INTEGER NOT NULL, 
                    `totalChapterNum` INTEGER NOT NULL, `durChapterTitle` TEXT, `durChapterIndex` INTEGER NOT NULL, `durChapterPos` INTEGER NOT NULL, 
                    `durChapterTime` INTEGER NOT NULL, `wordCount` TEXT, `canUpdate` INTEGER NOT NULL, `order` INTEGER NOT NULL, 
                    `originOrder` INTEGER NOT NULL, `useReplaceRule` INTEGER NOT NULL, `variable` TEXT, PRIMARY KEY(`bookUrl`))"""
            )
            database.execSQL("INSERT INTO books_new select * from books ")
            database.execSQL("DROP TABLE books")
            database.execSQL("ALTER TABLE books_new RENAME TO books")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_name_author` ON `books` (`name`, `author`) ")
        }
    }

    private val migration_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE bookmarks ADD bookAuthor TEXT NOT NULL DEFAULT ''")
        }
    }

    private val migration_15_17 = object : Migration(15, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `readRecord` (`bookName` TEXT NOT NULL, `readTime` INTEGER NOT NULL, PRIMARY KEY(`bookName`))")
        }
    }

    private val migration_17_18 = object : Migration(17, 18) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `httpTTS` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, PRIMARY KEY(`id`))")
        }
    }

    private val migration_18_19 = object : Migration(18, 19) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `readRecordNew` (`androidId` TEXT NOT NULL, `bookName` TEXT NOT NULL, `readTime` INTEGER NOT NULL, 
                    PRIMARY KEY(`androidId`, `bookName`))"""
            )
            database.execSQL("INSERT INTO readRecordNew(androidId, bookName, readTime) select '${AppConst.androidId}' as androidId, bookName, readTime from readRecord")
            database.execSQL("DROP TABLE readRecord")
            database.execSQL("ALTER TABLE readRecordNew RENAME TO readRecord")
        }
    }
    private val migration_19_20 = object : Migration(19, 20) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE book_sources ADD bookSourceComment TEXT")
        }
    }

    private val migration_20_21 = object : Migration(20, 21) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE book_groups ADD show INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val migration_21_22 = object : Migration(21, 22) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `books_new` (`bookUrl` TEXT NOT NULL, `tocUrl` TEXT NOT NULL, `origin` TEXT NOT NULL, 
                    `originName` TEXT NOT NULL, `name` TEXT NOT NULL, `author` TEXT NOT NULL, `kind` TEXT, `customTag` TEXT, 
                    `coverUrl` TEXT, `customCoverUrl` TEXT, `intro` TEXT, `customIntro` TEXT, `charset` TEXT, `type` INTEGER NOT NULL, 
                    `group` INTEGER NOT NULL, `latestChapterTitle` TEXT, `latestChapterTime` INTEGER NOT NULL, `lastCheckTime` INTEGER NOT NULL, 
                    `lastCheckCount` INTEGER NOT NULL, `totalChapterNum` INTEGER NOT NULL, `durChapterTitle` TEXT, `durChapterIndex` INTEGER NOT NULL, 
                    `durChapterPos` INTEGER NOT NULL, `durChapterTime` INTEGER NOT NULL, `wordCount` TEXT, `canUpdate` INTEGER NOT NULL, 
                    `order` INTEGER NOT NULL, `originOrder` INTEGER NOT NULL, `variable` TEXT, `readConfig` TEXT, PRIMARY KEY(`bookUrl`))"""
            )
            database.execSQL(
                """INSERT INTO books_new select `bookUrl`, `tocUrl`, `origin`, `originName`, `name`, `author`, `kind`, `customTag`, `coverUrl`, 
                    `customCoverUrl`, `intro`, `customIntro`, `charset`, `type`, `group`, `latestChapterTitle`, `latestChapterTime`, `lastCheckTime`, 
                    `lastCheckCount`, `totalChapterNum`, `durChapterTitle`, `durChapterIndex`, `durChapterPos`, `durChapterTime`, `wordCount`, `canUpdate`, 
                    `order`, `originOrder`, `variable`, null
                    from books"""
            )
            database.execSQL("DROP TABLE books")
            database.execSQL("ALTER TABLE books_new RENAME TO books")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_name_author` ON `books` (`name`, `author`) ")
        }
    }

    private val migration_22_23 = object : Migration(22, 23) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE chapters ADD baseUrl TEXT NOT NULL DEFAULT ''")
        }
    }

    private val migration_23_24 = object : Migration(23, 24) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `caches` (`key` TEXT NOT NULL, `value` TEXT, `deadline` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_caches_key` ON `caches` (`key`)")
        }
    }

    private val migration_24_25 = object : Migration(24, 25) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `sourceSubs` 
                    (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `type` INTEGER NOT NULL, `customOrder` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`))"""
            )
        }
    }

    private val migration_25_26 = object : Migration(25, 26) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `ruleSubs` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `type` INTEGER NOT NULL, 
                    `customOrder` INTEGER NOT NULL, `autoUpdate` INTEGER NOT NULL, `update` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
            )
            database.execSQL(" insert into `ruleSubs` select *, 0, 0 from `sourceSubs` ")
            database.execSQL("DROP TABLE `sourceSubs`")
        }
    }

    private val migration_26_27 = object : Migration(26, 27) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(" ALTER TABLE rssSources ADD singleUrl INTEGER NOT NULL DEFAULT 0 ")
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `bookmarks1` (`time` INTEGER NOT NULL, `bookUrl` TEXT NOT NULL, `bookName` TEXT NOT NULL, 
                        `bookAuthor` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, `chapterPos` INTEGER NOT NULL, `chapterName` TEXT NOT NULL, 
                        `bookText` TEXT NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`time`))"""
            )
            database.execSQL(
                """insert into `bookmarks1` 
                        select `time`, `bookUrl`, `bookName`, `bookAuthor`, `chapterIndex`, `pageIndex`, `chapterName`, '', `content` 
                        from bookmarks"""
            )
            database.execSQL(" DROP TABLE `bookmarks` ")
            database.execSQL(" ALTER TABLE bookmarks1 RENAME TO bookmarks ")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_time` ON `bookmarks` (`time`)")
        }
    }

    private val migration_27_28 = object : Migration(27, 28) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE rssArticles ADD variable TEXT")
            database.execSQL("ALTER TABLE rssStars ADD variable TEXT")
        }
    }

    private val migration_28_29 = object : Migration(28, 29) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE rssSources ADD sourceComment TEXT")
        }
    }

    private val migration_29_30 = object : Migration(29, 30) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE chapters ADD `startFragmentId` TEXT")
            database.execSQL("ALTER TABLE chapters ADD `endFragmentId` TEXT")
            database.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `epubChapters` 
                    (`bookUrl` TEXT NOT NULL, `href` TEXT NOT NULL, `parentHref` TEXT, 
                    PRIMARY KEY(`bookUrl`, `href`), FOREIGN KEY(`bookUrl`) REFERENCES `books`(`bookUrl`) ON UPDATE NO ACTION ON DELETE CASCADE )
                """
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_epubChapters_bookUrl` ON `epubChapters` (`bookUrl`)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epubChapters_bookUrl_href` ON `epubChapters` (`bookUrl`, `href`)")
        }
    }

    private val migration_30_31 = object : Migration(30, 31) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE readRecord RENAME TO readRecord1")
            database.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `readRecord` (`deviceId` TEXT NOT NULL, `bookName` TEXT NOT NULL, `readTime` INTEGER NOT NULL, PRIMARY KEY(`deviceId`, `bookName`))
                """
            )
            database.execSQL("insert into readRecord (deviceId, bookName, readTime) select androidId, bookName, readTime from readRecord1")
        }
    }

    private val migration_31_32 = object : Migration(31, 32) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE `epubChapters`")
        }
    }

    private val migration_32_33 = object : Migration(32, 33) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE bookmarks RENAME TO bookmarks_old")
            database.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `bookmarks` (`time` INTEGER NOT NULL,
                    `bookName` TEXT NOT NULL, `bookAuthor` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, 
                    `chapterPos` INTEGER NOT NULL, `chapterName` TEXT NOT NULL, `bookText` TEXT NOT NULL, 
                    `content` TEXT NOT NULL, PRIMARY KEY(`time`))
                """
            )
            database.execSQL(
                """
                    CREATE INDEX IF NOT EXISTS `index_bookmarks_bookName_bookAuthor` ON `bookmarks` (`bookName`, `bookAuthor`)
                """
            )
            database.execSQL(
                """
                    insert into bookmarks (time, bookName, bookAuthor, chapterIndex, chapterPos, chapterName, bookText, content)
                    select time, ifNull(b.name, bookName) bookName, ifNull(b.author, bookAuthor) bookAuthor, 
                    chapterIndex, chapterPos, chapterName, bookText, content from bookmarks_old o
                    left join books b on o.bookUrl = b.bookUrl
                """
            )
        }
    }

    private val migration_33_34 = object : Migration(33, 34) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `book_groups` ADD `cover` TEXT")
        }
    }

    private val migration_34_35 = object : Migration(34, 35) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `book_sources` ADD `concurrentRate` TEXT")
        }
    }

    private val migration_35_36 = object : Migration(35, 36) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `book_sources` ADD `loginUi` TEXT")
            database.execSQL("ALTER TABLE `book_sources` ADD`loginCheckJs` TEXT")
        }
    }

    private val migration_36_37 = object : Migration(36, 37) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `rssSources` ADD `loginUrl` TEXT")
            database.execSQL("ALTER TABLE `rssSources` ADD `loginUi` TEXT")
            database.execSQL("ALTER TABLE `rssSources` ADD `loginCheckJs` TEXT")
        }
    }

    private val migration_37_38 = object : Migration(37, 38) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `book_sources` ADD `respondTime` INTEGER NOT NULL DEFAULT 180000")
        }
    }

    private val migration_38_39 = object : Migration(38, 39) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `rssSources` ADD `concurrentRate` TEXT")
        }
    }

    private val migration_39_40 = object : Migration(39, 40) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `chapters` ADD `isVip` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `chapters` ADD `isPay` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration_40_41 = object : Migration(40, 41) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `httpTTS` ADD `loginUrl` TEXT")
            database.execSQL("ALTER TABLE `httpTTS` ADD `loginUi` TEXT")
            database.execSQL("ALTER TABLE `httpTTS` ADD `loginCheckJs` TEXT")
            database.execSQL("ALTER TABLE `httpTTS` ADD `header` TEXT")
            database.execSQL("ALTER TABLE `httpTTS` ADD `concurrentRate` TEXT")
        }
    }

    private val migration_41_42 = object : Migration(41, 42) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE 'httpTTS' ADD `contentType` TEXT")
        }
    }

    private val migration_42_43 = object : Migration(42, 43) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `chapters` ADD `isVolume` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration_82_83 = object : Migration(82, 83) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE readRecord RENAME TO readRecord_old")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `readRecord` (
                    `deviceId` TEXT NOT NULL,
                    `bookName` TEXT NOT NULL,
                    `bookAuthor` TEXT NOT NULL DEFAULT '',
                    `readTime` INTEGER NOT NULL DEFAULT 0,
                    `lastRead` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`deviceId`, `bookName`, `bookAuthor`)
                )
                """
            )
            database.execSQL(
                """
                INSERT INTO readRecord(deviceId, bookName, bookAuthor, readTime, lastRead)
                SELECT
                    rr.deviceId,
                    rr.bookName,
                    IFNULL(
                        (
                            SELECT CASE
                                WHEN COUNT(DISTINCT b.author) = 1 THEN MAX(b.author)
                                ELSE ''
                            END
                            FROM books b
                            WHERE b.name = rr.bookName
                        ),
                        ''
                    ) AS bookAuthor,
                    rr.readTime,
                    rr.lastRead
                FROM readRecord_old rr
                """
            )
            database.execSQL("DROP TABLE readRecord_old")

            database.execSQL("ALTER TABLE readRecordDetail RENAME TO readRecordDetail_old")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `readRecordDetail` (
                    `deviceId` TEXT NOT NULL,
                    `bookName` TEXT NOT NULL,
                    `bookAuthor` TEXT NOT NULL DEFAULT '',
                    `date` TEXT NOT NULL,
                    `readTime` INTEGER NOT NULL DEFAULT 0,
                    `readWords` INTEGER NOT NULL DEFAULT 0,
                    `firstReadTime` INTEGER NOT NULL DEFAULT 0,
                    `lastReadTime` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`deviceId`, `bookName`, `bookAuthor`, `date`)
                )
                """
            )
            database.execSQL(
                """
                INSERT INTO readRecordDetail(
                    deviceId, bookName, bookAuthor, date, readTime, readWords, firstReadTime, lastReadTime
                )
                SELECT
                    rd.deviceId,
                    rd.bookName,
                    IFNULL(
                        (
                            SELECT CASE
                                WHEN COUNT(DISTINCT b.author) = 1 THEN MAX(b.author)
                                ELSE ''
                            END
                            FROM books b
                            WHERE b.name = rd.bookName
                        ),
                        ''
                    ) AS bookAuthor,
                    rd.date,
                    rd.readTime,
                    rd.readWords,
                    rd.firstReadTime,
                    rd.lastReadTime
                FROM readRecordDetail_old rd
                """
            )
            database.execSQL("DROP TABLE readRecordDetail_old")

            database.execSQL("ALTER TABLE readRecordSession RENAME TO readRecordSession_old")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `readRecordSession` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `deviceId` TEXT NOT NULL,
                    `bookName` TEXT NOT NULL,
                    `bookAuthor` TEXT NOT NULL DEFAULT '',
                    `startTime` INTEGER NOT NULL,
                    `endTime` INTEGER NOT NULL,
                    `words` INTEGER NOT NULL
                )
                """
            )
            database.execSQL(
                """
                INSERT INTO readRecordSession(id, deviceId, bookName, bookAuthor, startTime, endTime, words)
                SELECT
                    rs.id,
                    rs.deviceId,
                    rs.bookName,
                    IFNULL(
                        (
                            SELECT CASE
                                WHEN COUNT(DISTINCT b.author) = 1 THEN MAX(b.author)
                                ELSE ''
                            END
                            FROM books b
                            WHERE b.name = rs.bookName
                        ),
                        ''
                    ) AS bookAuthor,
                    rs.startTime,
                    rs.endTime,
                    rs.words
                FROM readRecordSession_old rs
                """
            )
            database.execSQL("DROP TABLE readRecordSession_old")
        }
    }

    @Suppress("ClassName")
    class Migration_54_55 : AutoMigrationSpec {

        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                update books set type = ${BookType.audio}
                where type = ${BookSourceType.audio}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = ${BookType.image}
                where type = ${BookSourceType.image}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = ${BookType.webFile}
                where type = ${BookSourceType.file}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = ${BookType.text}
                where type = ${BookSourceType.default}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = type | ${BookType.local}
                where origin like '${BookType.localTag}%' or origin like '${BookType.webDavTag}%'
            """.trimIndent()
            )
        }

    }


    @Suppress("ClassName")
    @DeleteColumn(
        tableName = "book_sources",
        columnName = "enabledReview"
    )
    class Migration_64_65 : AutoMigrationSpec
}
