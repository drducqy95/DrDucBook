package com.drducbook.app

object AppIdentity {
    const val APPLICATION_ID = "com.drducbook.app"
    const val LEGACY_CODE_PACKAGE = "io.legado.app"
    const val READER_PROVIDER_AUTHORITY = "$APPLICATION_ID.readerProvider"
    const val FILE_PROVIDER_AUTHORITY = "$APPLICATION_ID.fileProvider"
    const val IMPORT_SCHEME = "drducbook"
    const val AUTH_CALLBACK = "drducbook://auth/callback"
}
