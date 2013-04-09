(ns com.raymondwko.omegacomplete2
  [:import [vim Vim ClojureInterpreter]])

(def words nil)

(defn exit-callback []
  ;
  )

(defn init []
  (def words (agent '()))
  ; uncomment this to have Vim print normal REPL output
  (set! (. ClojureInterpreter PRINT_REPL_OUTPUT) true)
  )

(defn real-hyphenated-word? [word]
  (and (not (.endsWith word "-")) (not (.startsWith word "-")) true)
  )

(defn tokenize [dummy lines]
  (let [split-lines (map #(clojure.string/split % #"[^a-zA-Z0-9\-]+?") lines)
        filtered-lines (map #(filter real-hyphenated-word? %) split-lines)
        joined-lines (map #(concat %) filtered-lines)
        word-list (reduce concat joined-lines)
        ]
    word-list
    )
  )

(defn capture-buffer [buffer_id]
  (let [buf (Vim/buffer (str buffer_id))
        lines (map #(.getLine buf %) (range 1 (+ 1 (.getNumLines buf))))]
    (send-off words tokenize lines)
    @words
    ))
