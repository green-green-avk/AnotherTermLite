# AnotherTermLite
Local pty, USB serial port and Telnet terminal client for Android.

https://play.google.com/store/apps/details?id=green_green_avk.anothertermlite



Why another one? Just to have:

* Local pty, USB serial port (UART) etc. support in the same application;

* Adequate screen input methods as long as a terminal requires specific keyboard functions.


Features:

* Interface for custom connection modules in separate applications will be added later.

* Supported USB UART devices: Generic USB CDC, CP210X, FTDI, PL2303, CH34x, CP2130 SPI-USB.

* No SSH in the Lite version.

* And no MoSH.

* Telnet (no encryption).

* Local Linux pty (aka terminal emulator) is supported. Feel free to follow directions from
http://kevinboone.net/android_nonroot.html
"Using standard Linux utilities in a stock, non-rooted Android device"
by Kevin Boone. ;)

* The introduction of a shell utility to interact with the Android environment has been started. Content exchange between other applications and own files / pipes has been implemented like in Termux. USB serial port dongle access from the command line has also been implemented (unlike Termux) ;).

* Arbitrary content providers support will be added later.

* Different charsets and customizable key mapping support.

* Builtin screen keyboard and mouse.

* Minimum supported Android version is 4.0 Ice Cream Sandwich.


3rd party components:

* USB UART: https://github.com/felHR85/UsbSerial
* Console font: https://www.fontsquirrel.com/fonts/dejavu-sans-mono
