; Script generated for Intra Server (Windows GUI + Backend)
; Requires Inno Setup 6 (https://jrsoftware.org/isdl.php)

#define MyAppName "Intra Server"
#define MyAppVersion "3.8.2"
#define MyAppPublisher "IntraChat"
#define MyAppExeName "IntraServer.exe"

[Setup]
AppId={{C7B1E408-A932-4E90-B8DF-419827ACF59E}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=Output
OutputBaseFilename=IntraServerSetup
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "firewallrule"; Description: "Allow Intra Server through Windows Firewall (Port 8000)"; GroupDescription: "Network Configuration:"; Flags: checkedonce

[Files]
; Dist folder contents produced by build_windows_dist.py
Source: "..\dist\IntraServer\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
; Add Windows Firewall inbound rule for Port 8000
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""IntraServer"" dir=in action=allow protocol=TCP localport=8000"; Tasks: firewallrule; Flags: runhidden
; Option to launch after installation
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; Remove Windows Firewall rule upon uninstall
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""IntraServer"""; Flags: runhidden
