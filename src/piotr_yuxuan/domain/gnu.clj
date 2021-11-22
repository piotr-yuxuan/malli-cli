(ns piotr-yuxuan.domain.gnu)

(defn long-option?
  [s]
  (re-find #"^--\p{Alnum}[-\p{Alnum}]" s))

(defn long-option-with-value?
  [s]
  (re-find #"^--\p{Alnum}[-\p{Alnum}]+=" s))

(defn long-option-without-value?
  [s]
  (re-find #"^--\p{Alnum}[-\p{Alnum}]+$" s))
