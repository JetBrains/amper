## KMM Sample App using a shared business logic with:

- 📡 Network: using Ktor & Kotlinx.serialization
- 💾 Persistence: using Realm Database

The UI part 🎨 is platform specific:
- 🤖 Android: Jetpack compose
- 🍏 iOS: SwiftUI

## Requirements

- JDK 11
- Android Studio [Dolphin (2021.3.1)](https://developer.android.com/studio)

## Screenshots:

### Android

<img src="./Screenshots/Android/Search.png" width="400" > <img src="./Screenshots/Android/Results.png" width="400">
<img src="./Screenshots/Android/Saved.png" width="400" > <img src="./Screenshots/Android/About.png" width="400" >


### iOS

<img src="./Screenshots/iOS/Search_Pending.png" width="400" > <img src="./Screenshots/iOS/Search.png" width="400">
<img src="./Screenshots/iOS/Saved.png" width="400" > <img src="./Screenshots/iOS/SavedBooks.png" width="400" >
<img src="./Screenshots/iOS/About.png" width="400" >                                                                                                            

Run using Cocoapods:
```
cd iosApp
pod install
open iosApp.xcworkspace
```