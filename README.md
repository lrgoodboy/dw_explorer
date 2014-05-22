# DW Explorer #

## Build & Run ##

```sh
$ cd DW_Explorer
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
