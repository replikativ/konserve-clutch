(ns konserve-couch.core
  "CouchDB store implemented with Clutch."
  (:require [konserve.serializers :as ser]
            [konserve.protocols :refer [-serialize -deserialize]]
            [hasch.core :refer [uuid]]
            [clojure.core.async :as async
             :refer [<!! <! >! timeout chan alt! go go-loop]]
            [clojure.edn :as edn]
            [com.ashafa.clutch :refer [couch create!] :as cl]
            [konserve.protocols :refer [PEDNAsyncKeyValueStore
                                        -exists? -get-in -update-in
                                        PBinaryAsyncKeyValueStore
                                        -bassoc -bget]]))


(defrecord CouchKeyValueStore [db serializer read-handlers write-handlers locks]
  PEDNAsyncKeyValueStore
  (-exists? [this key]
    (let [id (str (uuid key))]
      (go (try (cl/document-exists? db id)
               (catch Exception e
                 (ex-info "Could not access edn value."
                          {:type :access-error
                           :id id
                           :key key
                           :exception e}))))))
  (-get-in [this key-vec]
    (let [[fkey & rkey] key-vec
          id (str (uuid fkey))]
      (go (try (get-in (->> id
                            (cl/get-document db)
                            :edn-value
                            (-deserialize serializer read-handlers)
                            second)
                       rkey)
               (catch Exception e
                 (ex-info "Could not read edn value."
                          {:type :read-error
                           :id id
                           :key fkey
                           :exception e}))))))
  (-update-in [this key-vec up-fn]
    (go (try
          (let [[fkey & rkey] key-vec
                id (str (uuid fkey))
                doc (cl/get-document db id)]
            ((fn trans [doc attempt]
               (let [old (->> doc :edn-value (-deserialize serializer read-handlers) second)
                     new (if-not (empty? rkey)
                           (update-in old rkey up-fn)
                           (up-fn old))]
                 (prn "NEW" new)
                 (cond (and (not doc) new)
                       [nil (get-in (->> (cl/put-document db {:_id id
                                                              :edn-value #_(pr-str [key-vec new])
                                                              (with-out-str
                                                                (-serialize serializer
                                                                            *out*
                                                                            write-handlers
                                                                            [key-vec new]))})
                                         :edn-value
                                         (-deserialize serializer read-handlers)
                                         second)
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
                                                :edn-value (with-out-str
                                                             (-serialize
                                                              serializer
                                                              *out*
                                                              write-handlers
                                                              [key-vec
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
                                                                         v)))])))))
                                      (catch clojure.lang.ExceptionInfo e
                                        (if (< attempt 10)
                                          (trans (cl/get-document db id) (inc attempt))
                                          (throw e))))
                             new* (-> (-deserialize serializer read-handlers (:edn-value new))
                                      second
                                      (get-in rkey))]
                         [old* new*])))) doc 0))
          (catch Exception e
            (ex-info "Could not write edn value."
                     {:type :write-error
                      :key key-vec
                      :exception e})))))

  PBinaryAsyncKeyValueStore
  (-bget [this key locked-cb]
    (go (locked-cb
         {:input-stream (cl/get-attachment db (str (uuid key))
                                          (pr-str key))})))
  (-bassoc [this key val]
    (let [id (str (uuid key))]
      (go
        (when-let [doc (cl/get-document db id)]
          (cl/delete-document db doc))
        (cl/put-document db {:_id id} :attachments [{:data val
                                                     :filename (pr-str key)
                                                     :mime-type "application/octet-stream"}])))))


(defn new-couch-store
  "Constructs a CouchDB store either with name for db or a clutch DB
object and a tag-table atom, e.g. {'namespace.Symbol (fn [val] ...)}."
  [db & {:keys [serializer read-handlers write-handlers]
         :or {serializer (ser/string-serializer)
              read-handlers (atom {})
              write-handlers (atom {})}}]
  (let [db (if (string? db) (couch db) db)]
    (go (try
          (create! db)
          (map->CouchKeyValueStore {:db db
                                    :serializer serializer
                                    :read-handlers read-handlers
                                    :write-handlers write-handlers
                                    :locks (atom {})})
          (catch Exception e
            (ex-info "Cannot open CouchDB."
                     {:type :db-error
                      :db db
                      :exception e}))))))




