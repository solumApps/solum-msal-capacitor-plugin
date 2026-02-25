import { registerPlugin } from '@capacitor/core';
import type { MsAuthPlugin } from './definitions';
import { MsAuthWeb } from './web';

/**
 * The MsAuthPlugin singleton.
 *
 * Import this in your app exactly the same way you imported
 * MsAuthPlugin from @recognizebv/capacitor-plugin-msauth:
 *
 *   import { MsAuthPlugin } from '@solum/capacitor-msauth';
 */
const MsAuthPlugin = registerPlugin<MsAuthPlugin>('MsAuthPlugin', {
  web: () => new MsAuthWeb(),
});

export * from './definitions';
export { MsAuthPlugin };
