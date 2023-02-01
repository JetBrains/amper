package co.touchlab.kampkit.models

import co.touchlab.kermit.Logger

/**
 * Base class that provides a Kotlin/Native equivalent to the AndroidX `ViewModel`.
 */
actual class PlatformViewModel actual constructor(
    breedRepository: BreedRepository,
    log: Logger
) {
    actual val breedViewModel = BreedViewModel(breedRepository, log)
}
