#!/bin/bash

# Build script for AdvancedTerminal Android application
echo "Building AdvancedTerminal Android Application..."

# Set ANDROID_HOME if not set
if [ -z "$ANDROID_HOME" ]; then
  # Try to find Android SDK automatically
  if [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
  else
    echo "Error: ANDROID_HOME not set and could not be auto-detected."
    echo "Please set ANDROID_HOME to your Android SDK location."
    exit 1
  fi
fi

echo "Using Android SDK at: $ANDROID_HOME"

# Check if required tools exist
if [ ! -f "$ANDROID_HOME/build-tools/30.0.3/aapt2" ]; then
  echo "Error: AAPT2 not found. Please install Build Tools 30.0.3 or update the path in this script."
  exit 1
fi

# Create a simple Gradle command
GRADLEW_CMD="$ANDROID_HOME/tools/bin/sdkmanager --update"
echo "Updating SDK manager..."
$GRADLEW_CMD

# Run the build directly using Android Studio if installed
if [ -d "/Applications/Android Studio.app" ]; then
  echo "Android Studio found. You can open the project in Android Studio:"
  echo "open -a \"Android Studio\" /Volumes/DATA_vivek/GITHUB/terimal_for_andoird"
  echo ""
  echo "Would you like to open the project in Android Studio now? (y/n)"
  read -r response
  if [[ "$response" =~ ^([yY][eE][sS]|[yY])+$ ]]; then
    open -a "Android Studio" /Volumes/DATA_vivek/GITHUB/terimal_for_andoird
  fi
else
  echo "Android Studio not found. Please install it to build this project easily."
fi

echo ""
echo "Alternatively, you can build the project using the following command:"
echo "cd /Volumes/DATA_vivek/GITHUB/terimal_for_andoird && ./gradlew assembleDebug"

echo ""
echo "Script completed."
