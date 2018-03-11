(ns treefy.core-test
  (:require [clojure.test :refer :all]
            [treefy.core :refer :all]))

;; If we wanted to use (FoldableNode. x y z) we need to (:import [treefy.core FoldableNode]) or something

(require '[clojure.zip :as zip])

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 1 1)))

  (testing "treefy"
    (is (= {} (treefy [])))
    (is (= {:a {1 nil}} (treefy [[:a 1]])))
    (is (= {:a {:b {1 nil} :c {2 nil}}} (treefy [[:a :b 1] [:a :c 2]]))))

  (testing "map-zipper"
    (is (= (map-zipper [:a nil]) (map-zipper {:a nil})))
    (is (= (map-zipper [:a nil]) (map-zipper (zip/root (map-zipper [:a nil])))))
    (is (= :a (first (zip/node (map-zipper [:a nil])))))
    (is (= false (zip/branch? (map-zipper [:a nil]))))
    (is (= true (zip/end? (zip/next (map-zipper [:a nil]))))))
    ;(must-throw (map-zipper {:a nil :b nil}))
    ;(must-throw (seq {:a nil :b nil}))

  (testing "traversals"
    (let [t (map-zipper {:a {:b {:c nil} :d nil}})]
      (is (= '(:a :b :c :d) (map (comp first zip/node) (traverse t))))
      (is (= '(:c :d) (map (comp first zip/node) (leaves t))))))

  (testing "edits"
    (let [t (map-zipper {1 {2 {3 nil} 4 nil}})]
      (is (= [2 {3 {4 nil} 5 nil}] (zip/root (inc-all t))))
      (is (= [2 {3 {4 nil} 5 nil}] (zip/root (map-dtree inc t))))))

  (testing "FoldableNodes"
    (is (= (->FoldableNode :a 0 -1) (create-node :a)))
    (is (= '(1 1 1 1)
            (->> [[1 2 3] [1 4]]
                (create-foldable-tree)
                (map-dtree #(assoc % :status 1))
                (traverse)
                (map (comp :status first zip/node))))))

  (testing "merge-single-child"
    (is (= [3 {3 nil}] (zip/root (merge-single-child (map-zipper {1 {2 {3 nil}}}) +))))
    (is (= ["a/b/" {"c" nil}] (zip/root (merge-single-child (map-zipper {"a/" {"b/" {"c" nil}}}) str))))
    (is (= '("a/b/" "c/d/" "e" "f")
        (-> [["a/" "b/" "c/" "d/" "e"] ["a/" "b/" "f"]]
             (create-foldable-tree)
             (merge-single-child-rec merge-fnodes)
             (node-values)
             (->> (map :value))))))

  (testing "skip-subtree"
    (let [t (map-zipper (treefy [[:a :b :c]]))]
      (is (= '(:a :a :a) (map (comp first zip/node skip-subtree) (traverse t))))
      (is (= '(true true true) (map (comp zip/end? skip-subtree) (traverse t)))))
    (let [t (map-zipper (treefy [[:a :b :c] [:a :b :d] [:a :e]]))]
      (is (= '(:a :e :d :e :a) (map (comp first zip/node skip-subtree) (traverse t))))
      (is (= '(true false false false true) (map (comp zip/end? skip-subtree) (traverse t))))))

  (testing "split-path"
    (is (= [""] (split-path "")))
    (is (= ["" ""] (split-path "/")))
    (is (= ["pre" ""] (split-path "pre/")))
    (is (= ["" "post"] (split-path "/post")))
    (is (= ["pre" "post"] (split-path "pre/post")))
    (is (= ["pre" "" "post"] (split-path "pre//post")))
    (is (= ["a" "b" "c"] (split-path "a/b/c")))
    (is (= ["a" "b" "c" ""] (split-path "a/b/c/")))))


;; TODO: test printing?
;(def zipped-tree (map-zipper (treefy [[1 2 3] [1 2 4]])))
;(def ft (create-foldable-tree [[1 2 3] [1 2 4]]))
;(print-dtree zipped-tree)
;(print-ftree ft)
;(print-ftree (expand-all ft))

;; TODO: test reindex and paths-ftree
;(def t (create-foldable-tree [["a/" "b/" "c/" "d/" "e"] ["a/" "b/" "f"]]))
;(reindex (create-foldable-tree [["a/" "b/" "c/" "d/" "e"] ["a/" "b/" "f"]]))
;(reindex (toggle-status (reindex (create-foldable-tree [["a/" "b/" "c/" "d/" "e"] ["a/" "b/" "f"]])) 1))
;(paths->ftree ["a/b/c" "a/b/c/"]) ;; TODO: This does not work because of the way "str/split" works...  I think I have to add an extra space if the line ends with "/")
;(paths->ftree ["a/b/c" "a/d"])
;(print-ftree (reindex (paths->ftree ["a/b/c" "a/d"])))
;(print-ftree (reindex (expand-all (paths->ftree ["a/b/c" "a/b/d"]))))


;; This can be used for ballpark performance testing
;(defn gen-vec []
;  (let [vec-size (inc (rand-int 20))
;        max-n 100
;        random-numbers (fn [] (repeatedly vec-size #(rand-int max-n)))]
;    (random-numbers)))
;(count (treefy (repeatedly 100000 gen-vec)))
;(count (map (partial map create-node) (repeatedly 3 gen-vec)))
;(count (doall (map (partial map create-node) (repeatedly 1000000 gen-vec))))

