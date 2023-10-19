(ns tpcl.utils)

(defn delete-keys
  "return a map with `keys` removed from `map`"
  [map & keys]
  (reduce (fn [m k] (dissoc m k)) map keys))

(defn left-keys
  "return a map with `keys` in keys and values in `map`"
  [map & keys]
  (reduce (fn [m k] (assoc m k (get map k))) {} keys))


(defn unique
  "a naive implementation of unique using a set"
  [coll]
  (into #{} coll))

;; (contains? ["test"] "test") => false
