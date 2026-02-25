import { registerPlugin } from '@capacitor/core';
import { MsAuthWeb } from './web';
/**
 * The MsAuthPlugin singleton.
 *
 * Import this in your app exactly the same way you imported
 * MsAuthPlugin from @recognizebv/capacitor-plugin-msauth:
 *
 *   import { MsAuthPlugin } from '@solum/capacitor-msauth';
 */
const MsAuthPlugin = registerPlugin('MsAuthPlugin', {
    web: () => new MsAuthWeb(),
});
export * from './definitions';
export { MsAuthPlugin };
