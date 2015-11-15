(ns konserve-couch.core
  "CouchDB store implemented with Clutch."
  (:require [konserve.serializers :as ser]
            [konserve.protocols :refer [-serialize -deserialize]]
            [clojure.core.async :as async
             :refer [<!! <! >! timeout chan alt! go go-loop]]
            [clojure.edn :as edn]
            [com.ashafa.clutch :refer [couch create!] :as cl]
            [konserve.protocols :refer [PEDNAsyncKeyValueStore
                                        -exists? -get-in -update-in]]))


(defrecord CouchKeyValueStore [db serializer read-handlers write-handlers]
  PEDNAsyncKeyValueStore
  (-exists? [this key]
    (go (try (cl/document-exists? db (pr-str key))
             (catch Exception e
               (ex-info "Could not access edn value."
                        {:type :access-error
                         :key key
                         :exception e})))))
  (-get-in [this key-vec]
    (let [[fkey & rkey] key-vec]
      (go (try (get-in (->> fkey
                            pr-str
                            (cl/get-document db)
                            :edn-value
                            (-deserialize serializer read-handlers))
                       rkey)
               (catch Exception e
                 (ex-info "Could not read edn value."
                          {:type :read-error
                           :key fkey
                           :exception e}))))))
  (-update-in [this key-vec up-fn]
    (go (try
          (let [[fkey & rkey] key-vec
                doc (cl/get-document db (pr-str fkey))]
            ((fn trans [doc attempt]
               (let [old (->> doc :edn-value (-deserialize serializer read-handlers))
                     new (if-not (empty? rkey)
                           (update-in old rkey up-fn)
                           (up-fn old))]
                 (cond (and (not doc) new)
                       [nil (get-in (->> (cl/put-document db {:_id (pr-str fkey)
                                                              :edn-value (pr-str new)})
                                         :edn-value
                                         (-deserialize serializer read-handlers))
                                    rkey)]

                       (and (not doc) (not new))
                       [nil nil]

                       (not new)
                       (do (cl/delete-document db doc) [(get-in old rkey) nil])

                       :else
                       (let [old* (get-in old rkey)
                             new (try (cl/update-document
                                       db
                                       doc
                                       (fn [{v :edn-value :as old}]
                                         (assoc old
                                                :edn-value (-serialize
                                                            serializer
                                                            nil
                                                            write-handlers
                                                            (if-not (empty? rkey)
                                                              (update-in (-deserialize
                                                                          serializer
                                                                          read-handlers
                                                                          v)
                                                                         rkey
                                                                         up-fn)
                                                              (up-fn (-deserialize
                                                                      serializer
                                                                      read-handlers
                                                                      v)))))))
                                      (catch clojure.lang.ExceptionInfo e
                                        (if (< attempt 10)
                                          (trans (cl/get-document db (pr-str fkey)) (inc attempt))
                                          (throw e))))
                             new* (-> (-deserialize serializer read-handlers (:edn-value new))
                                      (get-in rkey))]
                         [old* new*])))) doc 0))
          (catch Exception e
            (ex-info "Could not write edn value."
                     {:type :write-error
                      :key (first key)
                      :exception e}))))))


(defn new-couch-store
  "Constructs a CouchDB store either with name for db or a clutch DB
object and a tag-table atom, e.g. {'namespace.Symbol (fn [val] ...)}."
  [db read-handlers write-handlers]
  (let [db (if (string? db) (couch db) db)]
    (go (try
          (create! db)
          (CouchKeyValueStore. db (ser/string-serializer) read-handlers write-handlers)
          (catch Exception e
            (ex-info "Cannot open CouchDB."
                     {:type :db-error
                      :db db
                      :exception e}))))))



(comment
  (def couch-store
    (<!! (new-couch-store "geschichte"
                          (atom {})
                          (atom {}))))

  (reset! (:tag-table couch-store) {})
  (<!! (-get-in couch-store ["john"]))
  (<!! (-exists? couch-store  "johns"))
  (get-in (:db couch-store) ["john"])
  (<!! (-assoc-in couch-store ["john"] 42))
  (<!! (-update-in couch-store ["john"] inc))

  (defrecord Test [a])
  (<!! (-update-in couch-store ["peter"] (fn [_] (Test. 5))))
  (<!! (-get-in couch-store ["peter"]))

  (<!! (-update-in couch-store ["hans" :a] (fnil inc 0)))
  (<!! (-get-in couch-store ["hans"])))
