# P09.T08 - Media player VBook hotfix

## Muc tieu

Sua loi nguon video Legado/VBook import duoc nhung khi mo tu Kham pha/BookInfo lai roi ve reader/browser va hien URL `.m3u8` nhu text. Bo sung nut tai video ro rang va sheet cai dat trinh phat theo mau VBook.

## Pham vi file tac dong

- `app/src/main/java/io/legado/app/help/book/BookExtensions.kt`
- `app/src/main/java/io/legado/app/help/vbook/VbookPluginAdapter.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt`
- `app/src/main/java/io/legado/app/help/config/MediaPlayerConfig.kt`
- `app/src/main/java/io/legado/app/constant/PreferKey.kt`
- `app/src/main/java/io/legado/app/domain/model/ResolvedMedia.kt`
- `app/src/main/java/io/legado/app/service/MediaPlaybackService.kt`
- `app/src/main/java/io/legado/app/ui/media/player/**`
- `app/src/main/res/values*/strings.xml`
- `app/src/test/java/io/legado/app/ui/main/ExploreBookOpenPolicyTest.kt`

## Thuc hien

- Them `Book.normalizeTypeFromSource(source)` de sua lai book type theo `BookSource.bookSourceType` nhung van giu cac flag nhu `notShelf`.
- VBook adapter gan `SearchBook.type = source.getBookType()` cho ket qua search/explore.
- Luong Kham pha va BookInfo chuan hoa type truoc khi quyet dinh mo reader/player.
- Media player co gear settings, nut tai tap hien ro trong controls va cac tuy chon: tu phat, tu chuyen tap, tiep tuc vi tri cu, tua toi/lui 5/10/15/30 giay, giu man hinh, do sang tu dong, tat tieng, am luong mac dinh, phu de/mau/co chu/do mo/khoang cach day.
- Playback service ton trong tuy chon khong resume vi tri cu.
- PlayerView ap dung keep-screen-on va subtitle style theo state.

## Dieu kien thong qua

- Item cache dang `BookType.text` nhung thuoc source video phai mo `MainRouteMediaPlayer`.
- Ket qua VBook moi phai co `type` dung ngay khi parse.
- `.m3u8` van duoc parser media nhan la HLS co download support.
- Kotlin compile va APK debug build pass.

## Kiem tra

- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain` - PASS.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.main.ExploreBookOpenPolicyTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --no-daemon --console=plain` - PASS.
- `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain` - PASS.
- `adb install -r app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk` - PASS tren `emulator-5554`.
- `adb shell am start -S -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity` - PASS; PID `6318`, version `3.26.13_debug`, ABI `x86_64`.

## Ghi chu

- Mot lan chay test song song gay loi tam thoi `R.jar already contains entry ...`; chay lai serial/chung mot Gradle invocation da PASS.
- Device launch smoke khong thay `FATAL EXCEPTION`; can mo dung nguon video cua nguoi dung de xac nhan UI player thay cho browser va download queue voi media that.
