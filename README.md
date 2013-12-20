# Cornice

An experimental library that extracts pedestal-app from
[pedestal](https://github.com/pedestal/pedestal) and exposes it as a
cljx/cljsbuild-enabled library.

**NOTE: I made up the name Cornice. Totally not what it is called.**

## Usage

### REPL

To play with Cornice locally in a REPL run `lein repl`.
(cljsbuild and cljx has been configured to automatically run before tasks such
as this.)

```sh
$ lein repl
# ...
user=>
```
To grok the concepts in Cornice, I suggest following along with the
[walkthrough](doc/walkthrough.clj) at a REPL.

### Install

Install the library locally with the command `lein install`

```sh
$ lein install
Rewriting src/ to target/generated/clj (clj) with features #{clj} and 0 transformations.
Rewriting src/ to target/generated/cljs (cljs) with features #{cljs} and 1 transformations.
Compiling ClojureScript.
Picked up _JAVA_OPTIONS: -Xmx6144m
Compiling "target/cornice.js" from ["target/generated/cljs"]...
Successfully compiled "target/cornice.js" in 14.496827 seconds.
Created /Users/ryan/Dropbox/code/pedestal/cornice/target/cornice-0.1.0-SNAPSHOT.jar
Wrote /Users/ryan/Dropbox/code/pedestal/cornice/pom.xml
```

You should now be able to use Cornice (`[io.pedestal/cornice
"0.1.0-SNAPSHOT"]`) from any Clojure or ClojureScript (cljsbuild) application.


### On cljx

This project uses [cljx](https://github.com/lynaghk/cljx) to emit Clojure and
ClojureScript files from a single common source. Files are emitted to
`target/generated/` in their respective folders (`clj` or `cljx`).

To more easily edit `*.cljx` files, follow the [syntax
highlighting](https://github.com/lynaghk/cljx#syntax-highlighting) section of
cljx's README.

## License

Copyright 2013 Relevance, Inc., Ryan Neufeld

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
which can be found in the file [epl-v10.html](epl-v10.html) at the root of this distribution.

By using this software in any fashion, you are agreeing to be bound by
the terms of this license.

You must not remove this notice, or any other, from this software.
Copyright © 2013 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
