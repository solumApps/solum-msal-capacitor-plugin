#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Registers all plugin methods with the Capacitor bridge.
// Every method declared in MsAuthPlugin.swift MUST have a corresponding
// CAP_PLUGIN_METHOD entry here, otherwise the JS bridge returns UNIMPLEMENTED.
CAP_PLUGIN(MsAuthPlugin, "MsAuthPlugin",
    CAP_PLUGIN_METHOD(login,     CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(logout,    CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(logoutAll, CAPPluginReturnPromise);
)
