

# Idle Detector Compose 🕒

[![Maven Central](https://img.shields.io/maven-central/v/io.github.angatiabenson/idle-detector-compose)](https://search.maven.org/artifact/io.github.angatiabenson/idle-detector-compose)  
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)  
[![Android API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)

A Jetpack Compose library that detects user inactivity across your entire app with zero boilerplate. Perfect for implementing session timeouts, security screens, or automatic logouts.

## Features ✨
- **Global Activity Monitoring** - Works across all screens
- **Lifecycle Aware** - Automatically pauses/resumes with activity
- **Customizable Timeouts** - Set duration in minutes, seconds, or milliseconds
- **Compose Native** - Built with 100% Jetpack Compose
- **Touch Interaction Detection** - Captures all user interactions
- **State Management** - Observable idle state via CompositionLocal

## Installation 📦

Add to your `build.gradle.kts`:

```kotlin  
dependencies {  
    implementation("io.github.angatiabenson:idle-detector-compose:0.0.1") 
}  
```  

## Basic Usage 🚀

### 1. Wrap Your App
```kotlin  
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IdleDetectorProvider(
                idleTimeout = 5.seconds,
                checkInterval = 1.seconds,
                onIdle = { /* Could show dialog or navigate here */ },
            ){ 
                IdledetectorappTheme {
                    AppContent()
                }
            }
        }
    }
}
```  

### 2. Observe State in Composables
```kotlin  
@Composable  
fun HomeScreen() {  
    val isIdle by LocalIdleDetectorState.current.collectAsState()  
     if (isIdle) {  
        IdleWarningDialog()  
 }  
    // Your screen content  
}  
```  
## API Reference 📚

### IdleDetectorProvider Parameters
| Parameter       | Type          | Default     | Description                          |  
|-----------------|---------------|-------------|--------------------------------------|  
| `timeout` | `Duration` | **Required**| Duration until idle state triggers   |  
| `onIdle` | `() -> Unit` | **Required**| Callback when idle state is reached  |  
| `checkInterval` | `Duration` | 1.second    | How often to check for inactivity    |  
| `content` | `@Composable` | **Required**| Your app content                     |  

## Troubleshooting 🔍

### Common Issues
1. **Timeout not triggering**
- Ensure `checkInterval` is shorter than `timeout`
- Verify activity isn't being paused unexpectedly

2. **State not updating**
- Check you're using `collectAsState()` on `LocalIdleDetectorState.current`

3. **Multiple callbacks**
- Wrap your `onIdle` logic in `LaunchedEffect` if navigation involved

## Compatibility 🤝

| Version | Compose | Kotlin | Min SDK |  
|---------|---------|--------|---------|  
| 1.0.0   | 1.5.0+  | 1.9.0+ | 21      |  

## License 📄
```text  
Copyright 2025 Angatia Benson  
  
Licensed under the Apache License, Version 2.0 (the "License");  
you may not use this file except in compliance with the License.  
You may obtain a copy of the License at  
  
   http://www.apache.org/licenses/LICENSE-2.0  
  
Unless required by applicable law or agreed to in writing, software  
distributed under the License is distributed on an "AS IS" BASIS,  
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  
See the License for the specific language governing permissions and  
limitations under the License.  
```  
  
---  

**Happy coding!** 🎉 If you encounter any issues, please [open an issue](https://github.com/angatiabenson/idle-detector-compose/issues).