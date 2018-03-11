(ns treefy.core
  (:gen-class))

(require '[clojure.zip :as zip])
(require '[clojure.string :as str])


(defn treefy [seqs]
  "Transform a sequence of paths into a dictionary tree."
  (reduce #(assoc-in %1 %2 nil) {} seqs))


(defn map-zipper [map-or-pair]
  "Define a zipper data-structure to navigate trees represented by nested dictionaries."
  (if (or (and (map? map-or-pair) (= 1 (count map-or-pair))) (and (not (map? map-or-pair)) (= 2 (count map-or-pair))))
    (let [pair (if (map? map-or-pair) (first (seq map-or-pair)) map-or-pair)] ;; Is there a more concise way to do this?
      (zip/zipper
        (fn [x] (map? (nth x 1))) ; branch?
        (fn [x] (seq (nth x 1))) ; children
        (fn [x children] (assoc x 1 (into {} children))) ; create-node?
        pair)) ; root
    (throw (Exception. "Input must be a map with a single root node or a pair."))))

;; Handling of empty trees is not supported.
;; what if {} is transformed into {nil nil}?  Naa, we could instead have a special handling in the branch? and children function for empty maps
;(map-zipper {})
;(zip/branch? (zip/vector-zip []))


(defn traverse [t] (take-while (complement zip/end?) (iterate zip/next t)))
(defn nodes [t] (map zip/node (traverse t)))
(defn node-values [t] (map (comp first zip/node) (traverse t)))
(defn leaves [t] (filter (complement zip/branch?) (traverse t)))


;; We need to feed the edited locations back into the next iteration... We can't use `traverse` in the current form!!
;; We have to go the recursive way
(defn inc-all [t]
  (if (zip/end? t) t
    (recur (zip/next (zip/edit t #(update-in % [0] inc))))))

;; TODO: This should be a reimplementation of the inc-all, using sequences... except it doesn't fully work... (last node is missing)
;; Would be nice to know why it doesn't work.
;(defn edit-inc [loc] (zip/edit loc #(update-in % [0] inc)))
;(defn traverse-edit [t] (take-while (complement zip/end?) (iterate #(zip/next (edit-inc %)) t)))
;(zip/root (last (traverse-edit zipped-tree)))

(defn map-dtree [f t]
  "Walk the dictionay-based tree in depth-first and apply `f` to each node."
  (if (zip/end? t) (map-zipper (zip/root t)) ; note that we return the zipped root so that we can chain maps.
    (recur f (zip/next (zip/edit t #(update-in % [0] f))))))


(defn skip-subtree
  "Fast-forward a zipper to skip the subtree at loc."
  [loc]
  (cond
    (zip/end? loc) loc
    (some? (zip/right loc)) (zip/right loc)
    (some? (zip/up loc)) (recur (zip/up loc))
    :else (assoc loc 1 :end)))


(defn print-dtree [t]
  "Print a dtree."
  (when-not (zip/end? t)
    (do
      (let [l (count (zip/path t))
            tab (apply str (repeat l " "))]
        (println tab (first (zip/node t))))
      (recur (zip/next t)))))


;; ====================================


(defrecord FoldableNode [value status index])
(defn create-node [value] (FoldableNode. value 0 -1))
(defn create-foldable-tree
  "Create a foldable tree from a sequence of paths.
  Transform a sequence of paths into a sequncence of foldable nodes, the treeify it and wrap it in a zipper."
  [seqs]
  (map-zipper (treefy (map (partial map create-node) seqs))))


(defn print-ftree [t]
  "Print a dtree of FoldableNodes."
  (when-not (zip/end? t)
    (let [l (count (zip/path t))
          tab (apply str (repeat l "  "))
          fnode (first (zip/node t))
          index (if (= -1 (:index fnode)) "    " (format "%3d." (:index fnode)))
          status-icon (if (= 0 (:status fnode)) "+" "-")]
      (do
        (if (zip/branch? t)
          (println index tab status-icon (:value fnode))
          (println index tab "*" (:value fnode))))
      ;; Recur to next only if node status is expanded (1) otherwise need to recur right (or or back up? if there are no right nodes?)
      (if (= 1 (:status fnode))
        (recur (zip/next t))
        (recur (skip-subtree t))))))


(defn expand-all [t] (map-dtree #(assoc % :status 1) t))
(defn collapse-all [t] (map-dtree #(assoc % :status 0) t))


(defn single-child?
  "Return true if this `loc` is not a leaf and has a single child node."
  [loc]
  (and (zip/branch? loc) (= 1 (count (zip/children loc)))))


(defn merge-single-child [loc merge-fn]
  "Merge the single child node at `loc` with its only child.
  This is implemented by replacing the node at loc with its child, whose value has been edited using `(merge-fn parent child)`."
  (let [new-value (merge-fn (first (zip/node loc)) (first (zip/node (zip/down loc))))
        new-loc (zip/node (zip/edit (zip/down loc) #(assoc % 0 new-value)))]
    (zip/replace loc new-loc)))


(defn merge-single-child-rec [t merge-fn]
  "Recursively merge single-child nodes."
  (cond
    (zip/end? t) (map-zipper (zip/root t)) ;; Rewind the zipper at the root
    (not (single-child? t)) (recur (zip/next t) merge-fn)
    :else (recur (zip/next (merge-single-child t merge-fn)) merge-fn)))


(defn merge-fnodes
  "Merge the values of 2 FoldableNodes.  Can be used as `merge-fn` in `merge-single-child-rec`."
  [p c]
  (assoc p :value (str (:value p) (:value c))))


(defn edit-node-value
  "Helper function to edit a dict-tree node value"
  [loc f]
  (zip/edit loc (fn [node] (update-in node [0] f))))


(defn preexpand-single-child
  "Recursively merge single-child nodes."
  [t]
  ;We can't use map-dtree since the mapping function works on the node value that cannot know whether it's a single-child or not (only a loc can)!
  (cond
    (zip/end? t) (map-zipper (zip/root t)) ;; Rewind the zipper at the root
    (not (single-child? t)) (recur (zip/next t))
    :else (recur (zip/next (edit-node-value t #(assoc % :status 1))))))


(defn reindex
  "Do a depth first pass and assign indices based on the visit order.  Skip subtrees of collapsed nodes."
  ([t] (reindex t 1))
  ([t idx]
  (cond
    (zip/end? t) (map-zipper (zip/root t)) ;; Rewind the zipper at the root
    ;; Skip leaves
    (not (zip/branch? t)) (recur (zip/next t) idx)
    ;; We have to stop traversing the current subtree if the current node is collapsed
    :else
      (let [edited-loc (edit-node-value t #(assoc % :index idx))]
        (if (= 0 (:status (first (zip/node t))))
          (recur (skip-subtree edited-loc) (inc idx))
          (recur (zip/next edited-loc) (inc idx)))))))


(defn toggle-status
  [t idx]
  (cond
    (zip/end? t) (map-zipper (zip/root t)) ;; Rewind the zipper at the root
    (= idx (:index (first (zip/node t)))) (recur (zip/next (edit-node-value t #(update-in % [:status] (partial - 1)))) idx)
    :else (recur (zip/next t) idx)))


;; ============================================


(defn prompt
  [ftree]
  (with-open [stdin (clojure.java.io/reader "/dev/tty")]
    (loop [t (reindex ftree)]
      (print-ftree t)
      ;(print "\nSelect node or command: ")
      (print "\n> ")
      (flush)
      (let [cmd (.readLine stdin)
            new-t (cond
                    ;(= cmd h or help) (print help)
                    (str/starts-with? cmd "#") t
                    (= cmd "") t
                    (= cmd "A") (expand-all t)
                    (= cmd "C") (collapse-all t)
                    :else (toggle-status t (Integer/parseInt cmd)))]
        (recur (reindex new-t))))))


(defn split-path
  "A python-like string split function"
  ;; Differently from the python implementation, str/split will remove empty pieces: (str/split "a/") is not ["a" ""] but only ["a"]
  ;; Hence, the need for this function.
  [path]
  (loop [path path splitted []]
    (let [idx (str/index-of path "/")]
      (if (nil? idx) (conj splitted path)
        (let [[before after] (split-at idx path)
              new-path (apply str (drop 1 after))
              new-splitted (conj splitted (apply str before))]
          (recur new-path new-splitted))))))


(defn paths->ftree
  "Create a foldable tree from filepaths, applying some additional transformations."
  [lines]
  (let [seqs (map #(split-path (str/trim %)) lines)
        ;; Add back the slashes to identify folders (that is, non leaf nodes)
        ;; TODO: this manipulation should be only performed for file paths.)
        seqs (map (fn [seq] (map (partial apply str) (partition 2 2 "" (interpose "/" seq)))) seqs)
        ;; remove empty '' entries (as a result of split-path on "file/"))
        seqs (map (fn [seq] (remove empty? seq)) seqs)
        ftree (create-foldable-tree seqs)
        ;ftree (merge-single-child-rec ftree merge-fnodes) ;; TODO: make it a command
        ;ftree (preexpand-single-child ftree)] ;; TODO: make it a command
        ]
    ftree))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;(let [lines (str/split-lines (slurp *in*))]
  (let [lines (line-seq (clojure.java.io/reader *in*))]
    (prompt (paths->ftree lines))))

