# AnotherTermLite
Local pty, USB serial port and Telnet terminal client for Android.

* https://play.google.com/store/apps/details?id=green_green_avk.anothertermlite
* https://github.com/green-green-avk/AnotherTermLite/wiki


Why another one? Just to have:

* Local pty, USB serial port (UART) etc. support in the same application;

* Adequate screen input methods as long as a terminal requires specific keyboard functions.

* Ability to set fixed screen columns and/or rows numbers.


Features:

* Minimum supported Android version is 4.0 Ice Cream Sandwich.

* Supported USB UART devices: Generic USB CDC, CP210X, FTDI, PL2303, CH34x, CP2130 SPI-USB.

* Local Linux pty is supported. Feel free to use PRoot with some Linux environment:
https://github.com/green-green-avk/build-proot-android/blob/master/README.md .

* Shell tool to interact with the Android environment is also present.
   - Content exchange between other applications and own files / pipes has been implemented.
   - It also works in chrooted environments (PRoot at least).
   - USB serial port dongle access from the command line is also implemented.

* Telnet (no encryption).

* No SSH in the Lite version.

* And no MoSH.

* Builtin screen keyboard and mouse.

* Different charsets and customizable key mapping support.


3rd party components:

* USB UART: https://github.com/felHR85/UsbSerial
* Console font: https://www.fontsquirrel.com/fonts/dejavu-sans-mono
