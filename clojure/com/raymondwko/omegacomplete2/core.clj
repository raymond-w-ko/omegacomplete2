(ns com.raymondwko.omegacomplete2
  [:import [vim Vim Clojure] [java.io StringWriter]]
  [:use [clojure.pprint :only (pprint)]])

(def split-point-regexp #"[^a-zA-Z0-9\-]+?")
(def word-regexp #"[a-zA-Z0-9\-]+?")

(def buffer-snapshot-queue nil)
(def buffer-to-word-with-count nil)
(def global-word-count nil)

(defn trim-hyphen-prefix-suffix
  "trim leading and trailing hyphens on words that could have resulted from pre
   or post decrement in a C style language"
  [word]
  (-> word
      (clojure.string/replace #"^-+?" "")
      (clojure.string/replace #"-+?$" "")))

(defn debug-write [thing]
  (spit "C:\\omegacomplete2.txt" (pr-str thing)))

(defn offer-job! [job] (.offerLast buffer-snapshot-queue job))
(defn take-job! [] (.takeFirst buffer-snapshot-queue))

(def banned-buffers
  #("GoToFile" "ControlP" "__Scratch__" "__Gundo__" "__Gundo_Preview__"))

(defn capture-buffer
  "creates a snapshot of a buffer and queues it for processing by the
   background thread"
  [buffer-id]
  (let [buffer (Vim/buffer (str buffer-id))
        buffer-name (.getName buffer)
        line-numbers (range 1 (+ 1 (.getNumLines buffer)))
        lines (map #(.getLine buffer %) line-numbers)]
    (offer-job! (list buffer-id lines))
    ;(debug-write @global-word-count)
    ))

(defn get-count
  "gets the value of collection coll given the key k, but returns 0 if the key
   doesn't exist"
  [coll k]
  (let [v (coll k)]
    (if (nil? v) 0 v)))

(defn make-word-count-map
  "transforms a list of words into a mapping of word to number of occurrence"
  [word-list]
  (reduce (fn [coll k] (assoc coll k (+ 1 (get-count coll k))))
          {} word-list))

(defn update-global-word-count
  "updates the mapping of words to reference count"
  [old-global-word-count word-count op]
  (letfn [(func [coll keyval]
            (let [word (key keyval)
                  delta-count (val keyval)
                  prev-count (get-count coll word)
                  new-count (op prev-count delta-count)]
              (if (= 0 new-count)
                (dissoc coll word)
                (assoc coll word new-count))))]
    (reduce func old-global-word-count word-count)))

(defn process-buffer-snapshot [old-buffer-to-word-with-count id lines]
  (let [old-word-count (old-buffer-to-word-with-count id) 
        ,
        word-list
        (->> lines
             (map #(clojure.string/split % split-point-regexp))
             (map #(map trim-hyphen-prefix-suffix %))
             (map #(filter (fn [word] (not-empty word)) %))
             (map #(concat %))
             (reduce concat))
        ,
        word-count (make-word-count-map word-list)]
    (swap! global-word-count update-global-word-count word-count +)
    (if old-word-count
      (swap! global-word-count update-global-word-count old-word-count -))
    (assoc old-buffer-to-word-with-count id word-count)))

(defn background-worker
  "consumes buffer snapshots from buffer-snapshot-queue and updates
   global-word-count and buffer-to-word-with-count"
  []
  (let [job (take-job!)
        [buffer-id buffer-lines] job]
    (swap! buffer-to-word-with-count
           process-buffer-snapshot buffer-id buffer-lines)) 
  (recur))

(defn get-last-word
  ([line]
   (get-last-word line
                  (dec (.length line))))
  ([line i]
   (cond
     (< i 0)
     line
     (nil? (re-matches word-regexp (subs line i (inc i))))
     (subs line (inc i))
     :else
     (recur line (dec i)))))

(defn calculate-and-fill-results []
  (let [results (Vim/eval "g:omegacomplete2_results")
        cursor-col (dec (.getColPos (Vim/window "false")))
        line-prefix (subs (Vim/line) 0 cursor-col)
        word (get-last-word line-prefix)
        ]
   (Vim/msg word) 
))

(defn set-is-corrections-only
  []
  (if false
    (Vim/command "let is_corrections_only=1")
    (Vim/command "let is_corrections_only=0")))

(defn init []
  ; uncomment this to have Vim print normal REPL output
  ;(set! (. Clojure PRINT_REPL_OUTPUT) true)

  (def buffer-snapshot-queue (java.util.concurrent.LinkedBlockingDeque.))
  (def buffer-to-word-with-count (atom {}))
  (def global-word-count (atom (sorted-map)))

  (doto
    (Thread. background-worker)
    (.setDaemon true)
    (.start)))
