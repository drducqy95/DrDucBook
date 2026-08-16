package com.drducbook.app.cloud

object CloudConsentScopes {

    val googleSignIn: Set<String> = setOf(
        "openid",
        "email",
        "profile",
    )

    const val googleDriveAppData = "https://www.googleapis.com/auth/drive.appdata"
}
