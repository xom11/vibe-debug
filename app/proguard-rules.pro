# Keep TileService and AppWidgetProvider entry points intact under R8
-keep class io.github.l3n4k4.usbdebugswitch.tile.UsbDebugTileService { *; }
-keep class io.github.l3n4k4.usbdebugswitch.widget.UsbDebugWidgetProvider { *; }
