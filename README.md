# konserve-couch

A CouchDB implementation of the [konserve kv-protocol](https://github.com/ghubber/konserve).

## Usage

Add `[net.polyc0l0r/konserve "0.1.0-SNAPSHOT"]` to your dependencies.

~~~clojure
  (def couch-store (<!! (new-couch-store "geschichte" (atom {}))))

  (<!! (-exists? couch-store  "john"))
  (<!! (-get-in couch-store ["john"]))
  (<!! (-assoc-in couch-store ["john"] 42))
  (<!! (-update-in couch-store ["john"] inc))
  (<!! (-get-in couch-store ["john"]))

  (defrecord Test [a])
  (<!! (-assoc-in couch-store ["peter"] (Test. 5)))
  (<!! (-get-in couch-store ["peter"]))
~~~

## License

Copyright © 2014-2015 Christian Weilbach

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
