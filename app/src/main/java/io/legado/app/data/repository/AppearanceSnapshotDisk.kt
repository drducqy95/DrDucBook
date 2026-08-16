package io.legado.app.data.repository

import com.google.gson.annotations.SerializedName
import io.legado.app.domain.model.APPEARANCE_SCHEMA_VERSION
import io.legado.app.domain.model.AppearanceColors
import io.legado.app.domain.model.AppearanceEngine
import io.legado.app.domain.model.AppearanceIconSpec
import io.legado.app.domain.model.AppearanceProfile
import io.legado.app.domain.model.AppearanceSnapshot
import io.legado.app.domain.model.AppearanceThemeMode
import io.legado.app.domain.model.AppearanceWallpaperSpec
import io.legado.app.domain.model.WallpaperAlignment
import io.legado.app.domain.model.WallpaperFit

/** Stable on-disk schema. Alternate one-letter names migrate snapshots written by old R8 builds. */
internal data class AppearanceSnapshotDisk(
    @SerializedName(value = "schemaVersion", alternate = ["a"])
    val schemaVersion: Int = APPEARANCE_SCHEMA_VERSION,
    @SerializedName(value = "activeProfileId", alternate = ["b"])
    val activeProfileId: String? = null,
    @SerializedName(value = "profiles", alternate = ["c"])
    val profiles: List<AppearanceProfileDisk>? = emptyList(),
) {
    fun toDomain(): AppearanceSnapshot = AppearanceSnapshot(
        schemaVersion = schemaVersion,
        activeProfileId = activeProfileId.orEmpty(),
        profiles = profiles.orEmpty().map(AppearanceProfileDisk::toDomain),
    )

    companion object {
        fun fromDomain(snapshot: AppearanceSnapshot): AppearanceSnapshotDisk = AppearanceSnapshotDisk(
            schemaVersion = snapshot.schemaVersion,
            activeProfileId = snapshot.activeProfileId,
            profiles = snapshot.profiles.map(AppearanceProfileDisk::fromDomain),
        )
    }
}

internal data class AppearanceProfileDisk(
    @SerializedName(value = "schemaVersion", alternate = ["a"])
    val schemaVersion: Int = APPEARANCE_SCHEMA_VERSION,
    @SerializedName(value = "id", alternate = ["b"])
    val id: String? = null,
    @SerializedName(value = "name", alternate = ["c"])
    val name: String? = null,
    @SerializedName(value = "builtIn", alternate = ["d"])
    val builtIn: Boolean = false,
    @SerializedName(value = "engine", alternate = ["e"])
    val engine: String? = null,
    @SerializedName(value = "themeMode", alternate = ["f"])
    val themeMode: String? = null,
    @SerializedName(value = "lightColors", alternate = ["g"])
    val lightColors: AppearanceColorsDisk? = null,
    @SerializedName(value = "darkColors", alternate = ["h"])
    val darkColors: AppearanceColorsDisk? = null,
    @SerializedName(value = "fontScale", alternate = ["i"])
    val fontScale: Int = 10,
    @SerializedName(value = "containerOpacity", alternate = ["j"])
    val containerOpacity: Int = 100,
    @SerializedName(value = "topBarOpacity", alternate = ["k"])
    val topBarOpacity: Int = 100,
    @SerializedName(value = "bottomBarOpacity", alternate = ["l"])
    val bottomBarOpacity: Int = 100,
    @SerializedName(value = "blurEnabled", alternate = ["m"])
    val blurEnabled: Boolean = false,
    @SerializedName(value = "progressiveBlurEnabled", alternate = ["n"])
    val progressiveBlurEnabled: Boolean = false,
    @SerializedName(value = "iconSlots", alternate = ["o"])
    val iconSlots: Map<String, AppearanceIconSpecDisk>? = emptyMap(),
    @SerializedName(value = "lightWallpapers", alternate = ["p"])
    val lightWallpapers: Map<String, AppearanceWallpaperSpecDisk>? = emptyMap(),
    @SerializedName(value = "darkWallpapers", alternate = ["q"])
    val darkWallpapers: Map<String, AppearanceWallpaperSpecDisk>? = emptyMap(),
    @SerializedName(value = "updatedAt", alternate = ["r"])
    val updatedAt: Long = 0L,
) {
    fun toDomain(): AppearanceProfile = AppearanceProfile(
        schemaVersion = schemaVersion,
        id = id.orEmpty(),
        name = name.orEmpty(),
        builtIn = builtIn,
        engine = enumValue(engine, AppearanceEngine.MATERIAL) { it.configValue },
        themeMode = enumValue(themeMode, AppearanceThemeMode.SYSTEM) { it.configValue },
        lightColors = lightColors?.toDomain() ?: DEFAULT_COLORS,
        darkColors = darkColors?.toDomain() ?: DEFAULT_COLORS,
        fontScale = fontScale,
        containerOpacity = containerOpacity,
        topBarOpacity = topBarOpacity,
        bottomBarOpacity = bottomBarOpacity,
        blurEnabled = blurEnabled,
        progressiveBlurEnabled = progressiveBlurEnabled,
        iconSlots = iconSlots.orEmpty().mapValues { it.value.toDomain() },
        lightWallpapers = lightWallpapers.orEmpty().mapValues { it.value.toDomain() },
        darkWallpapers = darkWallpapers.orEmpty().mapValues { it.value.toDomain() },
        updatedAt = updatedAt,
    )

    companion object {
        private val DEFAULT_COLORS = AppearanceColors(0, 0, 0, 0, 0, 0)

        fun fromDomain(profile: AppearanceProfile): AppearanceProfileDisk = AppearanceProfileDisk(
            schemaVersion = profile.schemaVersion,
            id = profile.id,
            name = profile.name,
            builtIn = profile.builtIn,
            engine = profile.engine.name,
            themeMode = profile.themeMode.name,
            lightColors = AppearanceColorsDisk.fromDomain(profile.lightColors),
            darkColors = AppearanceColorsDisk.fromDomain(profile.darkColors),
            fontScale = profile.fontScale,
            containerOpacity = profile.containerOpacity,
            topBarOpacity = profile.topBarOpacity,
            bottomBarOpacity = profile.bottomBarOpacity,
            blurEnabled = profile.blurEnabled,
            progressiveBlurEnabled = profile.progressiveBlurEnabled,
            iconSlots = profile.iconSlots.mapValues { AppearanceIconSpecDisk.fromDomain(it.value) },
            lightWallpapers = profile.lightWallpapers.mapValues {
                AppearanceWallpaperSpecDisk.fromDomain(it.value)
            },
            darkWallpapers = profile.darkWallpapers.mapValues {
                AppearanceWallpaperSpecDisk.fromDomain(it.value)
            },
            updatedAt = profile.updatedAt,
        )
    }
}

internal data class AppearanceColorsDisk(
    @SerializedName(value = "primary", alternate = ["a"])
    val primary: Int = 0,
    @SerializedName(value = "secondary", alternate = ["b"])
    val secondary: Int = 0,
    @SerializedName(value = "primaryText", alternate = ["c"])
    val primaryText: Int = 0,
    @SerializedName(value = "secondaryText", alternate = ["d"])
    val secondaryText: Int = 0,
    @SerializedName(value = "background", alternate = ["e"])
    val background: Int = 0,
    @SerializedName(value = "container", alternate = ["f"])
    val container: Int = 0,
) {
    fun toDomain() = AppearanceColors(primary, secondary, primaryText, secondaryText, background, container)

    companion object {
        fun fromDomain(colors: AppearanceColors) = AppearanceColorsDisk(
            colors.primary,
            colors.secondary,
            colors.primaryText,
            colors.secondaryText,
            colors.background,
            colors.container,
        )
    }
}

internal data class AppearanceIconSpecDisk(
    @SerializedName(value = "assetId", alternate = ["a"])
    val assetId: String? = null,
    @SerializedName(value = "legacyLocation", alternate = ["b"])
    val legacyLocation: String? = null,
    @SerializedName(value = "bundledIcon", alternate = ["c"])
    val bundledIcon: String? = null,
    @SerializedName(value = "scale", alternate = ["d"])
    val scale: Float = 1f,
    @SerializedName(value = "paddingPercent", alternate = ["e"])
    val paddingPercent: Int = 0,
    @SerializedName(value = "tintColor", alternate = ["f"])
    val tintColor: Int? = null,
    @SerializedName(value = "backgroundColor", alternate = ["g"])
    val backgroundColor: Int? = null,
) {
    fun toDomain() = AppearanceIconSpec(
        assetId.orEmpty(),
        legacyLocation,
        bundledIcon,
        scale,
        paddingPercent,
        tintColor,
        backgroundColor,
    )

    companion object {
        fun fromDomain(spec: AppearanceIconSpec) = AppearanceIconSpecDisk(
            spec.assetId,
            spec.legacyLocation,
            spec.bundledIcon,
            spec.scale,
            spec.paddingPercent,
            spec.tintColor,
            spec.backgroundColor,
        )
    }
}

internal data class AppearanceWallpaperSpecDisk(
    @SerializedName(value = "assetId", alternate = ["a"])
    val assetId: String? = null,
    @SerializedName(value = "legacyLocation", alternate = ["b"])
    val legacyLocation: String? = null,
    @SerializedName(value = "fit", alternate = ["c"])
    val fit: String? = null,
    @SerializedName(value = "horizontalAlignment", alternate = ["d"])
    val horizontalAlignment: String? = null,
    @SerializedName(value = "verticalAlignment", alternate = ["e"])
    val verticalAlignment: String? = null,
    @SerializedName(value = "opacityPercent", alternate = ["f"])
    val opacityPercent: Int = 100,
    @SerializedName(value = "blurDp", alternate = ["g"])
    val blurDp: Int = 0,
    @SerializedName(value = "overlayColor", alternate = ["h"])
    val overlayColor: Int? = null,
    @SerializedName(value = "dimPercent", alternate = ["i"])
    val dimPercent: Int = 0,
) {
    fun toDomain() = AppearanceWallpaperSpec(
        assetId = assetId.orEmpty(),
        legacyLocation = legacyLocation,
        fit = enumValue(fit, WallpaperFit.COVER),
        horizontalAlignment = enumValue(horizontalAlignment, WallpaperAlignment.CENTER),
        verticalAlignment = enumValue(verticalAlignment, WallpaperAlignment.CENTER),
        opacityPercent = opacityPercent,
        blurDp = blurDp,
        overlayColor = overlayColor,
        dimPercent = dimPercent,
    )

    companion object {
        fun fromDomain(spec: AppearanceWallpaperSpec) = AppearanceWallpaperSpecDisk(
            assetId = spec.assetId,
            legacyLocation = spec.legacyLocation,
            fit = spec.fit.name,
            horizontalAlignment = spec.horizontalAlignment.name,
            verticalAlignment = spec.verticalAlignment.name,
            opacityPercent = spec.opacityPercent,
            blurDp = spec.blurDp,
            overlayColor = spec.overlayColor,
            dimPercent = spec.dimPercent,
        )
    }
}

private inline fun <reified T : Enum<T>> enumValue(
    raw: String?,
    fallback: T,
    configValue: (T) -> String = { it.name },
): T = enumValues<T>().firstOrNull {
    it.name.equals(raw, ignoreCase = true) || configValue(it).equals(raw, ignoreCase = true)
} ?: fallback
