(ns citron.utils
  (:require
   [cljs-time.coerce :refer [from-long to-local-date-time]]
   [cljs-time.core :refer [today to-default-time-zone]]
   [cljs-time.format :refer [unparse-local formatters formatter unparse]]
   [clojure.string :as s]))

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

(defn to-fixed
  ([f]
   (to-fixed f 2))
  ([f precision]
   (.toFixed (js/parseFloat f) precision)))

(def size-units ["" "K" "M" "G" "T" "P" "E" "Z"])

(defn readable-filesize
  ([size]
   (readable-filesize size 0))
  ([size i]
   (when size
     (if (< i (count size-units))
       (if (< size 1024)
         (str (if (pos? i)
                (to-fixed size 1)
                size)
              (nth size-units i) "B")
         (recur (/ size 1024.0) (inc i)))
       "Huge file"))))

(defn to-filename [path]
  (last (s/split path #"/")))
