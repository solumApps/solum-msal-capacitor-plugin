'use strict';

var core = require('@capacitor/core');

/**
 * Web stub — MSAL authentication is not supported in browser context.
 * The real implementations live in the Android and iOS native code.
 */
class MsAuthWeb extends core.WebPlugin {
    async login(_options) {
        throw this.unimplemented('MsAuth.login is not available on web.');
    }
    async logout(_options) {
        throw this.unimplemented('MsAuth.logout is not available on web.');
    }
    async logoutAll(_options) {
        throw this.unimplemented('MsAuth.logoutAll is not available on web.');
    }
}

/**
 * The MsAuthPlugin singleton.
 *
 * Import this in your app exactly the same way you imported
 * MsAuthPlugin from @recognizebv/capacitor-plugin-msauth:
 *
 *   import { MsAuthPlugin } from '@solum/capacitor-msauth';
 */
const MsAuthPlugin = core.registerPlugin('MsAuthPlugin', {
    web: () => new MsAuthWeb(),
});

exports.MsAuthPlugin = MsAuthPlugin;
//# sourceMappingURL=plugin.cjs.js.map
