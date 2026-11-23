# Keep TileServices, AppWidgetProvider, Receivers, and Shortcut/SafeLaunch Activities intact under R8
-keep class io.github.xom11.vibedebug.tile.UsbDebugTileService { *; }
-keep class io.github.xom11.vibedebug.tile.WirelessDebugTileService { *; }
-keep class io.github.xom11.vibedebug.widget.UsbDebugWidgetProvider { *; }
-keep class io.github.xom11.vibedebug.widget.WidgetToggleReceiver { *; }
-keep class io.github.xom11.vibedebug.shortcut.ShortcutDispatchActivity { *; }
-keep class io.github.xom11.vibedebug.shortcut.SafeLaunchActivity { *; }
-keep class io.github.xom11.vibedebug.data.AutoOffReceiver { *; }
