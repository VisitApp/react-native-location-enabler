# @visit-health/react-native-location-enabler

Check whether an Android device meets your React Native app’s location settings requirements and prompt the user to resolve them. The package uses Google Play services to check settings such as GPS and Wi-Fi scanning. Your app is responsible for requesting runtime location permissions and obtaining coordinates.

## Installation

With npm

```sh
npm install @visit-health/react-native-location-enabler
```

With Yarn

```sh
yarn add @visit-health/react-native-location-enabler
```

Rebuild your Android app after installation to include the native module.

## Usage

### Check settings with the React hook

```jsx
import React from 'react';
import { Button, Text } from 'react-native';
import LocationEnabler from '@visit-health/react-native-location-enabler';

const config = {
  priority: LocationEnabler.PRIORITIES.HIGH_ACCURACY,
  alwaysShow: true,
  needBle: false,
};

export default function LocationSettings() {
  const [locationEnabled, requestResolution] =
    LocationEnabler.useLocationSettings(config);

  if (locationEnabled === undefined) {
    return <Text>Checking location settings…</Text>;
  }

  if (locationEnabled) {
    return <Text>Location settings are ready.</Text>;
  }

  return (
    <Button title="Enable location settings" onPress={requestResolution} />
  );
}
```

### Check settings with a listener

Subscribe before checking settings so the listener can receive the result. To offer the resolution dialog from a user action, call `LocationEnabler.requestResolutionSettings(config)` with the same configuration.

```jsx
import { useEffect, useState } from 'react';
import LocationEnabler from '@visit-health/react-native-location-enabler';

const config = {
  priority: LocationEnabler.PRIORITIES.HIGH_ACCURACY,
  alwaysShow: true,
  needBle: false,
};

export function useLocationSettingsStatus() {
  const [enabled, setEnabled] = useState(undefined);

  useEffect(() => {
    const subscription = LocationEnabler.addListener(({ locationEnabled }) => {
      setEnabled(locationEnabled);
    });

    LocationEnabler.checkSettings(config);
    return () => subscription.remove();
  }, []);

  return enabled;
}
```

## API

| Member | Behavior |
| --- | --- |
| `PRIORITIES` | Provides `HIGH_ACCURACY`, `BALANCED_POWER_ACCURACY`, `LOW_POWER`, and `NO_POWER`. |
| `useLocationSettings(config, initialStatus?)` | Checks the settings and returns `[locationEnabled, requestResolution]`. Omit `initialStatus` to start with `undefined` while the check is pending. |
| `checkSettings(config)` | Checks whether the device meets the requested settings and reports the result asynchronously through the listener. |
| `requestResolutionSettings(config)` | Asks Android to show a resolution dialog when the requested settings are unmet and the issue can be resolved by the user. |
| `addListener(callback, context?)` | Subscribes to location settings results. The callback receives `{ locationEnabled: boolean }`. Call `.remove()` on the returned subscription during cleanup. |

All configuration fields are optional. The native module starts with the values below and reuses the current value for any field omitted from a subsequent call.

| Option | Initial value | Purpose |
| --- | --- | --- |
| `priority` | `PRIORITIES.BALANCED_POWER_ACCURACY` | Sets the location accuracy and power tradeoff used for the settings check. |
| `alwaysShow` | `false` | Tells Android whether the requested settings are required for the app to continue, which can affect the dialog wording. |
| `needBle` | `false` | Includes Bluetooth Low Energy scanning in the settings requirements. |

## Project links

- [npm package](https://www.npmjs.com/package/@visit-health/react-native-location-enabler)
- [Android example source](https://github.com/VisitApp/react-native-location-enabler/tree/master/example)
- [Contributing guide](https://github.com/VisitApp/react-native-location-enabler/blob/master/CONTRIBUTING.md)
- [Code of conduct](https://github.com/VisitApp/react-native-location-enabler/blob/master/CODE_OF_CONDUCT.md)
- [MIT license](https://github.com/VisitApp/react-native-location-enabler/blob/master/LICENSE)
