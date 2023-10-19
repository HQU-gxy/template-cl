(ns tpcl.core
  (:require [cheshire.core :as json]
            [json-path :as jp]
            [tpcl.utils :as utils]
            [tech.v3.dataset :as ds])
  (:import (java.time YearMonth)
           (java.time LocalDate)))

(def data-path "data.json")

;; convert key from string to keyword is necessary
(json/decode (slurp data-path) true)

(jp/at-path "$.visitList[*].age" (json/decode (slurp data-path) true))

(jp/at-path "$.visitList" (json/decode (slurp data-path) true))

(utils/unique (jp/at-path "$.visitList[*].TDIAGNOSE[*].FJBNAME" (json/decode (slurp data-path) true)))

;; https://techascent.github.io/tech.ml.dataset/supported-datatypes.html
(def operation-data (ds/->dataset (jp/at-path "$.operateList" (json/decode (slurp data-path) true))
                                  {:parser-fn {:date [:local-date
                                                      (fn [date]
                                                        (let [ym (YearMonth/parse date)]
                                                          (LocalDate/of (.getYear ym) (.getMonth ym) 1)))]}}))
(print operation-data)



