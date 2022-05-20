# Layout inspector for Android

Compose desktop application for viewing layouts and measure distance between views. 

Uses adb commands under the hood `adb shell screencap` for screenshots and `adb shell uiautomator dump` for layout.

## Getting started

### Requirements

- Adb installed
- Java 11 to run
- Java 15 to package an installable

### Running the application locally

```bash
./gradlew run
```

### Installing

```bash
./gradlew package
```

You'll get an installation file depending on what OS you are running.

For linux install via `dpkg -i`.

For mac just open the dmg file.

The app will be installed as ComposeAndroidLayoutInspector. 

## Known issues

### Failed to get devices

Adb configuration might be wonky, either it can't be found or it's a different version from what you normally use.

The application will prompt you to define what adb to run. Enter it into the text field. Run `which adb` to find it. This is then saved as `~/.ComposeAndroidLayoutInspector`.

### Selection box is cropped

For some reason on a device such as Pixel3XL the selection box does not span the whole view. it seems this is an issue with 
`uiautomator  dump` command not getting correct layout. If you are using an emulator try to use a different version.
