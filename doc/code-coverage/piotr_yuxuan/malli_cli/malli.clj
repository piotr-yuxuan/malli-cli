✔ (ns piotr-yuxuan.malli-cli.malli
?   "Functions or overloaded functions that could be part of malli, but aren't (as of now)."
?   (:require [malli.core :as m]
?             [malli.util :as mu]))
  
✔ (defn value-schemas
?   "Returns all leaf sub schemas for unique paths as a vector of maps
?   with :schema, :path and :in keys."
?   [schema]
✔   (->> schema
✔        mu/subschemas
✔        (remove (comp #{:map} m/type :schema))))
