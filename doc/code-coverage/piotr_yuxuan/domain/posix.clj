✔ (ns piotr-yuxuan.domain.posix
?   "Option names are single alphanumeric characters (as for isalnum). For
?   short options the space or equal character between option and an
?   option argument is optional; also, several one-character option may
?   be grouped after one hyphen.")
  
✔ (defn option?
?   "Arguments are options if they begin with a hyphen delimiter ('-')."
?   [s]
✔   (re-find #"^-\p{Alnum}{0,1}" s))
  
✔ (defn single-option?
?   "A option argument may follow the single-character option name. This
?   can become rather counterintuitive with single-character option -."
?   [s]
✔   (re-find #"^-\p{Alnum}{0,1}" s))
  
✔ (defn single-option-without-value?
?   [s]
✔   (re-find #"^-\p{Alnum}{0,1}$" s))
  
✔ (defn grouped-options?
?   [s]
✔   (re-find #"(?<=^-{1})\p{Alnum}{2,}" s))
  
✔ (def option-terminator
?   "--")
