# P03.T01 - AppearanceProfile schema va repository

## Ket qua

Trang thai: `DONE`

## Trien khai

- Tao `AppearanceProfile`, `AppearanceState`, `AppearanceSnapshot` va `AppearancePresets`.
- Dinh nghia mot contract chung cho Material va Miuix qua `AppearanceGateway` va `AppearanceUseCase`.
- Luu profile theo kieu atomic, co fallback khi file loi va asset store theo content-hash.
- Giu preset built-in, profile custom va mapping tu legacy theme.
- `AppearanceRepository` hydrat ho so active, cleanup asset khong con tham chieu va export/restore snapshot.

## Verification

- `AppearancePresetsTest`: PASS.
- `AppearanceFileStoreTest`: PASS.
- `AppearanceAssetPolicyTest`: PASS.
- `:app:compileAppDebugKotlin`: PASS.
- `:app:assembleAppDebug`: PASS.

## Bang chung

- `app/src/main/java/io/legado/app/domain/model/AppearanceProfile.kt`
- `app/src/main/java/io/legado/app/data/repository/AppearanceRepository.kt`
- `app/src/main/java/io/legado/app/domain/usecase/AppearanceUseCase.kt`
- `app/src/test/java/io/legado/app/domain/model/AppearancePresetsTest.kt`
- `app/src/test/java/io/legado/app/data/repository/AppearanceFileStoreTest.kt`
- `app/src/test/java/io/legado/app/data/repository/AppearanceAssetPolicyTest.kt`

## Rui ro con lai

- Moi thay doi schema version hoac preset co them migration gate o P11.
