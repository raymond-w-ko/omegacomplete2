(ns com.raymondwko.omegacomplete2
  (:import vim.Vim))

(defn init []
  ;(def background-thread (future (while true (+ 1 1))))
  )

(defn capture-buffer [buffer_id]
  (let [buf (Vim/buffer (str buffer_id))
        lines (map #(.getLine buf %) (range 1 (+ 1 (.getNumLines buf))))]
    (Vim/msg (reduce + (map #(count %) lines)))
    ))
