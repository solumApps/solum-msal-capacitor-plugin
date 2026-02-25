import { WebPlugin } from '@capacitor/core';
import type { MsAuthPlugin, MsAuthLoginOptions, MsAuthLoginResult, MsAuthLogoutOptions } from './definitions';
/**
 * Web stub — MSAL authentication is not supported in browser context.
 * The real implementations live in the Android and iOS native code.
 */
export declare class MsAuthWeb extends WebPlugin implements MsAuthPlugin {
    login(_options: MsAuthLoginOptions): Promise<MsAuthLoginResult>;
    logout(_options: MsAuthLogoutOptions): Promise<void>;
    logoutAll(_options: MsAuthLogoutOptions): Promise<void>;
}
