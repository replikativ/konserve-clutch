# konserve-couch

A CouchDB implementation of the [konserve kv-protocol](https://github.com/replikativ/konserve).

## Usage

Add to your leiningen dependencies:
[![Clojars Project](http://clojars.org/io.replikativ/konserve-couch/latest-version.svg)](http://clojars.org/io.replikativ/konserve-couch)


~~~clojure
  (require '[konserve-couch.core :refer :all]
           '[konserve.core :as k)
  (def couch-store (<!! (new-couch-store "my-store" (atom {}) (atom {}))))

  (<!! (k/exists? couch-store  "john"))
  (<!! (k/get-in couch-store ["john"]))
  (<!! (k/assoc-in couch-store ["john"] 42))
  (<!! (k/update-in couch-store ["john"] inc))
  (<!! (k/get-in couch-store ["john"]))

  (defrecord Test [a])
  (<!! (k/assoc-in couch-store ["peter"] (Test. 5)))
  (<!! (k/get-in couch-store ["peter"]))
~~~

## TODO
- add binary blob support

## Changes

### 0.1.1
- use new reduced konserve interface and serializers

### 0.1.0
- factor out from konserve

## License

Copyright © 2014-2015 Christian Weilbach

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
