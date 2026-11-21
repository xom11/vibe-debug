# Keep TileServices, AppWidgetProvider, and ShortcutDispatchActivity intact under R8
-keep class io.github.l3n4k4.usbdebugswitch.tile.UsbDebugTileService { *; }
-keep class io.github.l3n4k4.usbdebugswitch.tile.WirelessDebugTileService { *; }
-keep class io.github.l3n4k4.usbdebugswitch.widget.UsbDebugWidgetProvider { *; }
-keep class io.github.l3n4k4.usbdebugswitch.shortcut.ShortcutDispatchActivity { *; }
