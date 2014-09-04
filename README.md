# DW Explorer #

## Build & Run ##

Install [Bower][1] first.

```sh
$ cd dw_explorer
$ bower install
$ ./sbt
> container:start
> browse
```

If `browse` doesn't launch your browser, manually open [http://localhost:8080/](http://localhost:8080/) in your browser.

## Automatic Code Reloading

```bash
$ ./sbt
> container:start
> ~;copy-resources;aux-compile
```

## Import Into Eclipse

```bash
$ ./sbt eclipse
```

[1]: http://bower.io/
