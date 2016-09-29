# konserve-clutch

A CouchDB implementation of the [konserve kv-protocol](https://github.com/replikativ/konserve) on top of [clutch](https://github.com/clojure-clutch/clutch).

## Usage

Add to your leiningen dependencies:
[![Clojars Project](http://clojars.org/io.replikativ/konserve-couch/latest-version.svg)](http://clojars.org/io.replikativ/konserve-couch)

The whole purpose of konserve is to have a unified associative key-value interface for
edn datastructures. Just use the standard interface functions of konserve.

You can also provide a `clutch` db object to the `new-couch-store` constructor
as an argument. We do not require additional settings beyond the konserve
serialization protocol for the store, so you can still access the store through
clutch directly wherever you need.

~~~clojure
  (require '[konserve-clutch.core :refer :all]
           '[konserve.core :as k)
  (def couch-store (<!! (new-clutch-store "my-store")))

  (<!! (k/exists? couch-store  "john"))
  (<!! (k/get-in couch-store ["john"]))
  (<!! (k/assoc-in couch-store ["john"] 42))
  (<!! (k/update-in couch-store ["john"] inc))
  (<!! (k/get-in couch-store ["john"]))

  (defrecord Test [a])
  (<!! (k/assoc-in couch-store ["peter"] (Test. 5)))
  (<!! (k/get-in couch-store ["peter"]))
~~~


## Changes

### 0.1.2

- binary support
- update to konserve 0.4
- arbitrary key length (hashing)

### 0.1.1
- use new reduced konserve interface and serializers

### 0.1.0
- factor out from konserve

## License

Copyright © 2014-2016 Christian Weilbach

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
