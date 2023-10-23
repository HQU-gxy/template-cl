(ns tpcl.formatter
  (:import (org.slf4j.helpers MessageFormatter)))

(defn format
  "A simple wrapper around slf4j MessageFormatter.
  Format string like what you would do with format from C++20/Rust fmt/Python str.format.
  See https://www.slf4j.org/api/org/slf4j/helpers/MessageFormatter.html"
  [fmt & args]
  (let [array (into-array args)]
    (MessageFormatter/basicArrayFormat fmt array)))
