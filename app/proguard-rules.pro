# Keep TileServices, AppWidgetProvider, Receivers, and Shortcut/SafeLaunch Activities intact under R8
-keep class io.github.l3n4k4.usbdebugswitch.tile.UsbDebugTileService { *; }
-keep class io.github.l3n4k4.usbdebugswitch.tile.WirelessDebugTileService { *; }
-keep class io.github.l3n4k4.usbdebugswitch.tile.DevOptionsTileService { *; }
-keep class io.github.l3n4k4.usbdebugswitch.tile.KeepAwakeTileService { *; }
-keep class io.github.l3n4k4.usbdebugswitch.widget.UsbDebugWidgetProvider { *; }
-keep class io.github.l3n4k4.usbdebugswitch.shortcut.ShortcutDispatchActivity { *; }
-keep class io.github.l3n4k4.usbdebugswitch.shortcut.SafeLaunchActivity { *; }
-keep class io.github.l3n4k4.usbdebugswitch.data.AutoOffReceiver { *; }
