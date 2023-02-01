package co.touchlab.kampkit.models

import co.touchlab.kermit.Logger

expect class PlatformViewModel(
    breedRepository: BreedRepository,
    log: Logger
) {
    val breedViewModel: BreedViewModel
}
