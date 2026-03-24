package com.franklinharper.social.media.client.app

internal object PlaceholderAppContainer : AppContainer {
    override val dependencies: AppDependencies
        get() = error("App container is not implemented yet")
}

internal expect fun createAppContainer(): AppContainer
