package co.touchlab.kampkit.models

import androidx.lifecycle.ViewModel as AndroidXViewModel
import co.touchlab.kermit.Logger

actual class PlatformViewModel actual constructor(
    breedRepository: BreedRepository,
    log: Logger
) : AndroidXViewModel() {
    actual val breedViewModel = BreedViewModel(breedRepository, log)
    override fun onCleared() {
        super.onCleared()
        breedViewModel.onCleared()
    }
}
