# hive.bb

Very simple script to control aspects of British Gas' Hive
system. Very much tailored for my needs.

## Getting started

* Install [https://github.com/borkdude/babashka](Babashka).
* Run `hive.clj authenticate -u user@gmail.com -p mypassword`.

## Available commands

* `hive.clj authenticate` - what you used above.
* `hive.clj products -n [lamp name]` - EDN output of your products.
* `hive.clj lamp-toggle -n [lamp name]` - Toggle lamp.
* `hive.clj lamp-on -n [lamp name]` - Lamp on.
* `hive.clj lamp-off -n [lamp name]` - Lamp off.
* `hive.clj lamp-brightness -n [lamp name] [+- AMOUNT]` - Increase brightness by amount.
