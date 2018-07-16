(ns kaocha.type.ns
  (:require [clojure.test :as t]
            [kaocha.core-ext :refer :all]
            [kaocha.testable :as testable]
            [clojure.spec.alpha :as s]
            [kaocha.output :as out]))

(defn ->testable [ns-name]
  {:kaocha.testable/type :kaocha.type/ns
   :kaocha.testable/id   (keyword (str ns-name))
   :kaocha.ns/name       ns-name})

(defmethod testable/-load :kaocha.type/ns [testable]
  ;; TODO If the namespace has a test-ns-hook function, call that:
  ;; if-let [v (find-var (symbol (:kaocha.ns/name testable) "test-ns-hook"))]

  (let [ns-name (:kaocha.ns/name testable)]
    (try
      ;; TODO, unload first
      (require ns-name :reload)
      (let [ns-obj          (the-ns ns-name)
            ns-meta         (meta ns-obj)
            each-fixture-fn (t/join-fixtures (::t/each-fixtures ns-meta))]
        (->> ns-obj
             ns-publics
             (filter (comp :test meta val))
             (map (fn [[sym var]]
                    (let [nsname    (:kaocha.ns/name testable)
                          test-name (symbol (str nsname) (str sym))]
                      {:kaocha.testable/type :kaocha.type/var
                       :kaocha.testable/id   (keyword test-name)
                       :kaocha.testable/meta (meta var)
                       :kaocha.var/name      test-name
                       :kaocha.var/var       var
                       :kaocha.var/test      (:test (meta var))
                       :kaocha.var/wrap      (if (::t/each-fixtures ns-meta)
                                               [(fn [t] #(each-fixture-fn t))]
                                               [])})))
             (assoc testable
                    :kaocha.testable/meta (meta ns-obj)
                    :kaocha.ns/ns ns-obj
                    :kaocha.test-plan/tests)))
      (catch Throwable t
        (out/warn "Failed loading " ns-name " " (.getMessage t))
        (assoc testable :kaocha.test-plan/load-error t)))))

(defn run-tests [testable test-plan fixture-fn]
  ;; It's not guaranteed the the fixture-fn returns the result of calling the
  ;; tests function, so we need to put it in a box for reference.
  (let [result (atom (:kaocha.test-plan/tests testable))]
    (fixture-fn #(swap! result testable/run-testables test-plan))
    @result))

(defmethod testable/-run :kaocha.type/ns [testable test-plan]
  (let [do-report #(t/do-report (merge {:ns              (:kaocha.ns/ns testable)
                                        :kaocha/testable testable}
                                       %))]
    (binding [t/*report-counters* (ref t/*initial-report-counters*)]
      (do-report {:type :begin-test-ns})
      (if-let [load-error (:kaocha.test-plan/load-error testable)]
        (do
          (do-report {:type     :error
                      :message  "Failed to load namespace."
                      :expected nil
                      :actual   load-error})
          (do-report {:type :end-test-ns})
          assoc testable :kaocha.result/error 1)
        (let [ns-meta         (:kaocha.testable/meta testable)
              once-fixture-fn (t/join-fixtures (::t/once-fixtures ns-meta))
              tests           (run-tests testable test-plan once-fixture-fn)
              result          (assoc (dissoc testable :kaocha.test-plan/tests)
                                     :kaocha.result/tests
                                     tests)]
          (do-report {:type :end-test-ns})
          result)))))

(s/def :kaocha.type/ns (s/keys :req [:kaocha.testable/type
                                     :kaocha.testable/id
                                     :kaocha.ns/name]
                               :opt [:kaocha.ns/ns
                                     :kaocha.test-plan/tests]))

(s/def :kaocha.ns/name simple-symbol?)
(s/def :kaocha.ns/ns   ns?)
