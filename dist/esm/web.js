import { WebPlugin } from '@capacitor/core';
/**
 * Web stub — MSAL authentication is not supported in browser context.
 * The real implementations live in the Android and iOS native code.
 */
export class MsAuthWeb extends WebPlugin {
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
