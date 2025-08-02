package com.example.authn

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val authNModule = module {
    singleOf(::DiscordOAuthProvider)
}
