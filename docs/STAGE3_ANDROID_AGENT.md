# Stage 3: Android Emulator Agent Behavior

## Implementation Summary

Implemented agent behavior for automatic on-device search using Android emulator.

### Architecture

The implementation follows clean architecture principles with clear separation of concerns:

1. **DeviceSearchExecutor**: Interface for device search operations
2. **AdbCommandExecutor**: Handles ADB command execution
3. **AndroidEmulatorController**: Manages emulator lifecycle
4. **DeviceSearchService**: Orchestrates the search workflow

### Key Components

#### 1. AdbCommandExecutor
- Executes ADB commands using non-blocking I/O
- Checks emulator status
- Waits for device boot completion
- Launches Chrome with search queries

#### 2. AndroidEmulatorController
- Starts/stops Android emulators
- Checks if emulator is running
- Lists available AVDs
- Waits for full system boot

#### 3. DeviceSearchService
- Implements DeviceSearchExecutor interface
- Coordinates emulator control and Chrome launching
- Handles the complete search workflow

#### 4. Agent Integration
- Intent detection in `OpenRouterAgent`
- Automatic search execution when user requests on-device search
- Supports both Russian and English queries

### Files Created

- `src/main/kotlin/org/example/agent/android/DeviceSearchExecutor.kt`
- `src/main/kotlin/org/example/agent/android/AdbCommandExecutor.kt`
- `src/main/kotlin/org/example/agent/android/AndroidEmulatorController.kt`
- `src/main/kotlin/org/example/agent/android/DeviceSearchService.kt`

### Files Modified

- `src/main/kotlin/org/example/agent/OpenRouterAgent.kt`: Added intent detection and device search integration
- `src/main/kotlin/org/example/config/AppConfig.kt`: Added Android SDK path configuration
- `src/main/kotlin/org/example/Main.kt`: Initialize device search service

### Configuration

Set the Android SDK path:

```bash
export ANDROID_SDK_PATH=/path/to/android/sdk
```

Or in `local.properties`:
```properties
ANDROID_SDK_PATH=/path/to/android/sdk
```

The service automatically constructs paths for:
- ADB: `$ANDROID_SDK_PATH/platform-tools/adb`
- Emulator: `$ANDROID_SDK_PATH/emulator/emulator`

### Usage

When a user asks for on-device search, the agent automatically:

1. Detects the intent (keywords: "найди на устройстве", "find on device", etc.)
2. Checks if emulator is running
3. Starts emulator if needed
4. Waits for full boot
5. Launches Chrome browser
6. Performs search with the user's query

### Example Queries

- "Найди на устройстве: Kotlin programming"
- "Find on device: Android development"
- "Поиск на эмуляторе: weather forecast"

### Error Handling

The implementation includes comprehensive error handling:
- Emulator startup failures
- ADB connection issues
- Chrome launch failures
- Timeout handling for boot completion

