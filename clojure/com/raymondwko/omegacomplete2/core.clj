(ns com.raymondwko.omegacomplete2
  [:import
   [vim Vim Clojure List]
   [java.io StringWriter]
   [java.lang Character]
   [java.util.concurrent LinkedBlockingDeque]
   [javax.swing JOptionPane] 
   ]
  [:require [clojure.pprint]] 
  [:require [clojure.core [reducers :as r]]])

(set! *warn-on-reflection* true)

(defn MessageBox [text]
  (JOptionPane/showMessageDialog nil text)
  )
(def map! (comp doall map))
(def filter! (comp doall filter))
(def reduce! (comp doall reduce))

(def split-point-regexp #"[^a-zA-Z0-9\-]+")
(def word-regexp #"[a-zA-Z0-9\-]+?")

(def ^LinkedBlockingDeque buffer-snapshot-queue nil)
(def buffer-to-word-with-count nil)
(def global-word-count nil)

(defn trim-hyphen-prefix-suffix
  "trim leading and trailing hyphens on words that could have resulted from pre
   or post decrement in a C style language"
  [word]
  (-> word
      (clojure.string/replace #"^-+" "")
      (clojure.string/replace #"-+$" "")))

(defn debug-write [thing]
  (spit "C:\\SVN\\omegacomplete2.txt" (pr-str thing)))

(defn offer-job! [job] (.offerLast buffer-snapshot-queue job))
(defn take-job! [] (.takeFirst buffer-snapshot-queue))

(def banned-buffers
  #{"GoToFile" "ControlP" "__Scratch__" "__Gundo__" "__Gundo_Preview__"})

(defn capture-buffer
  "creates a snapshot of a buffer and queues it for processing by the
   background thread"
  [buffer-id]
  (let [buffer (Vim/buffer (str buffer-id))
        buffer-name (.getName buffer)]
    (if (false? (contains? banned-buffers buffer-name))
      (let [lines (seq (.getAllLines buffer))]
        (offer-job! (list buffer-id lines))))))

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
  (letfn [(split-to-words [line] (clojure.string/split line split-point-regexp))
          (trim-hyphens [coll] (map trim-hyphen-prefix-suffix coll))
          (remove-empty-strings [coll] (filter not-empty coll))]
    (let [old-word-count (old-buffer-to-word-with-count id) 
          ,
          word-list (->> lines
                         (map split-to-words)
                         (map trim-hyphens)
                         (map remove-empty-strings)
                         (apply concat))
          ,
          ;dummy (debug-write word-list)
          ,
          word-count (make-word-count-map word-list)]
    (swap! global-word-count update-global-word-count word-count +)
    (if old-word-count
      (swap! global-word-count update-global-word-count old-word-count -))
    (assoc old-buffer-to-word-with-count id word-count))))

(defn background-worker
  "consumes buffer snapshots from buffer-snapshot-queue and updates
   global-word-count and buffer-to-word-with-count"
  []
  (let [job (take-job!)
        [buffer-id buffer-lines] job]
    (swap! buffer-to-word-with-count
           process-buffer-snapshot buffer-id buffer-lines)
    ;(debug-write @global-word-count)
    ) 
  (recur))

(defn get-last-word
  "returns the last word of given the input line"
  ([^String line] (get-last-word line
                         (dec (.length line))))
  ([line i]
   (cond
     (< i 0)
     line
     (nil? (re-matches word-regexp (subs line i (inc i))))
     (subs line (inc i))
     :else
     (recur line (dec i)))))

(defn title-case-match?
  [^String word ^String input]
  (let [formatted-word
        (apply str
               (cons (Character/toUpperCase (.charAt word 0)) (rest word)))
        only-upper
        (apply str
               (filter (fn [^java.lang.Character letter] (Character/isUpperCase letter))
                       formatted-word))]
    (= input (clojure.string/lower-case only-upper))))

(defn underscore-match?
  [^String word ^String input]
  false
  )

(defn make-get-score [^String input]
  "Creates the get-score function given the current word the cursor is on. The
   inner function returns a word score pair in a list, where score is how
   preferable a match is."
  (fn
    [^String word]
    (list word
          (cond
            (.isEmpty input) 0
            (= word input) 0
            (title-case-match? word input) 100
            (underscore-match? word input) 100
            (.startsWith word input) 50
            :else 0))))

(defn positive-score [pair] (> (second pair) 0))

(defn calculate-and-fill-results []
  (let [^vim.List output (Vim/eval "g:omegacomplete2_results")
        cursor-col (dec (.getColPos (Vim/window "false")))
        line-prefix (subs (Vim/line) 0 cursor-col)
        word (get-last-word line-prefix)
        score-fn (make-get-score word)
        reducef (fn
                  ([] (vector))
                  ([a b] (conj a b)))
        coll (->> @global-word-count
                  (r/map (fn [a b] a))
                  (r/map score-fn)
                  (r/filter positive-score))
        results (r/fold reducef coll)
        ]
    (doseq [pair results] (.add output (first pair)))
))

(defn set-is-corrections-only
  []
  (if false
    (Vim/command "let is_corrections_only=1")
    (Vim/command "let is_corrections_only=0")))

(defn init []
  ; uncomment this to have Vim print normal REPL output
  ;(set! (. Clojure PRINT_REPL_OUTPUT) true)

  (def ^LinkedBlockingDeque buffer-snapshot-queue (LinkedBlockingDeque.))
  (def buffer-to-word-with-count (atom {}))
  (def global-word-count (atom (sorted-map)))

  (doto
    (Thread. ^java.lang.Runnable background-worker)
    (.setDaemon true)
    (.start)))
