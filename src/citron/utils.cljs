(ns citron.utils
  (:require
   [cljs-time.coerce :refer [from-long to-local-date-time]]
   [cljs-time.core :refer [today to-default-time-zone]]
   [cljs-time.format :refer [unparse-local formatters formatter unparse]]))

(defn set-hash! [loc]
  (set! (.-hash js/window.location) loc))

(defn now-in-millis []
  (.getTime (js/Date.)))

(defn format-millis [millis key-or-formatter]
  (unparse-local (if (keyword? key-or-formatter)
                   (formatters key-or-formatter)
                   key-or-formatter)
                 (to-default-time-zone (from-long millis))))

(defn long->date-string
  ([]
   (long->date-string (now-in-millis)))
  ([ts]
   (format-millis ts :year-month-day)))

(defn long->datetime-string
  ([]
   (long->datetime-string (now-in-millis)))
  ([ts]
   (format-millis ts :mysql)))

(defn long->complex-string
  ([]
   (long->complex-string (now-in-millis)))
  ([ts]
   (format-millis ts (formatter "yyyy/w E MM-dd HH:mm:ss"))))
