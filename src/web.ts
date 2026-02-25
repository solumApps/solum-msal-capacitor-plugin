import { WebPlugin } from '@capacitor/core';
import type { MsAuthPlugin, MsAuthLoginOptions, MsAuthLoginResult, MsAuthLogoutOptions } from './definitions';

/**
 * Web stub — MSAL authentication is not supported in browser context.
 * The real implementations live in the Android and iOS native code.
 */
export class MsAuthWeb extends WebPlugin implements MsAuthPlugin {
  async login(_options: MsAuthLoginOptions): Promise<MsAuthLoginResult> {
    throw this.unimplemented('MsAuth.login is not available on web.');
  }

  async logout(_options: MsAuthLogoutOptions): Promise<void> {
    throw this.unimplemented('MsAuth.logout is not available on web.');
  }

  async logoutAll(_options: MsAuthLogoutOptions): Promise<void> {
    throw this.unimplemented('MsAuth.logoutAll is not available on web.');
  }
}
