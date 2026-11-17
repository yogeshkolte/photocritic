import type { ConfigContext, ExpoConfig } from 'expo/config';

// APP_ENV picks the Firebase project: development -> photocritic-dev,
// production -> photocritic-prod (design file 2, file 8).
const APP_ENV = process.env.APP_ENV === 'production' ? 'production' : 'development';
const firebaseDir = APP_ENV === 'production' ? './firebase/prod' : './firebase/dev';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: 'PhotoCritic',
  slug: 'photocritic',
  scheme: 'photocritic',
  platforms: ['ios', 'android'],
  version: '0.1.0',
  orientation: 'default',
  userInterfaceStyle: 'dark',
  icon: './assets/icon.png',
  ios: {
    bundleIdentifier: 'com.yk.photocritic',
    supportsTablet: true,
    // Split View and Slide Over on iPad (G20).
    requireFullScreen: false,
    googleServicesFile: `${firebaseDir}/GoogleService-Info.plist`,
  },
  android: {
    package: 'com.yk.photocritic',
    // Nothing is backed up or restored (Q8).
    allowBackup: false,
    adaptiveIcon: {
      backgroundColor: '#0F1012',
      foregroundImage: './assets/android-icon-foreground.png',
      backgroundImage: './assets/android-icon-background.png',
      monochromeImage: './assets/android-icon-monochrome.png',
    },
    predictiveBackGestureEnabled: false,
    // G1: no broad media, storage or audio permission.
    blockedPermissions: [
      'android.permission.READ_EXTERNAL_STORAGE',
      'android.permission.WRITE_EXTERNAL_STORAGE',
      'android.permission.RECORD_AUDIO',
      'android.permission.READ_MEDIA_IMAGES',
      'android.permission.READ_MEDIA_VIDEO',
      'com.google.android.gms.permission.AD_ID',
    ],
    googleServicesFile: `${firebaseDir}/google-services.json`,
  },
  plugins: [
    'expo-router',
    'expo-secure-store',
    'expo-image',
    'expo-sharing',
    [
      'expo-splash-screen',
      { image: './assets/splash-icon.png', backgroundColor: '#0F1012', imageWidth: 160 },
    ],
    [
      'expo-image-picker',
      {
        photosPermission: 'PhotoCritic opens the photo you choose so you can ask questions about it.',
        cameraPermission: 'PhotoCritic uses the camera to take the photo you ask questions about.',
        microphonePermission: false,
      },
    ],
    '@react-native-firebase/app',
    ['@react-native-firebase/analytics', { ios: { withoutAdIdSupport: true } }],
    '@react-native-firebase/crashlytics',
    [
      'expo-build-properties',
      {
        // Q2: iOS 17, Android API 24 (author decision, 2026-11-15).
        ios: { deploymentTarget: '17.0', useFrameworks: 'dynamic' },
        android: {
          minSdkVersion: 24,
          enableMinifyInReleaseBuilds: true,
          enableShrinkResourcesInReleaseBuilds: true,
        },
      },
    ],
  ],
  experiments: {
    typedRoutes: true,
    reactCompiler: true,
  },
  owner: 'yogeshkolte',
  extra: {
    appEnv: APP_ENV,
    eas: { projectId: '1f05cafe-e55f-4d69-b0b9-b21149d62d59' },
  },
});
