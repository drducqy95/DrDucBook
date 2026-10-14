import subprocess
import json

p = subprocess.Popen(["adb", "shell", "sqlite3 /data/data/com.drducbook.app.debug/databases/legado.db"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
res, err = p.communicate("SELECT apiKey FROM ai_provider_profiles WHERE id='catalog_gemini_web';\n")

print("Read api_key length:", len(res))
with open("gemini_session.json", "w", encoding="utf-8") as f:
    f.write(res.strip())
print("Saved to gemini_session.json successfully")
