(ns post-hook.kaocha
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as str]
   [kaocha.output :as output]
   [kaocha.report :as report]))

(defn pre-test [testable test-plan]
  (assoc testable ::test-start-time (java.time.Instant/now)))

(defn post-test [testable test-plan]
  (assoc testable ::test-end-time (java.time.Instant/now)))

(defn print-output-during-failure [m]
  (pprint [:keys (-> m :kaocha/testable keys)]))

(defmethod report/fail-summary :kaocha/fail-type
  [{:keys [testing-contexts testing-vars] :as m}]
  ;; Adapted from the 1.60.945 upstream method to only change the
  ;; final call so we can append custom information.
  (println (str "\n" (output/colored :red "FAIL") " in")
           (report/testing-vars-str m))
  (when (seq testing-contexts)
    (println (str/join " " (reverse testing-contexts))))
  (when-let [message (:message m)]
    (println message))
  (if-let [expr (::printed-expression m)]
    (print expr)
    (report/print-expr m))
  (print-output-during-failure m))
