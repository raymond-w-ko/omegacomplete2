(ns com.raymondwko.omegacomplete2
  [:import [vim Vim Clojure] [java.io StringWriter]]
  [:use [clojure.pprint :only (pprint)]])

(def work-queue nil)
(def buffer-to-word-count nil)
(def global-word-count nil)

(defn trim-hyphen-prefix-suffix
  "trim leading and trailing hyphens on words as a result of increment and
   decrement operators in C style languages"
  [word]
  (-> word
      (clojure.string/replace #"^-+?" "")
      (clojure.string/replace #"-+?$" "")))

(defn debug-write [thing]
  (spit "C:\\omegacomplete2.txt" (pr-str thing)))

(defn offer-job! [job] (.offerLast work-queue job))
(defn take-job! [] (.takeFirst work-queue))

(defn capture-buffer
  "captures a buffer and queues it for processing by the background thread"
  [buffer-id]
  (let [buf (Vim/buffer (str buffer-id))
        lines (map #(.getLine buf %) (range 1 (+ 1 (.getNumLines buf))))]
    (offer-job! (list buffer-id lines))
    (debug-write @global-word-count)
    ))

(defn get-count [coll k]
  (let [v (coll k)]
    (if (nil? v) 0 v)))

(defn make-word-count [word-list]
  (reduce (fn [coll k] (assoc coll k (+ 1 (get-count coll k))))
          {} word-list))

(defn update-global-word-count [old-global word-count op]
  (letfn [(func [coll keyval]
            (let [word (key keyval)
                  delta-count (val keyval)
                  current-count (get-count coll word)
                  total-count (op current-count delta-count)]
              (if (= 0 total-count)
                (dissoc coll word)
                (assoc coll word total-count))))]
    (reduce func old-global word-count)))

(defn process-buffer-snapshot [old-map buffer-id buffer-lines]
  (let [old-word-count
        (old-map buffer-id) 
        ,
        word-list
        (->> buffer-lines
             (map #(clojure.string/split % #"[^a-zA-Z0-9\-]+?"))
             (map #(map trim-hyphen-prefix-suffix %))
             (map #(filter (fn [word] (not-empty word)) %))
             (map #(concat %))
             (reduce concat))
        ,
        word-count
        (make-word-count word-list)]
    (swap! global-word-count update-global-word-count word-count +)
    (if old-word-count
      (swap! global-word-count update-global-word-count old-word-count -))
    (assoc old-map buffer-id word-count)))

(defn background-worker []
  (let [job (take-job!)
        [buffer-id buffer-lines] job]
    (swap! buffer-to-word-count process-buffer-snapshot buffer-id buffer-lines)) 
  (recur))

(defn init []
  ; uncomment this to have Vim print normal REPL output
  ;(set! (. Clojure PRINT_REPL_OUTPUT) true)

  (def work-queue (java.util.concurrent.LinkedBlockingDeque.))
  (def buffer-to-word-count (atom {}))
  (def global-word-count (atom (sorted-map)))

  (doto
    (Thread. background-worker)
    (.setDaemon true)
    (.start)))
