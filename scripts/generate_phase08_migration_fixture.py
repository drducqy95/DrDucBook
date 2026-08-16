import json
import sqlite3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "app/schemas/io.legado.app.data.AppDatabase/98.json"
OUTPUT = ROOT / "app/src/androidTest/assets/test_db_v98.db"


def main() -> None:
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))["database"]
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.unlink(missing_ok=True)
    connection = sqlite3.connect(OUTPUT)
    try:
        connection.execute("PRAGMA foreign_keys=ON")
        for entity in schema["entities"]:
            table_name = entity["tableName"]
            connection.execute(entity["createSql"].replace("${TABLE_NAME}", table_name))
            for index in entity.get("indices", []):
                connection.execute(index["createSql"].replace("${TABLE_NAME}", table_name))
        for view in schema.get("views", []):
            connection.execute(view["createSql"].replace("${VIEW_NAME}", view["viewName"]))
        connection.execute(
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
        )
        connection.execute(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
            (schema["identityHash"],),
        )
        insert_fixture(connection)
        connection.execute("PRAGMA user_version=98")
        connection.commit()
    finally:
        connection.close()


def insert_fixture(db: sqlite3.Connection) -> None:
    book_kinds = [
        ("book://text", "https://source.test", "Text fixture", 0),
        ("book://audio", "https://audio.test", "Audio fixture", 4),
        ("book://local", "loc_book", "Local fixture", 1),
        ("book://vbook", "vbook://plugin/fd1246b6fd1246b6", "VBook fixture", 0),
        ("book://manga", "https://manga.test", "Manga fixture", 2),
    ]
    for book_url, origin, name, book_type in book_kinds:
        db.execute(
            "INSERT INTO books(bookUrl, tocUrl, origin, originName, name, author, type, totalChapterNum) "
            "VALUES (?, '', ?, ?, ?, 'Fixture author', ?, 10)",
            (book_url, origin, origin, name, book_type),
        )
        for index in range(10):
            db.execute(
                "INSERT INTO chapters(url, title, isVolume, baseUrl, bookUrl, `index`, isVip, isPay) "
                "VALUES (?, ?, 0, ?, ?, ?, 0, 0)",
                (f"{book_url}/chapter/{index}", f"Chapter {index + 1}", book_url, book_url, index),
            )
    sources = [
        ("https://source.test", "Online fixture", 0),
        ("loc_book", "Local fixture", 0),
        ("vbook://plugin/fd1246b6fd1246b6", "VBook fixture", 0),
    ]
    for url, name, source_type in sources:
        db.execute(
            "INSERT INTO book_sources(bookSourceUrl, bookSourceName, bookSourceType, lastUpdateTime, respondTime, weight) "
            "VALUES (?, ?, ?, 1, 0, 0)",
            (url, name, source_type),
        )
    db.execute("INSERT INTO cookies(url, cookie) VALUES ('https://source.test', 'session=fixture')")
    db.execute(
        "INSERT INTO ai_provider_profiles(id, name, protocol, baseUrl, apiKey, authType, secretRef, enabled, createdAt, updatedAt) "
        "VALUES ('provider-fixture', 'Fixture provider', 'OPENAI_CHAT', 'https://ai.test', 'legacy-secret', "
        "'API_KEY', NULL, 1, 1, 1)"
    )
    db.execute(
        "INSERT INTO ai_model_profiles(id, providerId, displayName, modelId, contextWindow, maxOutputTokens, "
        "capabilities, enabled, sortNumber, createdAt, updatedAt) VALUES "
        "('model-fixture', 'provider-fixture', 'Fixture model', 'fixture-model', 8192, 2048, 'TEXT', 1, 0, 1, 1)"
    )
    db.execute(
        "INSERT INTO ai_route_profiles(id, name, taskType, strategy, maxAttempts, stickySession, enabled, "
        "isDefault, sortNumber, createdAt, updatedAt) VALUES "
        "('route-fixture', 'Fixture route', 'CHAT', 'FALLBACK', 2, 0, 1, 1, 0, 1, 1)"
    )
    db.execute(
        "INSERT INTO ai_credentials(id, providerId, label, kind, secretRef, enabled, sortNumber, cooldownUntil, "
        "consecutiveFailures, status, createdAt, updatedAt) VALUES "
        "('credential-fixture', 'provider-fixture', 'Fixture credential', 'API_KEY', 'secret://fixture', 1, 0, 0, 0, "
        "'ACTIVE', 1, 1)"
    )
    db.execute(
        "INSERT INTO ai_memory(conversationId, `key`, value, updatedAt) VALUES "
        "('fixture-chat', 'hero', 'Azure Dragon Sword', 1)"
    )
    db.execute(
        "INSERT INTO ai_memory(conversationId, `key`, value, updatedAt) VALUES "
        "('', 'global-style', 'Concise', 2)"
    )


if __name__ == "__main__":
    main()
