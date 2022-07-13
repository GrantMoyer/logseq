(ns logseq.graph-parser.property
  "Property fns needed by graph-parser"
  (:require [logseq.graph-parser.util :as gp-util]
            [clojure.string :as string]
            [clojure.set :as set]
            [goog.string :as gstring]
            [goog.string.format]))

(defn properties-ast?
  [block]
  (and
   (vector? block)
   (contains? #{"Property_Drawer" "Properties"}
              (first block))))

(def markers
  #{"now" "later" "todo" "doing" "done" "wait" "waiting"
    "canceled" "cancelled" "started" "in-progress"})

(def built-in-extended-properties (atom #{}))
(defn register-built-in-properties
  [props]
  (reset! built-in-extended-properties (set/union @built-in-extended-properties props)))

(defn built-in-properties
  "Properties that should only be used by logseq"
  []
  (set/union
   #{:id :custom-id :background-color :heading :collapsed :created-at :updated-at :last-modified-at :created_at :last_modified_at :query-table :query-properties :query-sort-by :query-sort-desc
     :ls-type :hl-type :hl-page :hl-stamp}
   (set (map keyword markers))
   @built-in-extended-properties))

(defonce properties-start ":PROPERTIES:")
(defonce properties-end ":END:")
(defonce properties-end-pattern
  (re-pattern (gstring/format "%s[\t\r ]*\n|(%s\\s*$)" properties-end properties-end)))

(defn contains-properties?
  [content]
  (when content
    (and (string/includes? content properties-start)
         (gp-util/safe-re-find properties-end-pattern content))))

(defn ->new-properties
  "New syntax: key:: value"
  [content]
  (if (contains-properties? content)
    (let [lines (string/split-lines content)
          start-idx (.indexOf lines properties-start)
          end-idx (.indexOf lines properties-end)]
      (if (and (>= start-idx 0) (> end-idx 0) (> end-idx start-idx))
        (let [before (subvec lines 0 start-idx)
              middle (->> (subvec lines (inc start-idx) end-idx)
                          (map (fn [text]
                                 (let [[k v] (gp-util/split-first ":" (subs text 1))]
                                   (if (and k v)
                                     (let [k (string/replace k "_" "-")
                                           compare-k (keyword (string/lower-case k))
                                           k (if (contains? #{:id :custom_id :custom-id} compare-k) "id" k)
                                           k (if (contains? #{:last-modified-at} compare-k) "updated-at" k)]
                                       (str k ":: " (string/trim v)))
                                     text)))))
              after (subvec lines (inc end-idx))
              lines (concat before middle after)]
          (string/join "\n" lines))
        content))
    content))
