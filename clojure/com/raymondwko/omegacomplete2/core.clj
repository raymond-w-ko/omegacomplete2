(ns com.raymondwko.omegacomplete2
  [:import
   [vim Vim Clojure]
   [java.io StringWriter]]
  [:use [clojure.pprint :only (pprint)]]
  )

(def work-queue nil)
(def buffer-to-word-list nil)

(defn exit-callback []
  ;
  )

(defn trim-hyphen-prefix-suffix
  "trim leading and trailing hyphens on words as a result of increment and
   decrement operators in C style languages"
  [word]
  (-> word
      (clojure.string/replace #"^-+?" "")
      (clojure.string/replace #"-+?$" "")))

(defn debug-write [thing]
  (spit "C:\\omegacomplete2.txt" (pr-str thing))
  )

(defn offer-job! [job]
  (.offerLast work-queue job)
  work-queue)

(defn take-job! []
  (.takeFirst work-queue))

(defn capture-buffer [buffer-id]
  (let [buf (Vim/buffer (str buffer-id))
        lines (map #(.getLine buf %) (range 1 (+ 1 (.getNumLines buf))))]
    (offer-job! (list buffer-id lines))
    (debug-write @buffer-to-word-list)
    nil
    ))

(defn make-word-count [word-list m]
  (if (not-empty word-list)
    (let [word (first word-list)
          value (m word)
          existing-count (if (nil? value) 0 value)
          ]
      (recur (rest word-list) (assoc m word (inc existing-count))))
    m))

(defn tokenize [orig-value buffer-id buffer-lines]
  (let [buffer-word-list
        (->> buffer-lines
             (map #(clojure.string/split % #"[^a-zA-Z0-9\-]+?"))
             (map #(map trim-hyphen-prefix-suffix %))
             (map #(filter (fn [word] (not-empty word)) %))
             (map #(concat %))
             (reduce concat)
             )]
    (assoc orig-value buffer-id (make-word-count buffer-word-list (sorted-map)))))

(defn background-worker []
  (let [job (take-job!)
        [buffer-id buffer-lines] job]
    (swap! buffer-to-word-list tokenize buffer-id buffer-lines)) 
  (recur))

(defn init []
  ; uncomment this to have Vim print normal REPL output
  ;(set! (. Clojure PRINT_REPL_OUTPUT) true)

  (def work-queue (java.util.concurrent.LinkedBlockingDeque.))
  (def buffer-to-word-list (atom {}))

  (doto
    (Thread. background-worker)
    (.setDaemon true)
    (.start))
  )
