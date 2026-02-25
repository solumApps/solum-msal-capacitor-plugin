Pod::Spec.new do |s|
  s.name             = 'SolumMsauth'
  s.version          = '1.0.0'
  s.summary          = 'Microsoft MSAL Capacitor plugin for Solum'
  s.description      = <<-DESC
    Drop-in replacement for @recognizebv/capacitor-plugin-msauth.
    Includes production bug fixes for iOS silent login and account
    cache handling. Auto-injects Android BrowserTabActivity manifest
    entries so no manual AndroidManifest.xml edits are needed.
  DESC
  s.homepage         = 'https://github.com/solumApps/solum-msal-capacitor-plugin'
  s.license          = { :type => 'MIT' }
  s.author           = { 'Solum' => 'mobile@solum.com' }
  s.source           = { :git => 'https://github.com/solumApps/solum-msal-capacitor-plugin.git', :tag => s.version.to_s }
  s.source_files     = 'ios/Plugin/**/*.{swift,m,h}'
  s.ios.deployment_target = '14.0'
  s.dependency 'Capacitor'
  s.dependency 'MSAL', '~> 1.2'
  s.swift_version    = '5.1'
end
