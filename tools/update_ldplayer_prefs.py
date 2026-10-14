import subprocess
import os

print("Updating LDPlayer preferences...")
xml = subprocess.check_output(
    ['adb', 'shell', 'su', 'root', 'cat', '/data/data/com.drducbook.app.debug/shared_prefs/com.drducbook.app.debug_preferences.xml'],
    text=True, encoding='utf-8'
)

# Remove existing nmtBeamWidth if any, then insert
if 'name="nmtBeamWidth"' not in xml:
    xml = xml.replace('</map>', '    <int name="nmtBeamWidth" value="4" />\n</map>')
if 'name="quickTranslationBeamWidth"' not in xml:
    xml = xml.replace('</map>', '    <int name="quickTranslationBeamWidth" value="3" />\n</map>')

temp_file = "temp_prefs.xml"
with open(temp_file, "w", encoding="utf-8") as f:
    f.write(xml)

subprocess.run(['adb', 'push', temp_file, '/sdcard/temp_prefs.xml'], check=True)
subprocess.run(['adb', 'shell', 'su', 'root', 'cp', '/sdcard/temp_prefs.xml', '/data/data/com.drducbook.app.debug/shared_prefs/com.drducbook.app.debug_preferences.xml'], check=True)
subprocess.run(['adb', 'shell', 'su', 'root', 'chmod', '660', '/data/data/com.drducbook.app.debug/shared_prefs/com.drducbook.app.debug_preferences.xml'], check=True)
subprocess.run(['adb', 'shell', 'su', 'root', 'chown', 'u0_a87:u0_a87', '/data/data/com.drducbook.app.debug/shared_prefs/com.drducbook.app.debug_preferences.xml'], check=True)
os.remove(temp_file)
print("Successfully set nmtBeamWidth = 4 and quickTranslationBeamWidth = 3 on LDPlayer!")
